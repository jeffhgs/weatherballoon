package com.groovescale.weatherballoon

import java.io.{BufferedWriter, FileWriter}
import java.nio.file.Path

import com.groovescale.weatherballoon.UserdataUtil.{Script, genUserdataScript, getUserdataScript}
import com.groovescale.weatherballoon._
import org.scalatest.junit.JUnitSuite

import scala.collection.mutable.ListBuffer
import org.junit.Test
import org.junit.Before
import org.slf4j.LoggerFactory

class TestUserdataUtil extends JUnitSuite {
  val log = LoggerFactory.getLogger(classOf[TestUserdataUtil])

  var sb: StringBuilder = _
  var lb: ListBuffer[String] = _

  @Before def initialize() {
    sb = new StringBuilder("ScalaTest is ")
    lb = new ListBuffer[String]
  }

  def getUserdataTestScript(adirOut:String) : Seq[String] = {
    val scriptHeredoc = Seq(
      Script("customized.sh","EOF1X",Seq(("message","hello")))
    ).flatMap(genUserdataScript(adirOut))
    val scriptRun = Seq(
      Script("check_customized.sh", "EOF2X", Seq())
    ).flatMap(getUserdataScript)
    val lines = (Seq("#!/usr/bin/env bash") ++ scriptHeredoc ++ scriptRun)
    lines
  }

  import java.nio.file.Files
  import java.nio.file.Path

  @Test def verifyListNodes(): Unit = {
    import scala.sys.process._
    val adirTemp = Files.createTempDirectory("TestUserdataUtil")
    val afileScript1 = adirTemp.resolve("script1.sh")
    val bw = new BufferedWriter(new FileWriter(afileScript1.toFile))
    for(line <- getUserdataTestScript(adirTemp.toString)) {
      bw.write(line)
      bw.write("\n")
    }
    bw.close()
    afileScript1.toFile.setExecutable(true)
//    val status = ("bash \""+afileScript1.toString+"\"").!
    val status = afileScript1.toString.!
    assert(status == 0)
  }
}