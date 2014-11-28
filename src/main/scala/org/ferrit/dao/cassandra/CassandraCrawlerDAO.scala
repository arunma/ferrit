package org.ferrit.dao.cassandra

import com.datastax.driver.core.{PreparedStatement, Row, Session}
import org.ferrit.core.json.PlayJsonImplicits
import org.ferrit.core.model.Crawler
import org.ferrit.dao.CrawlerDAO
import org.ferrit.dao.cassandra.CassandraDAO._
import play.api.libs.json._

class CassandraCrawlerDAO(ttl: CassandraColumnTTL)(implicit session: Session) extends CrawlerDAO {

  import org.ferrit.dao.cassandra.CassandraTables.{Crawler => CrawlerTable}

  val stmtInsert: PreparedStatement = session.prepare(
    s"INSERT INTO $CrawlerTable (crawler_id, config_json) VALUES (?,?)"
  )
  val stmtDelete: PreparedStatement = session.prepare(
    s"DELETE FROM $CrawlerTable WHERE crawler_id = ?"
  )
  val stmtFind: PreparedStatement = session.prepare(
    s"SELECT * FROM $CrawlerTable WHERE crawler_id = ?"
  )
  val stmtFindAll: PreparedStatement = session.prepare(
    s"SELECT * FROM $CrawlerTable"
  )

  override def insert(crawler: Crawler): Unit = {
    val json = Json.stringify(Json.toJson(crawler.config)(PlayJsonImplicits.crawlConfigWrites))
    session.execute {
      stmtInsert.bind()
          .setString("crawler_id", crawler.crawlerId)
          .setString("config_json", json)
    }
  }

  override def delete(crawlerId: String): Unit = {
    session.execute(stmtDelete.bind().setString("crawler_id", crawlerId))
  }

  override def find(crawlerId: String): Option[Crawler] =
    mapOne {
      session.execute(stmtFind.bind().setString("crawler_id", crawlerId))
    } { rowToEntity }

  override def findAll(): Seq[Crawler] = {
    val crawlers = mapAll {
      session.execute(stmtFindAll.bind())
    } { rowToEntity }

    crawlers.sortWith(
      (c1, c2) => c1.config.crawlerName.toLowerCase < c2.config.crawlerName.toLowerCase
    )
  }

  private def rowToEntity(row: Row) = {
    val json = row.getString("config_json")
    Json.fromJson(Json.parse(json))(PlayJsonImplicits.crawlConfigReads) match {
      case JsSuccess(config, path) => Crawler(row.getString("crawler_id"), config)
      case JsError(errors) => throw new IllegalArgumentException(s"Cannot parse config $json")
    }
  }
}