package org.ferrit.core.model

import org.joda.time.DateTime

case class DocumentMetaData(
  crawlerId: String,
  jobId: String,
  uri: String,
  contentType: String,
  contentLength: Int,
  depth: Int,
  fetched: DateTime,
  responseStatus: String)