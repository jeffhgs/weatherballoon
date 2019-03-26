package com.groovescale.weatherballoon

import java.io.File

import com.groovescale.weatherballoon.Run1.log
import org.slf4j.LoggerFactory

object ConfigUtil {
  import com.groovescale.weatherballoon.config._

  val log = LoggerFactory.getLogger(ConfigUtil.getClass())

  def findCfg(name:String) : File = {
    var dir = new File(new File("").getAbsolutePath)
    while(dir != null) {
      val fileCfg = new File(dir, name)
      if(fileCfg.exists())
        return fileCfg
      dir = dir.getParentFile
    }
    return null;
  }

  def getCfg() = {
    val adirHome = System.getenv("HOME")

    import config.formats
    import org.json4s.jackson.Serialization.read
    var cfg : config.Remoter = null
    try {
      val afileConfig = findCfg(".weatherballoon.json")
      val textConfig = io.Source.fromFile(afileConfig).mkString
      cfg = read[config.Remoter](textConfig)
    } catch {
      case ex:Throwable =>
        throw new RuntimeException("could not parse configuration: ",ex)
    }
    var cred : config.AwsCred = null
    try {
      val afileCred = findCfg(".weatherballoon_cred.json")
      val textCred = io.Source.fromFile(afileCred).mkString
      cred = read[config.AwsCred](textCred)
    } catch {
      case ex:Throwable =>
        throw new RuntimeException("could not parse credentials: ",ex)
    }
    log.info(s"cfg=${cfg}")
    if(cred==null) {
      log.error("no credentials found")
      throw new RuntimeException("no credentials found")
    }
    cfg.copy(
      sync=cfg.sync.copy(adirLocal=System.getProperty("user.dir")),
      cred=cred
    )
  }


}
