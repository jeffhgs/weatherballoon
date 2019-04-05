package com.groovescale.weatherballoon

import java.io._

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model._
import org.slf4j.LoggerFactory

import scala.util.{Success, Try}

object AwsProvisioner {
  val log = LoggerFactory.getLogger(AwsProvisioner.getClass())

  import scala.collection.JavaConverters._

  def provisionViaAws(
                       provisioner: config.AwsProvisioner,
                       tag: String,
                       minutesMaxIdle:Int
                     ) = {
    val runInstancesRequest = new RunInstancesRequest();
    import java.nio.charset.StandardCharsets
    import java.util.Base64
    val encoder = Base64.getEncoder
    val normalString = UserdataUtil.genUserdataForWeatherballoon("/usr/local/bin", minutesMaxIdle).mkString("\n")
    val encodedString = encoder.encodeToString(normalString.getBytes(StandardCharsets.UTF_8))

    runInstancesRequest
      .withImageId(provisioner.os.ami)
      .withInstanceType(InstanceType.T2Medium)
      .withMinCount(1)
      .withMaxCount(1)
      .withKeyName(provisioner.keyPair)
      .withSecurityGroups(provisioner.group1)
      .withUserData(encodedString)
      .withInstanceType(provisioner.instanceType)
      .withEbsOptimized(true)
      .withBlockDeviceMappings(new BlockDeviceMapping()
        .withDeviceName("/dev/sda1")
        .withEbs(
          new EbsBlockDevice()
            .withVolumeType("gp2")
            .withVolumeSize(provisioner.gbsizeOfMainDisk)))
      .withTagSpecifications(
        new TagSpecification()
          .withResourceType(ResourceType.Instance)
          .withTags(new Tag(tag,""))
      )
      .withInstanceInitiatedShutdownBehavior(ShutdownBehavior.Terminate)
      .withIamInstanceProfile(
        new IamInstanceProfileSpecification().withArn(
          provisioner.roleOfInstance
        ))
      .withRequestCredentialsProvider(
        new AWSStaticCredentialsProvider(new BasicAWSCredentials(
          provisioner.cred.id,
          provisioner.cred.secret
        )))
    val amazonEC2Client = AmazonEC2Client.builder().withRegion(provisioner.region).build()
    val result = amazonEC2Client.runInstances(
      runInstancesRequest)
    result.getReservation()
  }

  case class NodeMetadata(
                           publicAddresses:Seq[String],
                           status:String,
                           tags:Seq[Tag])

  def listNodesViaAws( provisioner: config.AwsProvisioner
                     ) : Seq[NodeMetadata] =
  {
    val req = new DescribeInstancesRequest()
    val amazonEC2Client = AmazonEC2Client.builder()
      .withRegion(provisioner.region)
      .withCredentials(
        new AWSStaticCredentialsProvider(new BasicAWSCredentials(
          provisioner.cred.id,
          provisioner.cred.secret
        )))
      .build()
    val result = amazonEC2Client.describeInstances(req)
    for(reservation <- result.getReservations().asScala;
        instance <- reservation.getInstances().asScala;
        addrs = Seq(instance.getPublicIpAddress);
        status = instance.getState.getName;
        tags = instance.getTags.asScala.toSeq
    )
      yield(NodeMetadata(addrs, status, tags))
  }

  def tryFindNode(provisioner:config.AwsProvisioner,
                  tag:String
                 ) : Option[(NodeMetadata,String)] =
  {
    val nodesRemoter1 = listNodesViaAws(provisioner).filter(node =>
      (node.tags.exists(t => t.getKey.equals(tag)) &&
        (node.status == "running" || node.status == "pending"))
    )
    if (nodesRemoter1.nonEmpty) {
      val node = nodesRemoter1.head
      node.publicAddresses.toArray match {
        case Array(addr: String, _*) =>
          Some((node, addr))
        case Array() =>
          None
      }
    } else
      None
  }

