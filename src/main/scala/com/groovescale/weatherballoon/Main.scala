package com.groovescale.weatherballoon

import java.io._
import java.util.Properties

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model._
import com.jcraft.jsch.{ChannelExec, JSch, Session}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object Main {
  val log = LoggerFactory.getLogger(Main.getClass())

  import scala.collection.JavaConverters._

  def main(args: Array[String]): Unit = {
    val cmd = args
    val cfg = ConfigUtil.getCfg()
    AwsProvisioner.runProvisioned(cmd, cfg)
  }
}
