package com.groovescale.weatherballoon

import java.io.File

object config {
  case class AwsCred(
                        id:String,
                        secret:String
                      )

  case class AwsOs(
                       val ami: String,
                       val username: String
                     )

  case class AwsProvisioner(
                          val kind: String = "aws",
                          val region: String,
                          val group1: String,
                          val cred: config.AwsCred,
                          val roleOfInstance: String,
                          val keyPair: String,
                          val instanceType: String,
                          val gbsizeOfMainDisk: Int,
                          val os: config.AwsOs
                        )
  {
    def nameOfRole : String = {
      roleOfInstance.split("/").last
    }
  }

  case class Sync(
                 adirLocal:String,
                 adirServer:String,
                 dirStorage:String,
                 dirsToExclude:Seq[String]
                 )

  case class Remoter(
                      val provisioner: AwsProvisioner,
                      val tag: String,
                      val minutesMaxRun: Int,
                      val minutesMaxIdle: Int,
                      val spooler: String,
                      val sync:Sync
                          )
  {
    def kpFile() = {
      new File(new File(System.getenv("HOME"), ".ssh"), provisioner.keyPair).toString()
    }
  }

  import org.json4s._

  implicit val formats = (DefaultFormats
    + FieldSerializer[AwsCred]()
    + FieldSerializer[AwsOs]()
    + FieldSerializer[Remoter]())
}
