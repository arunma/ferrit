package org.ferrit.dao.cassandra

import com.datastax.driver.core.{BoundStatement, PreparedStatement, Row, Session}
import org.ferrit.core.model.DocumentMetaData
import org.ferrit.dao.DocumentMetaDataDAO
import org.ferrit.dao.cassandra.CassandraDAO._


class CassandraDocumentMetaDataDAO(ttl: CassandraColumnTTL)(implicit session: Session) extends DocumentMetaDataDAO {
  val timeToLive = ttl.get(CassandraTables.DocumentMetaData)

  val stmtInsert: PreparedStatement = session.prepare(
    "INSERT INTO document_metadata (" + 
    "  crawler_id, job_id, uri, content_type, content_length, depth, fetched, response_status " + 
    ") VALUES (?,?,?,?,?,?,?,?) USING TTL " + timeToLive
  )
  
  val stmtFindByJobAndUri: PreparedStatement = session.prepare(
    "SELECT * FROM document_metadata WHERE job_id = ? AND uri = ?"
  )

  val stmtFindByJob: PreparedStatement = session.prepare(
    "SELECT * FROM document_metadata WHERE job_id = ?"
  )

  def insert(docMeta: DocumentMetaData):Unit = {
    session.execute(bindFromEntity(stmtInsert.bind(), docMeta))
  }

  private[dao] def bindFromEntity(bs: BoundStatement, a: DocumentMetaData): BoundStatement = bs.bind()
      .setString("crawler_id", a.crawlerId)
      .setString("job_id", a.jobId)
      .setString("uri", a.uri)
      .setString("content_type", a.contentType)
      .setInt("content_length", a.contentLength)
      .setInt("depth", a.depth)
      .setDate("fetched", a.fetched)
      .setString("response_status", a.responseStatus)

  def find(jobId: String, uri: String): Option[DocumentMetaData] = {
    val rs = session.execute(stmtFindByJobAndUri.bind()
        .setString("job_id", jobId)
        .setString("uri", uri))
    mapOne(rs) {row => rowToEntity(row)}
  }

  private[dao] def rowToEntity(row: Row): DocumentMetaData = {
    DocumentMetaData(
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

  def find(jobId: String): Seq[DocumentMetaData] = {
    val docs = mapAll(session.execute(stmtFindByJob.bind().setString("job_id", jobId))) {
      row => rowToEntity(row)
    }

    docs.sortWith((d1, d2) => {
      if (d1.depth != d2.depth) d1.depth < d2.depth
      else d1.fetched.before(d2.fetched) //d1.uri < d2.uri
    })
  }
}
