package com.groovescale.weatherballoon

import java.io.{BufferedReader, InputStreamReader}

import com.groovescale.weatherballoon.AwsProvisioner.getClass

object UserdataUtil {
  import scala.collection.JavaConverters._

  val q = "\""

  private def noquote(v:String) = {
    v.replaceAllLiterally("\"","")
  }
  private def genHeredoc(sh:Seq[String], fn0:String, eof:String, adirOut:String) : Seq[String] = {
    val fn = noquote(fn0)
    val shHeader = Seq(
      s"cat > ${q}/tmp/${fn}${q} <<${q}${eof}${q}"
    )
    val shFooter = Seq(
      eof,
      s"install ${q}/tmp/${fn}${q} ${q}${adirOut}/${fn}${q}",
      s"rm -f ${q}/tmp/${fn}${q}"
    )
    shHeader ++ sh ++ shFooter
  }

  private def genVar(name:String, v:String) = {
    s"${name}=${q}${noquote(v)}${q}"
  }

  case class Script(
                   val res:String,
                   val eof:String,
                   val vs:Seq[(String,String)]
                   )

  def getScriptFromResource(script:Script) : Seq[String] = {
    val str = getClass().getResourceAsStream("/file/"+script.res)
    val r = new BufferedReader(new InputStreamReader(str))
    r.lines().iterator().asScala.toSeq
  }

  def genScriptOnly(script: Script) = {
    val shebang =
      if (script.vs.isEmpty)
        Seq()
      else
        Seq("#!/bin/bash")
    val init = script.vs.map(x => genVar(x._1, x._2))
    val lines = getScriptFromResource(script)
    (shebang ++ init ++ lines)
  }

  def genScriptHeredoc(adirOut:String)(script:Script) : Seq[String] = {
    val lines = genScriptOnly(script)
    genHeredoc(lines, script.res, script.eof, adirOut)
  }

  def genUserdataForWeatherballoon(adirOut:String, minutesMaxIdle:Int) : Seq[String] = {
    val scriptHeredoc = Seq(
      Script("heartbeat.sh","EOF1X",Seq(("numMinutes",minutesMaxIdle.toString))),
      Script("with_heartbeat.sh","EOF2X",Seq()),
      Script("with_instance_role.sh","EOF3X",Seq()),
      Script("rclone.sh","EOF4X",Seq()),
      Script("with_logging.sh","EOF5X",Seq()),
      Script("with_sync_updown.sh","EOF6X",Seq()),
      Script("spool_via_tmux.sh","EOF7X",Seq())
    ).flatMap(genScriptHeredoc(adirOut))
    val scriptRun = Seq(
      Script("install_deps.sh", "EOF8X", Seq(("afileDone", config.Hardcoded.afileDone))),
      Script("install_heartbeat_cron.sh", "EOF9X", Seq(("afileDone", config.Hardcoded.afileDone)))
    ).flatMap(getScriptFromResource)
    val lines = (Seq("#!/usr/bin/env bash") ++ scriptHeredoc ++ scriptRun)
    lines
  }
}
