package org.ferrit.core.model

case class Document(
  crawlerId: String,
  jobId: String,
  uri: String,
  contentType: String,
  content: Array[Byte]
)