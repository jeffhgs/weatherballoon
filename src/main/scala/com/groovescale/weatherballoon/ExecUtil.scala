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
                  spooler:String,
                  command:String
                ) : Try[Int] =
  {
    execViaSshImpl(hostname, username, pkfile, sConnectTimeout, spooler, command)
  }

  private def sshsessionCreate(hostname: String, username: String, pkfile: String) = {
    val props = new Properties()
    props.put("StrictHostKeyChecking", "no")
    val jsch = new JSch();
    //JSch.setLogger(new JSCHLogger());
    jsch.addIdentity(pkfile)
    val session = jsch.getSession(username, hostname, 22)
    session.setConfig(props)
    session
  }

  def execViaSshImpl(
                      hostname:String,
                      username:String,
                      pkfile:String,
                      //                fingerprint:String,
                      sConnectTimeout : Int,
                      spooler:String,
                      command:String
                    ) : Try[Int] =
  {
    try {
      val session: Session = sshsessionCreate(hostname, username, pkfile)
      session.connect(30000) // making a connection with timeout.
      return execViaSshImplJsch2(session, spooler, command)
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

  def execViaSshImplJsch2(session:Session, spooler:String, command:String) : Try[Int] = {
    val tmpPumpOnce = new Array[Byte](1024)
    val tmpPumpOnceErr = new Array[Byte](1024)
    //log.info("connected via jsch")
    val chan = session.openChannel("exec")
    // If we don't allocate a pty, sshd will not know to kill our process if we ctrl+C weatherballoon
    chan.asInstanceOf[ChannelExec].setPty(!(spooler == "tmux"))

    //log.info(s"setting command: ${command}")
    chan.asInstanceOf[ChannelExec].setCommand(command)
    val is = chan.getInputStream

    val out = new PipedOutputStream()
    val pout = new PipedInputStream(out)
    chan.setOutputStream(out)

    val err = new PipedOutputStream()
    val perr = new PipedInputStream(err)
    chan.setExtOutputStream(err)

    //log.info("about to connect")
    chan.connect(10000)
    //log.info("about to pump")
    while(!chan.isClosed) {
      pumpOnce(pout, System.out, tmpPumpOnce)
      pumpOnce(perr, System.out, tmpPumpOnceErr)
      //log.info("sleeping")
      Thread.sleep(1000)
    }
    pumpOnce(pout, System.out, tmpPumpOnce)
    pumpOnce(perr, System.out, tmpPumpOnceErr)
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

}
