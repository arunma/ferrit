package org.ferrit.core.test

import akka.actor.{Actor, ActorRef, Props}


/**
 * An adaptation of the StepParent / FosterParent testing pattern recommended by
 * Ronald Kuhn in the 2013 Principles of Reactive Programming Coursera course
 *
 * Messages sent by the real parent are forwarded to the child.
 * Messages sent by the child are forwarded to the real parent.
 * Neither the parent or child should be aware that there is a proxy in between them.
 */
class ProxyActor(realParent: ActorRef, childProps: Props, childName: String) extends Actor {

  val child = context.actorOf(childProps, childName)

  def receive = {

    case msg if sender == child => realParent forward msg

    case msg if sender == realParent => child forward msg

  }

}
