package com.groovescale.weatherballoon

import java.io.File

import com.groovescale.weatherballoon.Run1.log
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object SyncUtil {
  val log = LoggerFactory.getLogger(SyncUtil.getClass())

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
}
