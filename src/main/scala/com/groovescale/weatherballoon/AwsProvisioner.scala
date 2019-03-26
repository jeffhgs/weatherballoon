package com.groovescale.weatherballoon

import java.io._

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model._
import org.slf4j.LoggerFactory

object AwsProvisioner {
  val log = LoggerFactory.getLogger(AwsProvisioner.getClass())

  import scala.collection.JavaConverters._

  def provisionViaAws(
                       provisioner: config.AwsProvisioner,
                       tag: String
                     ) = {
    val runInstancesRequest = new RunInstancesRequest();
    import java.nio.charset.StandardCharsets
    import java.util.Base64
    val encoder = Base64.getEncoder
    val normalString = UserdataUtil.getUserdataScriptRaw().mkString("\n")
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

  private def execAndRetry(
                    provisioner: config.AwsProvisioner,
                    //                fingerprint:String,
                    pkfile:String,
                    command: String,
                    tag:String,
                    msBetweenPolls:Int,
                    sConnectTimeout:Int,
                    numTries: Int,
                    dryRun: Boolean
                  ) =
  {
    var cTriesLeft = numTries
    var done = false
    while (cTriesLeft >= 1 && !done) {
      cTriesLeft -= 1
      val iTries = numTries - cTriesLeft
      if(dryRun) {
        log.info(s"connection, try ${iTries} of ${numTries}")
      }
      AwsProvisioner.tryFindNode(provisioner, tag) match {
        case Some((node,addr)) =>
          val value = ExecUtil.execViaSsh(addr, provisioner.os.username, pkfile, sConnectTimeout, command)
          if(!dryRun) {
            value match {
              case scala.util.Success(value) =>
                log.info(s"status=${value}\n\tcommand=\n\t${command}")
              case scala.util.Failure(ex) =>
                log.warn(ex.toString)
            }
          }
          if (dryRun && value.isSuccess && value.get == 0) {
            done = true
          } else if (!dryRun && value.isSuccess) {
            done = true
          } else {
            Thread.sleep(msBetweenPolls)
          }
        case None =>
          Thread.sleep(msBetweenPolls)
      }
    }
  }

  def runProvisioned(cmd0:Array[String], cfg:config.Remoter) = {
    val idrun = System.currentTimeMillis()
    val cmd1 = cmd0.mkString(" ")
    val cmd2 =
      s"/usr/local/bin/with_heartbeat.sh 1m bash /usr/local/bin/with_instance_role.sh ${cfg.provisioner.nameOfRole} /usr/local/bin/rclone.sh --s3-region us-west-2 sync mys3:${cfg.sync.dirStorage}/srchome ${cfg.sync.adirServer}" +
        s" && rm -rf ${cfg.sync.adirServer}/log" +
        s" && mkdir -p ${cfg.sync.adirServer}/log" +
        s" && cd ${cfg.sync.adirServer} " +
        s" && (${cmd1} 2>&1 | tee -a ${cfg.sync.adirServer}/log/build.log )" +
        s" ; /usr/local/bin/with_heartbeat.sh 1m bash /usr/local/bin/with_instance_role.sh ${cfg.provisioner.nameOfRole} /usr/local/bin/rclone.sh --s3-region us-west-2 sync ${cfg.sync.adirServer}/log mys3:${cfg.sync.dirStorage}/log/${idrun} "
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
          provisionViaAws(cfg.provisioner, cfg.tag)
          Thread.sleep(10000)
          tryFindNode(cfg.provisioner, cfg.tag) match {
            case Some((node, addr)) =>
              // now there will eventually be a node
              nodeaddr = Some((node, addr))
            case None =>
              throw new RuntimeException("looks like we didn't succeed in making a node")
          }
      }
      execAndRetry(cfg.provisioner, pkfile, "wc -c /var/log/userdata-done", cfg.tag, msBetweenPolls, sConnectTimeout, numTries, dryRun = true)
      execAndRetry(cfg.provisioner, pkfile, cmd2, cfg.tag, msBetweenPolls, sConnectTimeout, numTries, dryRun = false)
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
