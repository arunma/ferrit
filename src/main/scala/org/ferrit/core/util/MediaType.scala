package org.ferrit.core.util

import org.ferrit.core.http.Response

/**
 * Simple media type checking. Long term this would be better handled
 * by Spray.IO or Apache Tika.
 */
object MediaType {
  def is(response: Response, mediaType: ContentType): Boolean =
    response.contentType match {
      case None => false
      case Some(ct) => mediaType match {
        case Text => ct.startsWith("text")
        case Html => ct.startsWith("text/html")
        case Css => ct.startsWith("text/css")
        case _ => false // not supported
      }
    }

  class ContentType

  object Text extends ContentType

  object Html extends ContentType

  object Css extends ContentType
}
