package org.ferrit.core.util

object HttpUtil {

  val ContentTypeHeader = "Content-Type"
  val TextHtmlUtf8 = "text/html; charset=UTF-8"
  val TextCssUtf8 = "text/css; charset=UTF-8"
  val TextUtf8 = "text; charset=UTF-8"

}

object Headers {

  // "Content-Type: text/html; charset=UTF-8"  
  val ContentTypeTextHtmlUtf8 = HttpUtil.ContentTypeHeader -> Seq(HttpUtil.TextHtmlUtf8)

  val ContentTypeTextCssUtf8 = HttpUtil.ContentTypeHeader -> Seq(HttpUtil.TextCssUtf8)

  val ContentTypeTextUtf8 = HttpUtil.ContentTypeHeader -> Seq(HttpUtil.TextUtf8)

}