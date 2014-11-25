package org.ferrit.core.http

import com.ning.http.client.AsyncHandler._
import com.ning.http.client.{AsyncHandler, AsyncHttpClient, Response => NingResponse, _}
import org.ferrit.core.http.{Response => OurResponse}

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}

import java.io.ByteArrayOutputStream
import java.util.concurrent.{Future => JFuture}
import java.util.{List => JList, Map => JMap, Set => JSet}

/**
 * HttpClient implementation that uses the Ning AsyncHttpClient.
 */
class NingAsyncHttpClient(config: HttpClientConfig) extends HttpClient {

  private val client = {
    val conf = new AsyncHttpClientConfig.Builder()
      .setAllowPoolingConnection(config.useConnectionPooling)
      .setRequestTimeoutInMs(config.requestTimeout)
      .setFollowRedirects(config.followRedirects)
      .setCompressionEnabled(config.useCompression)
      .build()
    new AsyncHttpClient(conf)
  }

  def this() {
    this(new HttpClientConfig)
  }

  override def shutdown(): Unit = client.close()

  override def request(request: Request): Future[OurResponse] = {
    val allHeaders = request.headers ++ Map[String, String](
      "User-Agent" -> request.userAgent,
      "Pragma" -> "no-cache",
      "Cache-Control" -> "no-cache",
      "Connection" -> "keep-alive",
      "Keep-Alive" -> s"${config.keepAlive}"
    )

    val reqBuilder = client.prepareGet(request.crawlUri.crawlableUri)
    allHeaders.foreach(pair => reqBuilder.addHeader(pair._1, pair._2))

    val promise = Promise[OurResponse]()
    reqBuilder.execute(new Handler(request, promise))
    promise.future

  }

  /**
   * Implements AsyncDirectly but we don't get the Ning Response in onCompleted()
   */
  class Handler(request: Request, promise: Promise[OurResponse]) extends AsyncHandler[OurResponse] {
    var bytes = new ByteArrayOutputStream
    var headers: Map[String, List[String]] = Map.empty
    var statusCode: Int = -1
    var length = 0

    var timeStatus: Long = _
    var timeHeaders: Long = _
    var timeCompleted: Long = _
    var start = now

    def onStatusReceived(status: HttpResponseStatus): STATE = {
      statusCode = status.getStatusCode()
      timeStatus = now() - start
      STATE.CONTINUE // what if status code > = 500?
    }

    def onHeadersReceived(h: HttpResponseHeaders): STATE = {
      headers ++= h.getHeaders.entrySet.asScala
          .map(e => e.getKey -> e.getValue.asScala.toList)
      timeHeaders = now() - start
      STATE.CONTINUE
    }

    def onBodyPartReceived(bodyPart: HttpResponseBodyPart): STATE = {
      val b: Array[Byte] = bodyPart.getBodyPartBytes()
      length += b.length
      bytes.write(b)
      if (maxSizeExceeded) STATE.ABORT else STATE.CONTINUE
    }

    def onCompleted(): OurResponse = {
      if (maxSizeExceeded)
        throw new Exception(s"Max content size exceeded: ${config.maxContentSize}")
      else {
        timeCompleted = now() - start
        val response =
          DefaultResponse(
            statusCode,
            headers,
            bytes.toByteArray(),
            Stats(timeStatus, timeHeaders, timeCompleted),
            request
          )
        promise.success(response)
        response // only needed for the Ning AsyncHandler, not the Promise
      }
    }

    def now(): Long = System.currentTimeMillis

    def maxSizeExceeded = length > config.maxContentSize

    def onThrowable(t: Throwable): Unit = promise.failure(t)

  }

}