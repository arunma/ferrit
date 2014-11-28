package org.ferrit.dao.cassandra

import com.datastax.driver.core.{Row, Session}
import org.ferrit.core.model.DocumentMetaData
import org.ferrit.dao.DocumentMetaDataDAO
import org.ferrit.dao.cassandra.CassandraDAO._

class CassandraDocumentMetaDataDAO(ttl: CassandraColumnTTL)(implicit session: Session)
    extends DocumentMetaDataDAO {
  val stmtInsert = session.prepare(
    "INSERT INTO document_metadata (" + 
    "  crawler_id, job_id, uri, content_type, content_length, depth, fetched, response_status " + 
    ") VALUES (?,?,?,?,?,?,?,?) USING TTL " + ttl.get(CassandraTables.DocumentMetaData)
  )
  
  val stmtFindByJobAndUri = session.prepare(
    "SELECT * FROM document_metadata WHERE job_id = ? AND uri = ?"
  )

  val stmtFindByJob = session.prepare(
    "SELECT * FROM document_metadata WHERE job_id = ?"
  )

  def insert(docMeta: DocumentMetaData): Unit = {
    session.execute(
      stmtInsert.bind()
        .setString("crawler_id", docMeta.crawlerId)
        .setString("job_id", docMeta.jobId)
        .setString("uri", docMeta.uri)
        .setString("content_type", docMeta.contentType)
        .setInt("content_length", docMeta.contentLength)
        .setInt("depth", docMeta.depth)
        .setDate("fetched", docMeta.fetched)
        .setString("response_status", docMeta.responseStatus)
    )
  }

  def find(jobId: String, uri: String): Option[DocumentMetaData] =
    mapOne {
      session.execute(stmtFindByJobAndUri.bind()
          .setString("job_id", jobId)
          .setString("uri", uri))
    } {rowToEntity}

  def find(jobId: String): Seq[DocumentMetaData] = {
    val docs = mapAll {
      session.execute(stmtFindByJob.bind().setString("job_id", jobId))
    } { rowToEntity }

    docs.sortWith((d1, d2) => {
      if (d1.depth != d2.depth) d1.depth < d2.depth
      else d1.fetched.before(d2.fetched) //d1.uri < d2.uri
    })
  }

  private def rowToEntity(row: Row) = DocumentMetaData(
    row.getString("crawler_id"),
    row.getString("job_id"),
    row.getString("uri"),
    row.getString("content_type"),
    row.getInt("content_length"),
    row.getInt("depth"),
    row.getDate("fetched"),
    row.getString("response_status")
  )
}
