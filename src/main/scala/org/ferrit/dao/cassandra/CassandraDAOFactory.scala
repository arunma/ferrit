package org.ferrit.dao.cassandra

import com.datastax.driver.core.Session
import com.typesafe.config.Config
import org.ferrit.dao.DAOFactory

class CassandraDAOFactory(ttl: CassandraColumnTTL, session: Session) extends DAOFactory {
  private[dao] implicit val _session = session

  override val crawlerDao = new CassandraCrawlerDAO(ttl)

  // Cassandra prepared statements should only be created once.
  // The driver complains if they are created multiple times
  // which happens if there are multiple DAOs created, yet a Session
  // is required to create prepared statements which first requires
  // a DAO instance.
  override val crawlJobDao = new CassandraCrawlJobDAO(ttl)

  override val fetchLogEntryDao = new CassandraFetchLogEntryDAO(ttl)

  override val documentMetaDataDao = new CassandraDocumentMetaDataDAO(ttl)

  override val documentDao = new CassandraDocumentDAO(ttl)

  def this(cc: CassandraPersistenceManager, config: Config) {
    this(cc.getColumnTTL(config), cc.session)
  }

  def this(config: Config) {
    this(new CassandraPersistenceManager(config), config)
  }
}