  private def waitForProvisioning(
                                   provisioner: config.AwsProvisioner,
                                   //                fingerprint:String,
                                   pkfile:String,
                                   command: String,
                                   tag:String,
                                   msBetweenPolls:Int,
                                   sConnectTimeout:Int,
                                   numTries: Int
                                 ) : Try[Int] =
  {
    var cTriesLeft = numTries
    var done = false
    while (cTriesLeft >= 1 && !done) {
      cTriesLeft -= 1
      val iTries = numTries - cTriesLeft
      log.info(s"connection, try ${iTries} of ${numTries}")
      AwsProvisioner.tryFindNode(provisioner, tag) match {
        case Some((node,addr)) =>
          val spooler = ""
          val value = ExecUtil.execViaSsh(addr, provisioner.os.username, pkfile, sConnectTimeout, spooler, command)
          if (value.isSuccess && value.get == 0) {
            log.info(s"successfully provisioned node ${node} at ${addr}")
            return value
          } else {
            Thread.sleep(msBetweenPolls)
          }
        case None =>
          Thread.sleep(msBetweenPolls)
      }
    }
    throw new ExecUtil.TooManyRetriesException(s"waitForProvisioning: command=${command}")
  }

  private def execAndRetry(
                    provisioner: config.AwsProvisioner,
                    //                fingerprint:String,
                    pkfile:String,
                    command: String,
                    tag:String,
                    msBetweenPolls:Int,
                    sConnectTimeout:Int,
                    spooler:String,
                    numTries: Int
                  ) : Try[Int] =
  {
    // TODO: by specification, while we cannot retry our command, we can retry here
    AwsProvisioner.tryFindNode(provisioner, tag) match {
      case Some((node,addr)) =>
        val value = ExecUtil.execViaSsh(addr, provisioner.os.username, pkfile, sConnectTimeout, spooler, command)
        value match {
          case scala.util.Success(value) =>
            log.info(s"status=${value}\n\tcommand=\n\t${command}")
          case scala.util.Failure(ex) =>
            log.warn(ex.toString)
        }
        return value
      case None =>
        throw new RuntimeException("TODO: retries: execAndRetry")
    }
  }

  def runProvisioned(cmd0:Array[String], cfg:config.Remoter) = {
    val idrun = System.currentTimeMillis()
    val cmd1 = cmd0.mkString(" ")
    val cmd2 =
      s"/usr/local/bin/with_sync_updown.sh ${cfg.minutesMaxRun} ${cfg.provisioner.nameOfRole} ${cfg.sync.dirStorage} ${cfg.sync.adirServer} ${idrun} " +
      s"/usr/local/bin/with_logging.sh ${cfg.sync.adirServer}/log/build.log ${cmd1}"
    val cmd3 =
      if(cfg.spooler == "tmux")
        s"/usr/local/bin/spool_via_tmux.sh ${cmd2}"
      else
        cmd2
    val pkfile = cfg.kpFile()

    val numTries = 50
    val msBetweenPolls = 5000
    val sConnectTimeout = 5000

    try {
      SyncUtil.rcloneUp(cfg)

      var nodeaddr : Option[(NodeMetadata,String)] = None
      tryFindNode(cfg.provisioner, cfg.tag) match {
        case Some((node, addr)) =>
          nodeaddr = Some((node, addr))
        // do nothing
        case None =>
          // presume we should make a node
          log.info("looks like we should make a node")
          provisionViaAws(cfg.provisioner, cfg.tag, cfg.minutesMaxIdle)
          Thread.sleep(10000)
          tryFindNode(cfg.provisioner, cfg.tag) match {
            case Some((node, addr)) =>
              // now there will eventually be a node
              nodeaddr = Some((node, addr))
            case None =>
              throw new RuntimeException("looks like we didn't succeed in making a node")
          }
      }
      val cmdCheckDone = "test -e " + "\"" + config.Hardcoded.afileDone + "\""
      val status = waitForProvisioning(cfg.provisioner, pkfile, cmdCheckDone, cfg.tag, msBetweenPolls, sConnectTimeout, numTries)
      status match {
        case Success(0) =>
          log.info(s"about to run cmd: ${cmd3}")
          execAndRetry(cfg.provisioner, pkfile, cmd3, cfg.tag, msBetweenPolls, sConnectTimeout, cfg.spooler, numTries)
        case _ =>
          log.info(s"waiting for provisioning")
      }
    } catch {
      case ex:Throwable =>
        val sw = new StringWriter()
        val buf = new PrintWriter(sw)
        //ex.fillInStackTrace().printStackTrace(buf)
        ex.printStackTrace(buf)
        buf.close()
        log.info(sw.getBuffer.toString)
        System.exit(1)
    }
    System.exit(0)
  }
}
