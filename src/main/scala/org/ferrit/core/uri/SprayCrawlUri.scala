package org.ferrit.core.uri

import org.ferrit.core.uri.SprayUriReader._
import spray.http.Uri.{ ParsingMode, Query }
import spray.http.{ StringRendering, Uri }
import spray.util.UTF8

import scala.annotation.tailrec
import scala.collection.immutable.SortedMap

/** An implementation of CrawlUri backed by the Spray Uri class.
  */
case class SprayCrawlUri(originalUri: String) extends CrawlUri {

  require(
    originalUri != null && originalUri.trim.nonEmpty,
    "the URI string was missing or empty")

  /** Internally we store a cached copy of the normalized URI
    * which unfortunately doubles the storage required becase the original
    * URI passed in to the constructor is also retained.
    * This could perhaps be replaced by a constructor in the companion
    * object that discards the original uncleansed URI after construction,
    * but to do so means losing the original URI value which sometimes is
    * useful to keep a hold of.
    */
  private[uri] val normalUri: String = render(getCrawlableUri)

  /** Override default case class behaviour.
    * Is required in this case because we don't want to consider the
    * raw input URI for comparison, but instead the normalized URI, else
    * 'URI already seen' tests will fail to handle duplicates.
    */
  override def equals(a: Any): Boolean =
    if (a != null) a equals normalUri // avoid calling a.toString if possible
    else normalUri equals a.toString

  override def hashCode: Int = normalUri.hashCode

  override def toString: String = normalUri

  override def crawlableUri: String = normalUri

  override def absoluteCrawlableUri(base: CrawlUri): CrawlUri = {
    val baseUri = makeUri(base.crawlableUri)
    val absUri = getCrawlableUri.resolvedAgainst(baseUri)
    SprayCrawlUri(render(absUri))
  }

  private def getCrawlableUri: Uri = {
    val sReader = reader.asInstanceOf[SprayUriReader]
    sReader.uri.withoutFragment.withQuery(
      Query(sReader.sortedQueryMap -- CrawlUri.SessionIdKeys))
  }

  override def reader: UriReader = new SprayUriReader(originalUri, this)

  private def render(uri: Uri): String =
    uri.render(new StringRendering, UTF8).get
}

class SprayUriReader(val uriString: String, override val crawlUri: CrawlUri) extends UriReader {
  val uri: Uri = makeUri(uriString)

  override def scheme = uri.scheme

  override def authority = uri.authority.toString

  override def path = uri.path.toString

  /** Spray converts default ports for schemes like http or ssh to 0
    */
  override def schemeToPort: String = {
    val port = uri.authority.port
    val p = if (port == 0) "" else ":" + port
    uri.scheme + "://" + uri.authority.host + p
  }

  /** Returns a new Query based on the given Query with
    * the key values sorted alphanumerically by key.
    * The intention is an additional normalization step
    * to reduce chance of duplicate URI being crawled.
    * Adapted from spray.http.Uri.Query.toMap method
    */
  def sortedQueryMap: SortedMap[String, String] = {
    @tailrec
    def append(map: SortedMap[String, String], q: Query): SortedMap[String, String] =
      if (q.isEmpty) map
      else append(map.updated(q.key, q.value), q.tail)
    append(SortedMap.empty, uri.query)
  }

}

object SprayUriReader {

  /** The choice of ParsingMode influences treatment of query string parsing.
    * RelaxedWithRawQuery is the most lenient and does not touch query string,
    * but tradeoff is that parsing Query into parts is not possible.
    * The Relaxed setting allows  query to be parsed into key values,
    * making it possible to remove parameters, but values get decoded.
    *
    * Newline characters: Windows \r\n, Max OS \n, Unix \r
    *
    * @see http://stackoverflow.com/questions/15433188/r-n-r-n-what-is-the-difference-between-them
    *
    */
  def makeUri(uri: String): Uri = Uri(clean(uri), ParsingMode.Relaxed)

  private def clean(uri: String): String =
    uri
      .replaceAll(" ", "%20") // Spray blows up with spaces or newline characters
      .replaceAll("\r", "") // Remove 3 kinds of newline: \r\n, \r, \n
      .replaceAll("\n", "")

}