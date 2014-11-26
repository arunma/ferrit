package org.ferrit.dao.cassandra

/**
 * A map of time to live values for Cassandra row columns.
 */
case class CassandraColumnTTL(map: Map[String, Int]) {
  def get(name: String): Int = map.get(name) match {
    case Some(ttl) => ttl
    case None => throw new IllegalArgumentException(s"No TTL for [$name]")
  }
}
