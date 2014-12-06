package org.ferrit.server

import akka.actor._
import com.typesafe.config._
import org.ferrit.core.crawler.{ CrawlLog, SpiderManager }
import org.ferrit.core.http.{ HttpClient, NingAsyncHttpClient }
import org.ferrit.core.robot.{ DefaultRobotRulesCache, RobotRulesCacheActor }
import org.ferrit.dao.cassandra.CassandraDAOFactory
import org.ferrit.dao.{ DAOFactory, Journal }

trait SpiderContext {
  implicit val system: ActorSystem

  def config: Config

  def spiderManager: ActorRef

  def robotsRuleCache: ActorRef

  def daoFactory: DAOFactory

  def journal: ActorRef

  def logger: ActorRef

  def spiderClient: HttpClient //TODO: Change to ActorRef
}

class ProdSpiderContext(implicit val system: ActorSystem) extends SpiderContext {
  override lazy val spiderManager = system.actorOf(SpiderManager.props(config, spiderClient, robotsRuleCache))
  override lazy val robotsRuleCache = system.actorOf(Props(classOf[RobotRulesCacheActor],
    new DefaultRobotRulesCache(spiderClient)(system.dispatcher)))
  override lazy val daoFactory = new CassandraDAOFactory(config)
  override lazy val logger = system.actorOf(CrawlLog.props())
  override lazy val journal = system.actorOf(Props(classOf[Journal], daoFactory))
  override lazy val spiderClient = new NingAsyncHttpClient

  override def config = ConfigFactory.load()
}

