package com.groovescale.weatherballoon

import java.io.File

object config {
  case class AwsCred(
                        id:String,
                        secret:String
                      )

  case class AwsRole(
                      arn:String
                    )
  {
    def name : String = {
      arn.split("/").last
    }
  }

  case class Os(
                       val ami: String,
                       val username: String
                     )

  case class Sync(
                 adirLocal:String,
                 adirServer:String,
                 dirStorage:String,
                 fileExcludes:String
                 )

  case class Remoter(
                      val region: String,
                      val group1: String,
                      val cred: config.AwsCred,
                      val roleOfInstance: config.AwsRole,
                      val tag: String,
                      val keyPair: String,
                      val instanceType: String,
                      val os: config.Os,
                      val sync:Sync
                          )
  {
    def kpFile() = {
      new File(new File(System.getenv("HOME"), ".ssh"), keyPair).toString()
    }
  }

  import org.json4s._

  implicit val formats = (DefaultFormats
    + FieldSerializer[AwsCred]()
    + FieldSerializer[Os]()
    + FieldSerializer[Remoter]())
}
