package org.ferrit.core.json

import org.ferrit.core.crawler.CrawlConfig
import org.ferrit.core.crawler.CrawlConfigTester.{ Result, Results }
import org.ferrit.core.filter._
import org.ferrit.core.model.{ CrawlJob, DocumentMetaData, FetchLogEntry }
import org.ferrit.core.uri.{ CrawlUri, SprayCrawlUri }
import org.ferrit.core.util.Media
import org.joda.time.DateTime
import play.api.libs.json._

object PlayJsonImplicits {

  val Iso8601Format = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

  implicit val jodaIso8601DateWrites: Writes[DateTime] = new Writes[DateTime] {
    def writes(d: DateTime): JsValue = JsString(d.toString(Iso8601Format))
  }

  implicit val mediaWrites: Writes[Media] = new Writes[Media] {
    def writes(m: Media): JsValue = Json.obj(
      "count" -> m.count,
      "totalBytes" -> m.totalBytes)
  }

  implicit val crawlJobWrites = Json.writes[CrawlJob]

  implicit val fetchLogEntryWrites = Json.writes[FetchLogEntry]

  implicit val uriFilterReads = new Reads[UriFilter] {

    import org.ferrit.core.util.KeyValueParser.parse

    final val FirstMatchUriFilterT = classOf[FirstMatchUriFilter].getName

    final val PriorityRejectUriFilterT = classOf[PriorityRejectUriFilter].getName

    def reads(value: JsValue): JsResult[UriFilter] = {
      val rules = (value \ "rules").as[JsArray].value.map(r => r.as[JsString].value)
      val filter = (value \ "filterClass").as[JsString].value match {
        case FirstMatchUriFilterT =>
          import org.ferrit.core.filter.FirstMatchUriFilter.{ Accept, Reject }
          new FirstMatchUriFilter(
            parse(Seq("accept", "reject"), rules, { (key: String, value: String) =>
              if ("accept" == key) Accept(value.r) else Reject(value.r)
            }))

        case PriorityRejectUriFilterT =>
          import org.ferrit.core.filter.PriorityRejectUriFilter.{ Accept, Reject }
          new PriorityRejectUriFilter(
            parse(Seq("accept", "reject"), rules, { (key: String, value: String) =>
              if ("accept" == key) Accept(value.r) else Reject(value.r)
            }))
      }

      JsSuccess(filter)
    }
  }

  implicit val firstMatchUriFilterWrites = new Writes[FirstMatchUriFilter] {
    def writes(filter: FirstMatchUriFilter): JsValue = Json.obj(
      "filterClass" -> filter.getClass.getName,
      "rules" -> filter.rules.map({ r => s"${r.name}: ${r.regex.toString}" }))
  }

  implicit val priorityRejectUriFilterWrites = new Writes[PriorityRejectUriFilter] {
    def writes(filter: PriorityRejectUriFilter): JsValue = Json.obj(
      "filterClass" -> filter.getClass.getName,
      "rules" -> filter.rules.map({ r => s"${r.name}: ${r.regex.toString}" }))
  }

  implicit val uriFilterWrites = new Writes[UriFilter] {
    def writes(filter: UriFilter): JsValue = filter match {
      case f: FirstMatchUriFilter =>
        firstMatchUriFilterWrites.writes(filter.asInstanceOf[FirstMatchUriFilter])
      case p: PriorityRejectUriFilter =>
        priorityRejectUriFilterWrites.writes(filter.asInstanceOf[PriorityRejectUriFilter])
      case _ => throw new IllegalArgumentException(s"No writer for ${filter.getClass.getName}")
    }
  }

  implicit val crawlUriReads = new Reads[CrawlUri] {
    def reads(value: JsValue): JsResult[CrawlUri] =
      JsSuccess(CrawlUri(value.as[JsString].value))
  }

  implicit val crawlUriWrites = new Writes[CrawlUri] {
    def writes(uri: CrawlUri): JsValue =
      JsString(uri.asInstanceOf[SprayCrawlUri].originalUri)
  }

  implicit val crawlConfigReads = Json.reads[CrawlConfig]

  implicit val crawlConfigWrites = Json.writes[CrawlConfig]

  implicit val crawlConfigTestResultWrites = Json.writes[Result]

  implicit val crawlConfigTestResultsWrites = Json.writes[Results]

  implicit val documentMetaReads = Json.reads[DocumentMetaData]

  implicit val documentMetaWrites = Json.writes[DocumentMetaData]
}