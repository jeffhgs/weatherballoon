package com.groovescale

import java.io._
import java.util.Properties

import com.amazonaws.auth.{AWSCredentials, AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model._
import com.jcraft.jsch.{Channel, ChannelExec, JSch, Session}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object Run1 {
  val log = LoggerFactory.getLogger(Run1.getClass())

  import scala.collection.JavaConverters._


  def provisionViaAws(
                       region: String,
                       group1: String,
                       keyPair: String,
                       instanceType: String,
                       stUser: String,
                       ami: String,
                       tag: String,
                       cred: config.AwsCred
                     ) = {
    val runInstancesRequest = new RunInstancesRequest();
    import java.nio.charset.StandardCharsets
    import java.util.Base64
    val encoder = Base64.getEncoder
    val normalString = getUserdataScriptRaw().mkString("\n")
    val encodedString = encoder.encodeToString(normalString.getBytes(StandardCharsets.UTF_8))

    runInstancesRequest
      .withImageId(ami)
      .withInstanceType(InstanceType.T2Medium)
      .withMinCount(1)
      .withMaxCount(1)
      .withKeyName(keyPair)
      .withSecurityGroups(group1)
      .withUserData(encodedString)
      .withInstanceType(instanceType)
      .withTagSpecifications(
        new TagSpecification()
          .withResourceType(ResourceType.Instance)
          .withTags(new Tag(tag,""))
      )
      .withInstanceInitiatedShutdownBehavior(ShutdownBehavior.Terminate)
      .withIamInstanceProfile(
        new IamInstanceProfileSpecification().withArn(
          "arn:aws:iam::............:instance-profile/can_terminateInstances2"))
      .withRequestCredentialsProvider(
        new AWSStaticCredentialsProvider(new BasicAWSCredentials(
          cred.id,
          cred.secret
        )))
    val amazonEC2Client = AmazonEC2Client.builder().withRegion(region).build()
    val result = amazonEC2Client.runInstances(
      runInstancesRequest)
    result.getReservation()
  }

  private def getUserdataScript(res:String) : Seq[String] = {
    val str = getClass().getResourceAsStream(res)
    val r = new BufferedReader(new InputStreamReader(str))
    r.lines().iterator().asScala.toSeq
  }

  private def getUserdataScriptRaw() : Seq[String] = {
    val scriptBig = Seq(
      "/file/install_heartbeat_cron.sh",
      "/file/install_deps.sh"
    ).flatMap(getUserdataScript)
    val lines = (Seq("#!/usr/bin/env bash") ++ scriptBig)
    lines
  }

  case class NodeMetadata(
                           publicAddresses:Seq[String],
                           status:String,
                           tags:Seq[Tag])

  def listNodesViaAws(
                           group1 : String,
                           region: String,
                           cred : config.AwsCred
                         ) : Seq[NodeMetadata] =
  {
    val req = new DescribeInstancesRequest()
    val amazonEC2Client = AmazonEC2Client.builder()
      .withRegion(region)
      .withCredentials(
        new AWSStaticCredentialsProvider(new BasicAWSCredentials(
          cred.id,
          cred.secret
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

  val tmpPumpOnce = new Array[Byte](1024)
  def pumpOnce(in:InputStream, os:OutputStream) : Unit = {
    while(in.available > 0) {
      //log.info("trying to read")
      val i = in.read(tmpPumpOnce, 0, 1024)
      if (i < 0) {
        log.info("available input has no input.  Error?")
        return
      }
      System.out.print(new String(tmpPumpOnce, 0, i))
    }
  }

  def execViaSshImplJsch2(session:Session, command:String) : Try[Int] = {
    //log.info("connected via jsch")
    val chan = session.openChannel("exec")
    // If we don't allocate a pty, sshd will not know to kill our process if we ctrl+C weatherballoon
    chan.asInstanceOf[ChannelExec].setPty(true)
    //log.info(s"setting command: ${command}")
    chan.asInstanceOf[ChannelExec].setCommand(command)
    val is = chan.getInputStream

    val out = new PipedOutputStream()
    val pout = new PipedInputStream(out)
    chan.setOutputStream(out)

    //log.info("about to connect")
    chan.connect(10000)
    //log.info("about to pump")
    while(!chan.isClosed) {
      pumpOnce(pout, System.out)
      //log.info("sleeping")
      Thread.sleep(1000)
    }
    System.out.flush()
    //log.info("about to disconnect channel")
    if(chan.isConnected) {
      chan.disconnect()
    }
    //log.info("about to disconnect session")
    if(session.isConnected) {
      session.disconnect()
    }
    //log.info(s"status ${chan.getExitStatus}")
    return Success(chan.getExitStatus)
  }

  def execViaSsh(
                  hostname:String,
                  username:String,
                  pkfile:String,
                  //                fingerprint:String,
                  sConnectTimeout : Int,
                  command:String
                ) : Try[Int] =
  {
    val res = execViaSshImpl(hostname, username, pkfile, sConnectTimeout, "touch /tmp/heartbeat")
    res match {
      case Success(0) =>
        return execViaSshImpl(hostname, username, pkfile, sConnectTimeout, command)
      case _ =>
        return res
    }
  }

  def execViaSshImpl(
                hostname:String,
                username:String,
                pkfile:String,
//                fingerprint:String,
                sConnectTimeout : Int,
                command:String
                ) : Try[Int] =
  {
    try {
      val props = new Properties()
      props.put("StrictHostKeyChecking", "no")
      val jsch = new JSch();
      //JSch.setLogger(new JSCHLogger());
      jsch.addIdentity(pkfile)
      val session = jsch.getSession(username, hostname, 22)
      session.setConfig(props)
      session.connect(30000) // making a connection with timeout.
      return execViaSshImplJsch2(session, command)
    } catch {
      case ex:Throwable =>
        return Failure(ex)
    }
  }

  def testProvision(cfg:config.Remoter) = {
    provisionViaAws(
      cfg.region,
      cfg.group1,
      cfg.keyPair,
      cfg.instanceType,
      cfg.os.username,
      cfg.os.ami,
      cfg.tag,
      cfg.cred
    )
  }
  def testListNodes(
                     cfg:config.Remoter
                   ) =
  {
    val nodes = listNodesViaAws(cfg.group1, cfg.region, cfg.cred)
    log.info(s">> No of nodes ${nodes.size}")
    import scala.collection.JavaConversions._
    for (node <- nodes) {
      log.info(">>>>  " + node)
      if(node.tags.contains(cfg.tag)) {
        log.info("woot!")
      }
    }
  }
  def testFindNode(
                     cfg:config.Remoter
                   ) =
  {
    val node = tryFindNode(cfg.group1, cfg.region, cfg.tag, cfg.cred)
    log.info(">>>>  " + node)
  }

  def testSsh(cfg:config.Remoter) = {
    log.info("hello")
    val hostname = "ec2-54-186-244-37.us-west-2.compute.amazonaws.com"
    val sConnectTimeout = 10
    val cmd = "echo hello"
    val value = execViaSsh(hostname, cfg.os.username, cfg.kpFile(), sConnectTimeout, cmd)
    value match {
      case scala.util.Success(value) =>
        // TODO: for logging, limit stdout and stderr to a maximum number of characters
        log.info(s"status=${value}\n\tcommand=\n\t${cmd}")
      case scala.util.Failure(ex) =>
        log.warn(ex.toString)
    }
    log.info("sleeping")
    Thread.sleep(10000)
  }

  def tryFindNode(
                 group1:String,
                 region:String,
                 tag:String,
                 cred:config.AwsCred
                 ) : Option[(NodeMetadata,String)] =
  {
    val nodesRemoter1 = listNodesViaAws(group1, region, cred).filter(node =>
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
  def execAndRetry(
                       username: String,
                       pkfile: String,
                       //                fingerprint:String,
                       command: String,
                       group1:String,
                       region:String,
                       cred:config.AwsCred,
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
      tryFindNode(group1, region, tag, cred) match {
        case Some((node,addr)) =>
          val value = execViaSsh(addr, username, pkfile, sConnectTimeout, command)
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

  def rsync(ipaddr:String, cfg : config.Remoter) : Int = {
    import scala.sys.process._
    if(!(new File(cfg.sync.fileExcludes).exists()))
      throw new RuntimeException(s"fileExcludes=${cfg.sync.fileExcludes} does not exist")
    val dirFrom = new File(cfg.sync.adirLocal)
    if(!dirFrom.exists())
      throw new RuntimeException(s"fileExcludes=${cfg.sync.adirLocal} does not exist")
    if(!dirFrom.isDirectory)
      throw new RuntimeException(s"fileExcludes=${cfg.sync.adirLocal} is not a directory")
    val cmd = cmdRsync(ipaddr,cfg)
    log.info(s"about to ${cmd}")
    cmd.!
  }

  def cmdRsync(ipaddr:String, cfg:config.Remoter) : Seq[String] = {
    Seq(
      "env", s"RSYNC_RSH=ssh -i ${cfg.kpFile()} -l ${cfg.os.username} -o CheckHostIP=no -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null",
      "rsync", s"--exclude-from=${cfg.sync.fileExcludes}", "--verbose", "-r", s"${cfg.sync.adirLocal}/", s"${cfg.os.username}@${ipaddr}:${cfg.sync.adirServer}/"
    )
  }

  def rcloneUp(cfg:config.Remoter) : Try[Int] = {
    import scala.sys.process._
    val cmd = Seq(
      "env", s"AWS_ACCESS_KEY_ID=${cfg.cred.id}",
      s"AWS_SECRET_ACCESS_KEY=${cfg.cred.secret}",
      "RCLONE_CONFIG_MYS3_TYPE=s3",
      "rclone",
      "--config", "/dev/null",
      "--s3-env-auth", "--s3-region", "us-west-2",
      "--exclude", ".git/", "--exclude", ".idea/", "--exclude", "build/", "--exclude", "out/", "--exclude", ".gradle/",
      "sync", cfg.sync.adirLocal, s"mys3:${cfg.sync.dirStorage}/srchome"
    )
    val status = cmd.!
    if(status==0) Success(0) else Failure(new RuntimeException(s"status=${status}"))
  }

  def testProvisionRun(cmd0:Array[String], cfg:config.Remoter) = {
    val idrun = System.currentTimeMillis()
    val cmd1 = cmd0.mkString(" ")
    val cmd2 =
      s"/usr/local/bin/with_heartbeat.sh 1m bash /usr/local/bin/with_instance_role.sh can_terminateInstances2 /usr/local/bin/rclone.sh --s3-region us-west-2 sync mys3:${cfg.sync.dirStorage}/srchome ${cfg.sync.adirServer}" +
      s" && rm -rf ${cfg.sync.adirServer}/log" +
      s" && mkdir -p ${cfg.sync.adirServer}/log" +
      s" && cd ${cfg.sync.adirServer} " +
      s" && (${cmd1} 2>&1 | tee -a ${cfg.sync.adirServer}/log/build.log )" +
      s" && /usr/local/bin/with_heartbeat.sh 1m bash /usr/local/bin/with_instance_role.sh can_terminateInstances2 /usr/local/bin/rclone.sh --s3-region us-west-2 sync ${cfg.sync.adirServer}/log mys3:${cfg.sync.dirStorage}/log/${idrun} "
    val pkfile = new File(System.getenv("HOME"), ".ssh/id_gs_temp_2019-01").toString()

    val numTries = 50
    val msBetweenPolls = 5000
    val sConnectTimeout = 5000

    try {
      rcloneUp(cfg)

      var nodeaddr : Option[(NodeMetadata,String)] = None
      tryFindNode(cfg.group1, cfg.region, cfg.tag, cfg.cred) match {
        case Some((node, addr)) =>
          nodeaddr = Some((node, addr))
        // do nothing
        case None =>
          // presume we should make a node
          log.info("looks like we should make a node")
          provisionViaAws(cfg.region, cfg.group1, cfg.keyPair, cfg.instanceType, cfg.os.username, cfg.os.ami, cfg.tag, cfg.cred)
          Thread.sleep(10000)
          tryFindNode(cfg.group1, cfg.region, cfg.tag, cfg.cred) match {
            case Some((node, addr)) =>
              // now there will eventually be a node
              nodeaddr = Some((node, addr))
            case None =>
              throw new RuntimeException("looks like we didn't succeed in making a node")
          }
      }
      execAndRetry(cfg.os.username, pkfile, "wc -c /var/log/userdata-done", cfg.group1, cfg.region, cfg.cred, cfg.tag, msBetweenPolls, sConnectTimeout, numTries, dryRun = true)
      execAndRetry(cfg.os.username, pkfile, cmd2, cfg.group1, cfg.region, cfg.cred, cfg.tag, msBetweenPolls, sConnectTimeout, numTries, dryRun = false)
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

  def findCfg(name:String) : File = {
    var dir = new File(new File("").getAbsolutePath)
    while(dir != null) {
      val fileCfg = new File(dir, name)
      if(fileCfg.exists())
        return fileCfg
      dir = dir.getParentFile
    }
    return null;
  }

  def getCfg() = {
    val adirHome = System.getenv("HOME")

    import org.json4s._

    import org.json4s.jackson.Serialization.{read, write}
    import config.formats
    var cfg : config.Remoter = null
    try {
      val afileConfig = findCfg(".weatherballoon.json")
      val textConfig = io.Source.fromFile(afileConfig).mkString
      cfg = read[config.Remoter](textConfig)
    } catch {
      case ex:Throwable =>
        throw new RuntimeException("could not parse configuration: ",ex)
    }
    var cred : config.AwsCred = null
    try {
      val afileCred = findCfg(".weatherballoon_cred.json")
      val textCred = io.Source.fromFile(afileCred).mkString
      cred = read[config.AwsCred](textCred)
    } catch {
      case ex:Throwable =>
        throw new RuntimeException("could not parse credentials: ",ex)
    }
    log.info(s"cfg=${cfg}")
    if(cred==null) {
      log.error("no credentials found")
      throw new RuntimeException("no credentials found")
    }
    cfg.copy(
      sync=cfg.sync.copy(adirLocal=System.getProperty("user.dir")),
      cred=cred
    )
  }

  def main(args: Array[String]): Unit = {
    val cmd = args
    val cfg = getCfg()
//    testListNodes(cfg)
//    testFindNode(cfg)
//    testProvision(cfg)
//    testSsh(cfg)
    testProvisionRun(cmd, cfg)
  }
}