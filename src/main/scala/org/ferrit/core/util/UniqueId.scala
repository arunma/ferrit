package org.ferrit.core.util

object UniqueId {
  def next = java.util.UUID.randomUUID().toString
}