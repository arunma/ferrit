package org.ferrit.core.parser

import org.ferrit.core.http.Response

/** A ContentParser is first asked if it can parse the given (HTTP) Response.
  * If the parser understands the content type and says yes, then it extracts the
  * content from the Response, extracts any links that it finds (e.g. HTML/CSS files)
  * and returns these in a ParserResult.
  */
trait ContentParser {

  /** Returns true if this parser understands the content type of the
    * Response and can parse it and extract links.
    */
  def canParse(response: Response): Boolean

  /** Parses the response entity body from the given Response
    * and returns a ParserResult which contains a list of Link
    * extracted from the resource.
    * Should call canParse first before trying to parse.
    */
  def parse(response: Response): ParserResult

}