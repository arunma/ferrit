package org.ferrit.core.http

import org.ferrit.core.uri.CrawlUri

sealed abstract class Request(
  val method: String,
  val userAgent: String,
  val crawlUri: CrawlUri,
  val headers: Map[String, String])

case class Get(

  _userAgent: String,
  _crawlUri: CrawlUri,
  _headers: Map[String, String] = Map.empty) extends Request(

  "GET",
  _userAgent,
  _crawlUri,
  _headers)
