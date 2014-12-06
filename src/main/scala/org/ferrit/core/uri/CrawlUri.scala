package org.ferrit.core.uri

import java.net.URI

/** A CrawlUri is an abstraction over a URI.
  *
  * I do not simply just pass around a java.net.URI or Spray.IO Uri because
  * the abstraction will let me plug and play later on.
  *
  * Requirements:
  *
  * <ol>
  *    <li>Not blow up just because it finds a bad URI in the wild</li>
  *    <li>Occupy as little memory as possible</li>
  *    <li>Have fast hashCode for storing in cache collections</li>
  *    <li>Provide convenient access to certain combinations of URI parts</li>
  *    <li>Still provide a way to access any URI part</li>
  *    <li>Should be percent encoded and normalised for crawling</li>
  * </ol>
  */
abstract class CrawlUri {

  /** Returns a resolved URI suitable for issuing an HTTP request.
    * It is called 'crawlableUri' to make clear the difference to the
    * original URI that is passed in to the constructor and transformed.
    *
    * The original URI is transformed by these steps (in no fixed order):
    *
    * <ul>
    *  <li>percent encoded</li>
    *  <li>normalized</li>
    *  <li>fragment portion is dropped</li>
    *  <li>query string sorted</li>
    *  <li>common session ID query string params removed</li>
    * </ul>
    *
    * An HttpClient implementation should call this assuming it is absolute.
    * If not a new instance should be created by calling absoluteCrawlableUri
    * and then pass that to the HttpClient instead.
    */
  def crawlableUri: String

  /** Does the same as crawlableUri except that the URI if relative
    * is transformed by the given base.
    * This version is used by link extractors preparing absolute URIs
    * from URI strings in anchor tags.
    */
  def absoluteCrawlableUri(base: CrawlUri): CrawlUri

  /** Returns a reader for the URI so that the individual parts
    * can be retrieved such as scheme, authority etc.
    * To save memory the CrawlUri typically would not store the individual
    * parts or state relating to the underlying implementation.
    * Instead the parts and implementation are available via the reader.
    */
  def reader: UriReader

}

/** A UriReader is detached from it's enclosing CrawlUri
  * to keep the CrawlUri lightweight.
  * Clients of the CrawlUri request a reader to get at the
  * URI components. Each request for a reader probably
  * creates a new instance, therefore the client would cache the
  * reader if wishing to make multiple invocation for parts.
  * This (for now) should solve the problem of how to make the URI a
  * weak reference without messing around with WeakReference
  * or other kinds of garbage collectible transients.
  */
abstract class UriReader {

  def authority: String

  /** Exposes the path, useful for RobotRules
    */
  def path: String

  def scheme: String

  /** Convenience method to return first part of the URI up to
    * and including the port, e.g. http://www.site.com:8080
    */
  def schemeToPort: String

  def crawlUri: CrawlUri

}

object CrawlUri {

  /** Session ID parameters should be removed from a URI
    * before fetching it which helps to reduce effectively
    * duplicate URIs.
    */
  final val SessionIdKeys: Seq[String] = Seq(
    "jsessionid",
    "phpsessid",
    "aspsessionid")

  /** Constructs a new CrawlUri.
    * The default implementation is a SprayCrawlUri.
    */
  def apply(uri: String): CrawlUri = new SprayCrawlUri(uri)

  /** Constructs a new absolute CrawlUri from the given
    * base and relative URI string.
    */
  def apply(base: CrawlUri, relativeUri: String): CrawlUri =
    CrawlUri(relativeUri).absoluteCrawlableUri(base)

}