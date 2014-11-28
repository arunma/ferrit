package org.ferrit.dao.cassandra

import com.datastax.driver.core.{BatchStatement, BoundStatement, PreparedStatement, Row, Session}
import org.ferrit.core.model.CrawlJob
import org.ferrit.core.util.Media
import org.ferrit.dao.CrawlJobDAO
import org.ferrit.dao.cassandra.CassandraDAO._
import org.joda.time.DateTime

import scala.collection.JavaConverters._

class CassandraCrawlJobDAO(ttl: CassandraColumnTTL)(implicit session: Session) extends CrawlJobDAO {

  import org.ferrit.dao.cassandra.CassandraTables.{CrawlJobByCrawler, CrawlJobByDate}

  val stmtInsertByCrawler: PreparedStatement = session.prepare(
    insertTemplate(ttl.get(CrawlJobByCrawler)).format(CrawlJobByCrawler)
  )

  val stmtInsertByDate: PreparedStatement = session.prepare(
    insertTemplate(ttl.get(CrawlJobByDate)).format(CrawlJobByDate)
  )

  val stmtFindByCrawlerJob: PreparedStatement = session.prepare(
    s"SELECT * FROM $CrawlJobByCrawler WHERE crawler_id = ? AND job_id = ?"
  )

  val stmtFindByCrawler: PreparedStatement = session.prepare(
    s"SELECT * FROM $CrawlJobByCrawler WHERE crawler_id = ?"
  )

  val stmtFindByDate: PreparedStatement = session.prepare(
    s"SELECT * FROM $CrawlJobByDate WHERE partition_date = ?"
  )

  def insertTemplate(timeToLive: Int) =
    "INSERT INTO %s (" +
        "  crawler_id, crawler_name, job_id, node, " +
        "  partition_date, snapshot_date, created_date, finished_date, " +
        "  duration, outcome, message, " +
        "  uris_seen, uris_queued, fetch_counters, response_counters, media_counters " +
        ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) USING TTL " + timeToLive

  override def insertByCrawler(jobs: Seq[CrawlJob]): Unit = {
    val batch = new BatchStatement()
    jobs.foreach(job => batch.add(bindFromEntity(stmtInsertByCrawler.bind(), job)))
    session.execute(batch)
  }

  override def insertByDate(jobs: Seq[CrawlJob]): Unit = {
    val batch = new BatchStatement()
    jobs.foreach(job => batch.add(bindFromEntity(stmtInsertByDate.bind(), job)))
    session.execute(batch)
  }

  private def bindFromEntity(bs: BoundStatement, c: CrawlJob) = {
    bs.bind()
        .setString("crawler_id", c.crawlerId)
        .setString("crawler_name", c.crawlerName)
        .setString("job_id", c.jobId)
        .setString("node", c.node)
        .setDate("partition_date", c.partitionDate)
        .setDate("snapshot_date", c.snapshotDate)
        .setDate("created_date", c.createdDate)
        .setDate("finished_date", c.finishedDate)
        .setLong("duration", c.duration)
        .setString("outcome", c.outcome)
        .setString("message", c.message)
        .setInt("uris_seen", c.urisSeen)
        .setInt("uris_queued", c.urisQueued)
        .setMap("fetch_counters", c.fetchCounters.asJava)
        .setMap("response_counters", c.responseCounters.asJava)
        .setMap("media_counters", c.mediaCounters.map(p => p._1 -> s"${p._2.count},${p._2.totalBytes}").asJava
        )
  }

  override def find(crawlerId: String, jobId: String): Option[CrawlJob] =
    mapOne {
      session.execute(stmtFindByCrawlerJob.bind()
          .setString("crawler_id", crawlerId)
          .setString("job_id", jobId))
    } { rowToEntity }

  override def find(crawlerId: String): Seq[CrawlJob] = {
    val jobs = mapAll {
      session.execute(stmtFindByCrawler.bind().setString("crawler_id", crawlerId))
    } { rowToEntity }

    jobs.sortWith((j1, j2) => {
      j1.createdDate.after(j2.createdDate)
    })
  }

  override def find(partitionDate: DateTime): Seq[CrawlJob] =
    mapAll {
      session.execute(stmtFindByDate.bind().setDate("partition_date", partitionDate))
    } { rowToEntity }

  private def rowToEntity(row: Row) = {
    CrawlJob(
      row.getString("crawler_id"),
      row.getString("crawler_name"),
      row.getString("job_id"),
      row.getString("node"),
      row.getDate("partition_date"),
      row.getDate("snapshot_date"),
      row.getDate("created_date"),
      row.getDate("finished_date"),
      row.getLong("duration"),
      row.getString("outcome"),
      row.getString("message"),
      row.getInt("uris_seen"),
      row.getInt("uris_queued"),
      {
        val map = row.getMap("fetch_counters", classOf[String], classOf[java.lang.Integer])
        map.asScala.toMap.map(p => p._1 -> scala.Int.unbox(p._2))
      },
      {
        val map = row.getMap("response_counters", classOf[String], classOf[java.lang.Integer])
        map.asScala.toMap.map(p => p._1 -> scala.Int.unbox(p._2))
      },
      {
        val map = row.getMap("media_counters", classOf[String], classOf[String])
        map.asScala.toMap.map({p =>
          val nums = p._2.split(",").map({s => Integer.parseInt(s)})
          val media = nums match {
            case Array(c,t) => Media(c,t)
            case _ => Media(0,0)
          }
          p._1 -> media
        })
      }
    )
  }
}