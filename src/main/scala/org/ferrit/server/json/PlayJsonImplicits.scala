package org.ferrit.server.json

import play.api.libs.json.Json


object PlayJsonImplicits {

  implicit val errorMessageWrites = Json.writes[ErrorMessage]
  implicit val messageWrites = Json.writes[Message]
  implicit val idReads = Json.reads[Id]
  implicit val idWrites = Json.writes[Id]

}