package com.groovescale.weatherballoon

import org.slf4j.LoggerFactory

object Main {
  val log = LoggerFactory.getLogger(Main.getClass())

  def main(args: Array[String]): Unit = {
    val cmd = args
    val cfg = ConfigUtil.getCfg()
    AwsProvisioner.runProvisioned(cmd, cfg)
  }
}
