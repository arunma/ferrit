package org.ferrit.dao.cassandra

import com.datastax.driver.core.{ResultSet, Row}
import org.joda.time.DateTime

import java.util.Date

object CassandraDAO {
  import scala.language.implicitConversions
  
  implicit def toDate(jodaDate: DateTime): Date =
    if (jodaDate != null) new Date(jodaDate.getMillis) else null

  implicit def toDateOption(jodaDate: Option[DateTime]): Date =
    if (jodaDate.nonEmpty) new Date (jodaDate.get.getMillis) else null

  implicit def toDateTime(date: Date): DateTime =
    if (date != null) new DateTime(date.getTime)  else null

  implicit def toDateTimeOption(date: Date): Option[DateTime] =
    if (date != null) Some(new DateTime(date.getTime)) else None

  implicit def fromStringOption(stringOpt: Option[String]): String =
    if (stringOpt.nonEmpty)  stringOpt.get else null

  implicit def toStringOption(string: String): Option[String] =
    if (string != null) Some(string)  else None

  def mapOne[T](rs: ResultSet)(fn: Row => T): Option[T] = {
    val iter = rs.iterator()
    if (iter.hasNext) Some(fn(iter.next()))
    else None
  }

  def mapAll[T](rs: ResultSet)(fn: Row => T): Seq[T] = {
    val iter = rs.iterator()
    val items = new scala.collection.mutable.ListBuffer[T]
    while (iter.hasNext) {
      items.append(fn(iter.next()))
    }

    items.toList
  }
}
