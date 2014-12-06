package org.ferrit.core.parser

import org.ferrit.core.uri.CrawlUri

/** A Link contains information extracted from an HTML or CSS resource
  * element such as an anchor or image element.
  *
  * Example:
  *
  * Assume the following is parsed from an HTML document:
  *
  * <pre>
  *   <a href="http://site.com/page" nofollow="true">The Link Text</a>
  * </pre>
  *
  * <ul>
  *   <li>element  = "a", because the element is an anchor <a></li>
  *   <li>uriAttribute = href,
  *                  for an image element this would be "src" instead</li>
  *   <li>linkText = "The Link Text",
  *                   which is the text node inside the element.</li>
  *   <li>noFollow = true, because source element contains an attribute
  *                  indicating that the crawler should not follow it</li>
  *   <li>crawlUri = http://site.com/page
  *                  (relative URIs should be converted to absolute)</li>
  *   <li>failMessage = contains error message that may occur when parsing the
  *                     link, typically because the URI being formed is invalid.</li>
  * </ul>
  *
  */
case class Link(
  element: String,
  uriAttribute: String,
  linkText: String,
  noFollow: Boolean = false,
  crawlUri: Option[CrawlUri],
  failMessage: Option[String])