package com.groovescale

import java.io.File

object config {
  case class Aws(
                        id:String,
                        secret:String
                      )

  case class Os(
                       val ami: String,
                       val username: String,
                       val stUser: String
                     )

  case class Sync(
                 adirFrom:String,
                 adirRemote:String,
                 fileExcludes:String
                 )

  case class Remoter(
                            val region: String,
                            val group1: String,
                            val cred: config.Aws,
                            val tag: String,
                            val keyPair: String,
                            val os: config.Os,
                            val sync:Sync
                          )
  {
    def kpFile() = {
      new File(new File(System.getenv("HOME"), ".ssh"), keyPair).toString()
    }
  }

  import org.json4s._
  import org.json4s.jackson.JsonMethods._

  implicit val formats = (DefaultFormats
    + FieldSerializer[Aws]()
    + FieldSerializer[Os]()
    + FieldSerializer[Remoter]())
}
