package com.groovescale.weatherballoon

import java.io.{InputStream, OutputStream, PipedInputStream, PipedOutputStream}
import java.util.Properties

import com.jcraft.jsch.{ChannelExec, JSch, Session}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object ExecUtil {
  val log = LoggerFactory.getLogger(ExecUtil.getClass())

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

  def pumpOnce(in:InputStream, os:OutputStream, tmpPumpOnce:Array[Byte]) : Unit = {
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
    val tmpPumpOnce = new Array[Byte](1024)
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
      pumpOnce(pout, System.out, tmpPumpOnce)
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
      // TODO: make ExecUtil not depend on ImplAws
      AwsProvisioner.tryFindNode(group1, region, tag, cred) match {
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
}
