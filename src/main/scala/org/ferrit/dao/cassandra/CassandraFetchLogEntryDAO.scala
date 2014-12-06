package org.ferrit.dao.cassandra

import com.datastax.driver.core.{ BatchStatement, PreparedStatement, Row, Session }
import org.ferrit.core.model.FetchLogEntry
import org.ferrit.dao.FetchLogEntryDAO
import org.ferrit.dao.cassandra.CassandraDAO._

class CassandraFetchLogEntryDAO(ttl: CassandraColumnTTL)(implicit session: Session) extends FetchLogEntryDAO {

  import org.ferrit.dao.cassandra.CassandraTables.{ FetchLog => FetchLogTable }

  val stmtInsert: PreparedStatement = session.prepare(
    s"INSERT INTO $FetchLogTable (" +
      "  crawler_id, job_id, log_time, uri, uri_depth, status_code, " +
      "  content_type, content_length, links_extracted, " +
      "  fetch_duration, request_duration, parse_duration, " +
      "  uris_seen, uris_queued, fetches " +
      ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) USING TTL " + ttl.get(FetchLogTable))

  val stmtFindByPk = session.prepare(
    s"SELECT * FROM $FetchLogTable WHERE job_id = ?")

  val stmtFindFirst = session.prepare(
    s"SELECT * FROM $FetchLogTable WHERE job_id = ? LIMIT 1")

  def insert(fle: FetchLogEntry): Unit = insert(Seq(fle))

  def insert(entries: Seq[FetchLogEntry]): Unit = {
    val batch = new BatchStatement
    entries.foreach(fle => batch.add(
      stmtInsert.bind()
        .setString("crawler_id", fle.crawlerId)
        .setString("job_id", fle.jobId)
        .setDate("log_time", fle.logTime)
        .setString("uri", fle.uri)
        .setInt("uri_depth", fle.uriDepth)
        .setInt("status_code", fle.statusCode)
        .setString("content_type", fle.contentType)
        .setInt("content_length", fle.contentLength)
        .setInt("links_extracted", fle.linksExtracted)
        .setInt("fetch_duration", fle.fetchDuration)
        .setInt("request_duration", fle.requestDuration)
        .setInt("parse_duration", fle.parseDuration)
        .setInt("uris_seen", fle.urisSeen)
        .setInt("uris_queued", fle.urisQueued)
        .setInt("fetches", fle.fetches)))
    session.execute(batch)
  }

  def find(jobId: String): Seq[FetchLogEntry] =
    mapAll {
      session.execute(stmtFindByPk.bind().setString("job_id", jobId))
    } {
      rowToEntity
    }

  private def rowToEntity(row: Row) = FetchLogEntry(
    row.getString("crawler_id"),
    row.getString("job_id"),
    row.getDate("log_time"),
    row.getString("uri"),
    row.getInt("uri_depth"),
    row.getInt("status_code"),
    row.getString("content_type"),
    row.getInt("content_length"),
    row.getInt("links_extracted"),
    row.getInt("fetch_duration"),
    row.getInt("request_duration"),
    row.getInt("parse_duration"),
    row.getInt("uris_seen"),
    row.getInt("uris_queued"),
    row.getInt("fetches"))
}
