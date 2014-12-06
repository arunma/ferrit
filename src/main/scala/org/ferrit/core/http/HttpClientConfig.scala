package org.ferrit.core.http

/** A generic configuration class for an HttpClient client.
  */
sealed case class HttpClientConfig(
  requestTimeout: Int = 30000,
  maxContentSize: Int = 1024 * 1024 * 5, // sensible limit in megabytes
  followRedirects: Boolean = true,
  useConnectionPooling: Boolean = true,
  useCompression: Boolean = false,
  keepAlive: Int = 300)