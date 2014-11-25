package org.ferrit.core.filter

import org.ferrit.core.filter.FirstMatchUriFilter._
import org.ferrit.core.uri.CrawlUri

import scala.util.matching.Regex

/**
 * A UriFilter strategy that accepts or rejects a URI based on the 
 * first matching rule to win.
 * If a URI is not matched by any rule then it is automatically rejected 
 * to prevent the crawl job running on indefinitely.
 *
 */
class FirstMatchUriFilter(val rules: Seq[Rule]) extends UriFilter {
  override def accept(uri: CrawlUri): Boolean = test(uri).accepted

  override def explain(uri: CrawlUri): String =
    test(uri).matchedRule match {
      case Some(a: Accept) => AcceptMsg.format(uri, a.regex)
      case Some(r: Reject) => RejectMsg.format(uri, r.regex)
      case None => RejectDefaultMsg.format(uri)
    }

  def test(uri: CrawlUri): Result = {
    val result = rules.find(r => r.regex.findPrefixMatchOf(uri.crawlableUri).nonEmpty)
    Result(result.nonEmpty && result.get.accept, result)
  }

  override def toString: String = 
    this.getClass.getSimpleName + "(" + rules.mkString(",") + ")"

}

object FirstMatchUriFilter {
  val AcceptMsg: String =
    "The URI [%s] is accepted by pattern [%s]"
  val RejectMsg: String =
    "The URI [%s] is rejected by pattern [%s]"
  val RejectDefaultMsg: String =
    "The URI [%s] is rejected because no accept pattern accepted it"

  sealed abstract class Rule(val regex: Regex, val accept: Boolean) {
    def name:String = getClass.getSimpleName.toLowerCase
  }

  sealed case class Result(accepted: Boolean, matchedRule: Option[Rule])

  case class Accept(r: Regex) extends Rule(r, true)

  case class Reject(r: Regex) extends Rule(r, false)

}
