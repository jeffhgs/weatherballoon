package com.groovescale

import java.io._
import java.util.Properties

import com.amazonaws.auth.{AWSCredentials, AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model._
import com.decodified.scalassh._
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object Run1 {
  val log = LoggerFactory.getLogger(Run1.getClass())

  import scala.collection.JavaConverters._


  def provisionViaAws(
                       region: String,
                       group1: String,
                       keyPair: String,
                       stUser: String,
                       ami: String,
                       tag: String,
                       cred: config.Aws
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
                           tags:Seq[String])

  def listNodesViaJclouds(
                           group1 : String,
                           cred : config.Aws
                         ) : Seq[NodeMetadata] =
  {
    Seq()
  }

  def execViaSsh(
                hostname:String,
                username:String,
                pkfile:String,
//                fingerprint:String,
                sConnectTimeout : Int,
                command:String
                ) : Try[CommandResult] =
  {
    val hcp =
      new HostConfigProvider() {
        override def apply(v1: String): Try[HostConfig] = {
          scala.util.Success(HostConfig(
            login = new PublicKeyLogin(
              username,
              None,
              List(pkfile)
            ),
            sshjConfig = new net.schmizz.sshj.DefaultConfig(),
            hostName = hostname,
            // TODO: get fingerprint from API and verify
            //HostKeyVerifiers.forFingerprint(fingerprint)
            hostKeyVerifier = HostKeyVerifiers.DontVerify,
            connectTimeout = Some(sConnectTimeout)
          )
          )
        }
      }
    var res : Try[CommandResult] = scala.util.Failure(new RuntimeException("no ssh connection"))
    try {
      SSH(hostname, hcp) { client => {
        val res0 = client.exec("touch /tmp/heartbeat")
        if(res0.isSuccess)
          res = client.exec(command)
          client.exec("touch /tmp/heartbeat")
        }
      }
    } catch {
      case ex:Throwable =>
        log.error("ssh exception: "+ex)
    }
    return res
  }
  def execViaSshAndPrint(
                          hostname:String,
                          username:String,
                          pkfile:String,
                          //                fingerprint:String,
                          sConnectTimeout : Int,
                          command:String
                        ) : Try[CommandResult] =
  {
    val res = execViaSsh(hostname, username, pkfile, sConnectTimeout, command)
    res match {
      case scala.util.Success(value) =>
        println("result:")
        println(value.stdOutAsString())
      // do nothing
      case scala.util.Failure(ex) =>
        log.warn(ex.toString)
      //ok = true
    }
    return res
  }

  def testProvision(cfg:config.Remoter) = {
    provisionViaAws(
      cfg.region,
      cfg.group1,
      cfg.keyPair,
      cfg.os.stUser,
      cfg.os.ami,
      cfg.tag,
      cfg.cred
    )
  }
  def testListNodes(
                     cfg:config.Remoter
                   ) =
  {
    val nodes = listNodesViaJclouds(cfg.group1, cfg.cred)
    println(s">> No of nodes ${nodes.size}")
    import scala.collection.JavaConversions._
    for (node <- nodes) {
      println(">>>>  " + node)
      if(node.tags.contains(cfg.tag)) {
        println("woot!")
      }
    }
  }
  def testFindNode(
                     cfg:config.Remoter
                   ) =
  {
    val node = tryFindNode(cfg.group1, cfg.tag, cfg.cred)
    println(">>>>  " + node)
  }

  def testSsh(cfg:config.Remoter) = {
    log.info("hello")
    val hostname = "ec2-54-186-244-37.us-west-2.compute.amazonaws.com"
    val sConnectTimeout = 10
    val cmd = "echo hello"
    execViaSshAndPrint(hostname, cfg.os.username, cfg.kpFile(), sConnectTimeout, cmd)
    println("sleeping")
    Thread.sleep(10000)
  }

  def tryFindNode(
                 group1:String,
                 tag:String,
                 cred:config.Aws
                 ) : Option[(NodeMetadata,String)] =
  {
    val nodesRemoter1 = listNodesViaJclouds(group1, cred).filter(node =>
      (node.tags.contains(tag) &&
        (node.status == "RUNNING" || node.status == "PENDING"))
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
                       cred:config.Aws,
                       tag:String,
                       msBetweenPolls:Int,
                       sConnectTimeout:Int,
                       numTries: Int
                 ) =
  {
    var cTriesLeft = numTries
    var done = false
    while (cTriesLeft >= 1 && !done) {
      cTriesLeft -= 1
      val iTries = numTries - cTriesLeft
      println(s"connection, try ${iTries} of ${numTries}")
      tryFindNode(group1, tag, cred) match {
        case Some((node,addr)) =>
          if (execViaSshAndPrint(addr, username, pkfile, sConnectTimeout, command).isSuccess) {
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
    val dirFrom = new File(cfg.sync.adirFrom)
    if(!dirFrom.exists())
      throw new RuntimeException(s"fileExcludes=${cfg.sync.adirFrom} does not exist")
    if(!dirFrom.isDirectory)
      throw new RuntimeException(s"fileExcludes=${cfg.sync.adirFrom} is not a directory")
    val cmd = cmdRsync(ipaddr,cfg)
    println(s"about to ${cmd}")
    cmd.!
  }

  def cmdRsync(ipaddr:String, cfg:config.Remoter) : Seq[String] = {
    Seq(
      "env", s"RSYNC_RSH=ssh -i ${cfg.kpFile()} -l ${cfg.os.username} -o CheckHostIP=no -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null",
      "rsync", s"--exclude-from=${cfg.sync.fileExcludes}", "--verbose", "-r", s"${cfg.sync.adirFrom}/", s"${cfg.os.username}@${ipaddr}:${cfg.sync.adirRemote}/"
    )
  }

  def rcloneUp(cfg:config.Remoter) : Try[Int] = {
    import scala.sys.process._
    val cmd = Seq(
      "env", s"AWS_ACCESS_KEY_ID=${cfg.cred.id}",
      s"AWS_SECRET_ACCESS_KEY=${cfg.cred.secret}",
      "RCLONE_CONFIG_MYS3_TYPE=s3",
      "rclone", "--s3-env-auth", "--s3-region", "us-west-2",
      "--exclude", ".git/", "--exclude", ".idea/", "--exclude", "build/", "--exclude", "out/", "--exclude", ".gradle/",
      "sync", cfg.sync.adirFrom, s"mys3:${cfg.sync.adirRemote}"
    )
    val status = cmd.!
    if(status==0) Success(0) else Failure(new RuntimeException(s"status=${status}"))
  }

  def testProvisionRun(cfg:config.Remoter) = {
    log.info("hello")

    val idrun = System.currentTimeMillis()
    val cmd1 =
      "/usr/local/bin/with_heartbeat.sh 1m bash /usr/local/bin/with_instance_role.sh can_terminateInstances2 /usr/local/bin/rclone.sh --s3-region us-west-2 sync mys3:weatherballoon-test1/srchome /home/ubuntu/srchome" +
      " && rm -rf /home/ubuntu/srchome/log" +
      " && mkdir -p /home/ubuntu/srchome/log" +
      " && cd /home/ubuntu/srchome " +
      " && (bash ./gradlew jar 2>&1 | tee -a /home/ubuntu/srchome/log/build.log )" +
      s" && /usr/local/bin/with_heartbeat.sh 1m bash /usr/local/bin/with_instance_role.sh can_terminateInstances2 /usr/local/bin/rclone.sh --s3-region us-west-2 sync /home/ubuntu/srchome/log mys3:weatherballoon-test1/log/${idrun} "
    val pkfile = new File(System.getenv("HOME"), ".ssh/id_gs_temp_2019-01").toString()

    val cmd = s"(echo about to sleep && sleep 60 && echo done sleeping) | tee -a /tmp/test_nn_${System.currentTimeMillis()}.log"

    val numTries = 50
    val msBetweenPolls = 5000
    val sConnectTimeout = 5000

    try {
      rcloneUp(cfg)

      var nodeaddr : Option[(NodeMetadata,String)] = None
      tryFindNode(cfg.group1, cfg.tag, cfg.cred) match {
        case Some((node, addr)) =>
          nodeaddr = Some((node, addr))
        // do nothing
        case None =>
          // presume we should make a node
          println("looks like we should make a node")
          provisionViaAws(cfg.region, cfg.group1, cfg.keyPair, cfg.os.stUser, cfg.os.ami, cfg.tag, cfg.cred)
          Thread.sleep(10000)
          tryFindNode(cfg.group1, cfg.tag, cfg.cred) match {
            case Some((node, addr)) =>
              // now there will eventually be a node
              nodeaddr = Some((node, addr))
            case None =>
              throw new RuntimeException("looks like we didn't succeed in making a node")
          }
      }
      execAndRetry(cfg.os.username, pkfile, "echo hello", cfg.group1, cfg.cred, cfg.tag, msBetweenPolls, sConnectTimeout, numTries)
      println("about to sync")
      //rsync(nodeaddr.get._2, cfg)
      execAndRetry(cfg.os.username, pkfile, cmd1, cfg.group1, cfg.cred, cfg.tag, msBetweenPolls, sConnectTimeout, numTries)
    } catch {
      case ex:Throwable =>
        val sw = new StringWriter()
        val buf = new PrintWriter(sw)
        //ex.fillInStackTrace().printStackTrace(buf)
        ex.printStackTrace(buf)
        buf.close()
        println(sw.getBuffer.toString)
        System.exit(1)
    }
    System.exit(0)
  }

  def getCfg() = {
    val adirHome = System.getenv("HOME")

    import org.json4s._

    import org.json4s.jackson.Serialization.{read, write}
    import config.formats
    var cfg : config.Remoter = null
    try {
      val afileConfig = new File(new File(adirHome), ".weatherballoon.json")
      val textConfig = io.Source.fromFile(afileConfig).mkString
      cfg = read[config.Remoter](textConfig)
    } catch {
      case ex:Throwable =>
        throw new RuntimeException("could not parse configuration: ",ex)
    }
    var cred : config.Aws = null
    try {
      val afileCred = new File(new File(adirHome), ".weatherballoon_cred.json")
      val textCred = io.Source.fromFile(afileCred).mkString
      cred = read[config.Aws](textCred)
    } catch {
      case ex:Throwable =>
        throw new RuntimeException("could not parse credentials: ",ex)
    }
    println(s"cfg=${cfg}")
    println(s"cred=${cred}")
    cfg.copy(
      sync=cfg.sync.copy(adirFrom=System.getProperty("user.dir")),
      cred=cred
    )
  }

  def main(args: Array[String]): Unit = {
    val cfg = getCfg()
//    testListNodes(cfg)
//    testFindNode(cfg)
//    testProvision(cfg)
//    testSsh(cfg)
    testProvisionRun(cfg)
  }
}