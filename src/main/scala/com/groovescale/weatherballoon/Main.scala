package com.groovescale.weatherballoon

import org.slf4j.LoggerFactory

object Main {
  val log = LoggerFactory.getLogger(Main.getClass())

  def usage() = {
    System.err.println(
      """
        |usage:
        |  weatherballoon.sh -- <command to run remotely>
      """.stripMargin)
  }

  def main(args: Array[String]): Unit = {
    val iCommand = args.indexOf("--")
    if(iCommand >= 0 && (args.length - (iCommand+1) >= 1)) {
      val cmd = args.drop(iCommand+1)
      val cfg = ConfigUtil.getCfg()
      AwsProvisioner.runProvisioned(cmd, cfg)
    } else {
      usage()
    }
  }
}
