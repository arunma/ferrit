package org.ferrit.core.parser

import org.ferrit.core.http.Response

/** Parser to handle various media type. Internally this delegates
  * to the appropriate content parser for the given media type.
  */
class MultiParser(parsers: Seq[ContentParser]) extends ContentParser {
  override def canParse(response: Response): Boolean =
    parserFor(response).nonEmpty

  private[MultiParser] def parserFor(response: Response): Option[ContentParser] =
    response.contentType match {
      case None => None
      case Some(ct) => parsers.find(p => p.canParse(response))
    }

  override def parse(response: Response): ParserResult =
    parserFor(response) match {
      case Some(parser) => parser.parse(response)
      case None => throw new ParseException(s"No parser for response")
    }
}

object MultiParser {
  def default: MultiParser = new MultiParser(
    Seq(HtmlParserJsoup))
}