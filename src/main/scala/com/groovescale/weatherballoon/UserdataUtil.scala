package com.groovescale.weatherballoon

import java.io.{BufferedReader, InputStreamReader}

import com.groovescale.weatherballoon.AwsProvisioner.getClass

object UserdataUtil {
  import scala.collection.JavaConverters._

  private def getUserdataScript(res:String) : Seq[String] = {
    val str = getClass().getResourceAsStream(res)
    val r = new BufferedReader(new InputStreamReader(str))
    r.lines().iterator().asScala.toSeq
  }

  def getUserdataScriptRaw() : Seq[String] = {
    val scriptBig = Seq(
      "/file/install_deps.sh",
      "/file/install_heartbeat_cron.sh"
    ).flatMap(getUserdataScript)
    val lines = (Seq("#!/usr/bin/env bash") ++ scriptBig)
    lines
  }
}
