package org.ferrit.core.filter

import org.ferrit.core.uri.CrawlUri

/**
 * Strategy for deciding if the given URI can be followed by a crawler.
 *
 */
trait UriFilter {

  /**
   * Tests if the given URI is accepted.
   */
  def accept(uri: CrawlUri): Boolean

  /**
   * Optional method that can be overridden to explain why the given URI is
   * accepted or rejected. Useful for debugging and management tools.
   */
  def explain(uri: CrawlUri): String = "No explanation available"

}

object UriFilter {

  import scala.language.implicitConversions
  
  /** 
   * Sugar to reduce boilerplate conversions of String to CrawlUri,
   * in particular during tests.
   */
  implicit def implicitConvertStringToCrawlUri(uri: String): CrawlUri = CrawlUri(uri)

}

