package com.groovescale.weatherballoon

import java.io.File

import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object SyncUtil {
  val log = LoggerFactory.getLogger(SyncUtil.getClass())

  def rcloneUp(cfg:config.Remoter) : Try[Int] = {
    import scala.sys.process._
    val cmd = Seq(
      "env", s"AWS_ACCESS_KEY_ID=${cfg.provisioner.cred.id}",
      s"AWS_SECRET_ACCESS_KEY=${cfg.provisioner.cred.secret}",
      "RCLONE_CONFIG_MYS3_TYPE=s3",
      "rclone",
      "--config", "/dev/null",
      "--s3-env-auth", "--s3-region", cfg.provisioner.region,
      "--exclude", ".git/", "--exclude", ".idea/", "--exclude", "build/", "--exclude", "out/", "--exclude", ".gradle/",
      "--progress",
      "sync", cfg.sync.adirLocal, s"mys3:${cfg.sync.dirStorage}/srchome"
    )
    val status = cmd.!
    if(status==0) Success(0) else Failure(new RuntimeException(s"status=${status}"))
  }
}
