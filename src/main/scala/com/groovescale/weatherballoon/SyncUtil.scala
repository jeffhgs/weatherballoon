package com.groovescale.weatherballoon

import java.io.File

import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object SyncUtil {
  val log = LoggerFactory.getLogger(SyncUtil.getClass())

  def sanitizeForBash(st:String) : String =
  {
    // Quotes and spaces are not supported
    // TODO: implement and test quoting for spaces
    st.replaceAllLiterally("\"","")
      .replaceAllLiterally("\'","")
      .replaceAllLiterally(" ","")
  }
  def rcloneUp(cfg:config.Remoter) : Try[Int] = {
    import scala.sys.process._
    val cmdRclone = Seq(
      "env", s"AWS_ACCESS_KEY_ID=${cfg.provisioner.cred.id}",
      s"AWS_SECRET_ACCESS_KEY=${cfg.provisioner.cred.secret}",
      "RCLONE_CONFIG_MYS3_TYPE=s3",
      "rclone",
      "--config", "/dev/null",
      "--s3-env-auth", "--s3-region", cfg.provisioner.region)
    val cmdExclude = cfg.sync.dirsToExclude.flatMap((dir:String) => Seq(
      "--exclude", sanitizeForBash(dir)
    ))
    val cmdSync = Seq(
      "--progress",
      "sync", cfg.sync.adirLocal, s"mys3:${cfg.sync.dirStorage}/srchome"
    )
    val cmd = cmdRclone ++ cmdExclude ++ cmdSync
    val status = cmd.!
    if(status==0) Success(0) else Failure(new RuntimeException(s"status=${status}"))
  }
}
