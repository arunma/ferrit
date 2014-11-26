package org.ferrit.server

import play.api.libs.json.Json

case class Id(id: String)

case class Message(message: String)

case class ErrorMessage(statusCode: Int, message: String)

object PlayJsonImplicits {
  implicit val errorMessageWrites = Json.writes[ErrorMessage]
  implicit val messageWrites = Json.writes[Message]
  implicit val idReads = Json.reads[Id]
  implicit val idWrites = Json.writes[Id]
}
