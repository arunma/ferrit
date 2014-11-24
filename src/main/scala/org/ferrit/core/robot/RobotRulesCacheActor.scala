package org.ferrit.core.robot

import akka.actor.Actor
import akka.pattern.pipe
import org.ferrit.core.uri.UriReader

/**
 * A RobotRulesCache is not threadsafe (well at least the default implementation isn't).
 * By wrapping it with an enclosing Actor access to it by multiple crawlers 
 * becomes civilised.
 */
class RobotRulesCacheActor(cache: RobotRulesCache) extends Actor {

  import RobotRulesCacheActor._
  implicit val execContext = context.system.dispatcher

  override def receive = {
    case Allow(ua, reader) => cache.allow(ua, reader).pipeTo(sender)
    case DelayFor(ua, reader) => cache.getDelayFor(ua, reader).pipeTo(sender)
  }

}

object RobotRulesCacheActor {
  case class Allow(userAgent: String, reader: UriReader)
  case class DelayFor(userAgent: String, reader: UriReader)
}