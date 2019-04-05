package com.groovescale.weatherballoon

import java.io.{InputStream, OutputStream, PipedInputStream, PipedOutputStream}
import java.util.Properties

import com.jcraft.jsch.{ChannelExec, JSch, Session}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object ExecUtil {
  val log = LoggerFactory.getLogger(ExecUtil.getClass())

  val sizeOfBuffer = 1024

  class TooManyRetriesException(msg:String) extends RuntimeException(msg) {}

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
    try {
      val session: Session = sshsessionCreate(hostname, username, pkfile)
      session.connect(30000) // making a connection with timeout.
      return execViaSshImplJsch2(session, spooler, command)
    } catch {
      case ex: Throwable =>
        return Failure(ex)
    }
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

  def pumpOnce(in:InputStream, os:OutputStream, tmpPumpOnce:Array[Byte]) : Unit = {
    while(in.available > 0) {
      //log.info("trying to read")
      val i = in.read(tmpPumpOnce, 0, sizeOfBuffer)
      if (i < 0) {
        log.info("available input has no input.  Error?")
        return
      }
      System.out.print(new String(tmpPumpOnce, 0, i))
    }
  }

  def execViaSshImplJsch2(session:Session, spooler:String, command:String) : Try[Int] = {
    val bufStdout = new Array[Byte](sizeOfBuffer)
    val bufStderr = new Array[Byte](sizeOfBuffer)
    val chan = session.openChannel("exec")
    // If we don't allocate a pty, sshd will not know to kill our process if we ctrl+C weatherballoon
    chan.asInstanceOf[ChannelExec].setPty(!(spooler == "tmux"))

    // TODO: By specification we cannot retry our command.  However, we could greatly improve
    // our chances of a successful command attempt as follows:
    //   1) Connect with a piped stdin
    //   2) Once connected, verify our ssh connection by running a placebo command
    //   3) Drop the connection and fail if the placebo command doesn't work
    //   4) Proceed to attempt our command.
    // This way, we would only fail to attempt the command in the very unlikely case that
    // we dropped our socket right between verifying the ssh connection and the command.
    //
    // What we currently do is basically the same, but we use two entirely different ssh
    // connections.
    //
    // A downside of this approach would be to make it difficult to use stdin piping
    // for some other reason.
    chan.asInstanceOf[ChannelExec].setCommand(command)
    val is = chan.getInputStream

    val out = new PipedOutputStream()
    val pout = new PipedInputStream(out)
    chan.setOutputStream(out)

    val err = new PipedOutputStream()
    val perr = new PipedInputStream(err)
    chan.setExtOutputStream(err)

    chan.connect(10000)
    while(!chan.isClosed) {
      pumpOnce(pout, System.out, bufStdout)
      pumpOnce(perr, System.out, bufStderr)
      Thread.sleep(20)
    }
    pumpOnce(pout, System.out, bufStdout)
    pumpOnce(perr, System.out, bufStderr)
    System.out.flush()
    if(chan.isConnected) {
      chan.disconnect()
    }
    if(session.isConnected) {
      session.disconnect()
    }
    return Success(chan.getExitStatus)
  }

}
