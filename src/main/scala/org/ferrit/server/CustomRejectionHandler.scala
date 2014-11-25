package org.ferrit.server

import org.ferrit.server.json.ErrorMessage
import org.ferrit.server.json.PlayJsonImplicits._
import spray.http.StatusCodes
import spray.httpx.PlayJsonSupport._
import spray.routing.{Directives, MalformedRequestContentRejection, RejectionHandler}

object CustomRejectionHandler extends Directives {
  val UnmarshallErrorMsg = "The request entity could not be unmarshalled."
  val BadEntityMsg = "No %s found with identifier [%s]"
  val BadParamMsg = "Parameter [%s] has invalid value [%s]"

  val customRejectionHandler: RejectionHandler = RejectionHandler {
    case MalformedRequestContentRejection(msg, cause) :: _ =>
      complete {
        val sc = StatusCodes.BadRequest
        sc -> ErrorMessage(sc.intValue, UnmarshallErrorMsg)
      }

    case BadEntityRejection(name, value) :: _ =>
      complete {
        val sc = StatusCodes.NotFound
        sc -> ErrorMessage(sc.intValue, BadEntityMsg.format(name, value))
      }

    case BadParamRejection(name, value) :: _ =>
      complete {
        val sc = StatusCodes.BadRequest
        sc -> ErrorMessage(sc.intValue, BadParamMsg.format(name, value))
      }

  }

}