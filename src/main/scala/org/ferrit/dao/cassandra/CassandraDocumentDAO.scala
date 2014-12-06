package org.ferrit.dao.cassandra

import com.datastax.driver.core.{ PreparedStatement, Row, Session }
import org.ferrit.core.model.Document
import org.ferrit.dao.DocumentDAO
import org.ferrit.dao.cassandra.CassandraDAO._

import java.nio.ByteBuffer

class CassandraDocumentDAO(ttl: CassandraColumnTTL)(implicit session: Session)
    extends DocumentDAO {
  val stmtInsert: PreparedStatement = session.prepare(
    "INSERT INTO document (" +
      "  crawler_id, job_id, uri, content_type, content " +
      ") VALUES (?,?,?,?,?) USING TTL " + ttl.get(CassandraTables.Document))

  val stmtFind: PreparedStatement = session.prepare(
    "SELECT * FROM document WHERE job_id = ? AND uri = ?")

  def insert(doc: Document): Unit = {
    session.execute(
      stmtInsert.bind()
        .setString("crawler_id", doc.crawlerId)
        .setString("job_id", doc.jobId)
        .setString("uri", doc.uri)
        .setString("content_type", doc.contentType)
        .setBytes("content", ByteBuffer.wrap(doc.content)))
  }

  def find(jobId: String, uri: String): Option[Document] =
    mapOne {
      session.execute(
        stmtFind.bind()
          .setString("job_id", jobId)
          .setString("uri", uri))
    } { rowToEntity }

  private def rowToEntity(row: Row) = Document(
    row.getString("crawler_id"),
    row.getString("job_id"),
    row.getString("uri"),
    row.getString("content_type"),
    getBytes(row, "content") //data
    )

  /** Extracts bytes from a column.
    * It is not possible to simply call row.getBytes("content").array()
    * because this tends to return the whole backing array which likely
    * contains data from other columns.
    *
    * @see http://grokbase.com/t/cassandra/user/134brvqzd3/blobs-in-cql
    * @see http://stackoverflow.com/questions/17282361/serializing-java-objects-to-cassandra-1-2-via-bytebuffer-cql-3
    */
  private def getBytes(row: Row, colName: String): Array[Byte] = {
    val content = row.getBytes(colName)
    val data: Array[Byte] = new Array[Byte](content.remaining())
    // pass the data to have it populated by the content ByteBuffer
    content.get(data)
    // then return data array
    data
  }
}