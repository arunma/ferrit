package org.ferrit.core.http

import scala.concurrent.Future

/** An HttpClient is a wrapper for an underlying HTTP client library
  * implementation. This allows for future plug and play experiments,
  * for example switching between Ning client and Spray client.
  */
trait HttpClient {

  /** Issue an HTTP request and returns a future Response.
    */
  def request(request: Request): Future[Response]

  /** The underlying client may need to shutdown resources, e.g. Ning client.
    */
  def shutdown(): Unit

}
