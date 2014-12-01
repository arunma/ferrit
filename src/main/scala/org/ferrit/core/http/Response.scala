package org.ferrit.core.http


trait Response {

  // Move or replace these compile time constants
  final val DefaultEncoding: String = "UTF-8"
  final val ContentTypeHeader: String = "Content-Type"

  def statusCode: Int
  
  def headers: Map[String, Seq[String]]
  def firstHeader(name: String):Option[String] = {
    headers.get(name) match {
      case Some(head :: tail) => Some(head)
      case _ => None
    }
  }

  def content: Array[Byte]
  def contentLength: Int = content.length
  def contentString: String = new String(content, DefaultEncoding)
  def contentExists: Boolean = content.nonEmpty
  def contentType: Option[String] = firstHeader(ContentTypeHeader)
    
  def stats: Stats
  def request: Request // originating request

}

case class DefaultResponse(
  _statusCode: Int,
  _headers: Map[String, Seq[String]],
  _content: Array[Byte],
  _stats: Stats,
  _request: Request) extends Response
{
  def statusCode: Int = _statusCode
  def headers: Map[String, Seq[String]] = _headers
  def content: Array[Byte] = _content
  def stats: Stats = _stats
  def request: Request = _request
}

case class Stats(
  timeStatus: Long, // elapsed time in millis
  timeHeaders: Long,
  timeCompleted: Long) {
  override def toString:String =
    "Stats(" +
      s"timeStatus: ${timeStatus}ms, " + 
      s"timeHeaders: ${timeHeaders}ms, " + 
      s"timeCompleted: ${timeCompleted}ms" + 
    ")"
}

object Stats {
  def empty:Stats = Stats(0,0,0)
}