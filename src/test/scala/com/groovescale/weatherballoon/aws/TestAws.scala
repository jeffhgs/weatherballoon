package com.groovescale.weatherballoon.aws

import com.groovescale.weatherballoon._
import org.scalatest.junit.JUnitSuite

import scala.collection.mutable.ListBuffer
import org.junit.Test
import org.junit.Before
import org.slf4j.LoggerFactory

class TestAws extends JUnitSuite {
  val log = LoggerFactory.getLogger(classOf[TestAws])

  var sb: StringBuilder = _
  var lb: ListBuffer[String] = _

  @Before def initialize() {
    sb = new StringBuilder("ScalaTest is ")
    lb = new ListBuffer[String]
  }

  def testProvision(cfg:config.Remoter) = {
    AwsProvisioner.provisionViaAws(
      cfg.provider,
      cfg.tag
    )
  }
  def testListNodes(
                     cfg:config.Remoter
                   ) =
  {
    val nodes = AwsProvisioner.listNodesViaAws(cfg.provider)
    log.info(s">> No of nodes ${nodes.size}")
    for (node <- nodes) {
      log.info(">>>>  " + node)
      if(node.tags.contains(cfg.tag)) {
        log.info("woot!")
      }
    }
  }
  def testFindNode(
                    cfg:config.Remoter
                  ) =
  {
    val node = AwsProvisioner.tryFindNode(cfg.provider, cfg.tag)
    log.info(">>>>  " + node)
  }

  def testSsh(cfg:config.Remoter) = {
    log.info("hello")
    val hostname = "ec2-54-186-244-37.us-west-2.compute.amazonaws.com"
    val sConnectTimeout = 10
    val cmd = "echo hello"
    val value = ExecUtil.execViaSsh(hostname, cfg.provider.os.username, cfg.kpFile(), sConnectTimeout, cmd)
    value match {
      case scala.util.Success(value) =>
        // TODO: for logging, limit stdout and stderr to a maximum number of characters
        log.info(s"status=${value}\n\tcommand=\n\t${cmd}")
      case scala.util.Failure(ex) =>
        log.warn(ex.toString)
    }
    log.info("sleeping")
    Thread.sleep(10000)
  }


  @Test def verifyListNodes(): Unit = {
    val cfg = ConfigUtil.getCfg()
    testListNodes(cfg)
  }
  @Test def verifyFindNode(): Unit = {
    val cfg = ConfigUtil.getCfg()
    testFindNode(cfg)
  }
  @Test def verifyProvision(): Unit = {
    val cfg = ConfigUtil.getCfg()
    testProvision(cfg)
  }
  @Test def verifySsh(): Unit = {
    val cfg = ConfigUtil.getCfg()
    testSsh(cfg)
  }
}