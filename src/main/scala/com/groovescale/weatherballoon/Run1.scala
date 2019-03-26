package com.groovescale.weatherballoon

import java.io._
import java.util.Properties

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model._
import com.jcraft.jsch.{ChannelExec, JSch, Session}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object Run1 {
  val log = LoggerFactory.getLogger(Run1.getClass())

  import scala.collection.JavaConverters._

  def main(args: Array[String]): Unit = {
    val cmd = args
    val cfg = ConfigUtil.getCfg()
    ImplAws.testProvisionRun(cmd, cfg)
  }
}
