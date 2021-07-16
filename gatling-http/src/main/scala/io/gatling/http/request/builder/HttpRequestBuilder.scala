/*
 * Copyright 2011-2021 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.http.request.builder

import java.security.MessageDigest

import scala.concurrent.duration.FiniteDuration

import io.gatling.core.body.{ Body, RawFileBodies }
import io.gatling.core.check.ChecksumCheck
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session._
import io.gatling.http.ResponseTransformer
import io.gatling.http.action.HttpRequestActionBuilder
import io.gatling.http.cache.HttpCaches
import io.gatling.http.check.HttpCheck
import io.gatling.http.check.HttpCheckScope._
import io.gatling.http.engine.response.IsHttpDebugEnabled
import io.gatling.http.protocol.HttpProtocol
import io.gatling.http.request._

import com.softwaremill.quicklens._
import io.netty.handler.codec.http.{ HttpHeaderNames, HttpHeaderValues }

object HttpAttributes {
  val Empty: HttpAttributes =
    new HttpAttributes(
      checks = Nil,
      ignoreProtocolChecks = false,
      silent = None,
      followRedirect = true,
      responseTransformer = None,
      explicitResources = Nil,
      body = None,
      bodyParts = Nil,
      formParams = Nil,
      form = None,
      requestTimeout = None
    )
}

final case class HttpAttributes(
    checks: List[HttpCheck],
    ignoreProtocolChecks: Boolean,
    silent: Option[Boolean],
    followRedirect: Boolean,
    responseTransformer: Option[ResponseTransformer],
    explicitResources: List[HttpRequestBuilder],
    body: Option[Body],
    bodyParts: List[BodyPart],
    formParams: List[HttpParam],
    form: Option[Expression[Map[String, Any]]],
    requestTimeout: Option[FiniteDuration]
)

object HttpRequestBuilder {

  implicit def toActionBuilder(requestBuilder: HttpRequestBuilder): HttpRequestActionBuilder =
    new HttpRequestActionBuilder(requestBuilder)

  private val MultipartFormDataValueExpression = HttpHeaderValues.MULTIPART_FORM_DATA.toString.expressionSuccess
  private val ApplicationFormUrlEncodedValueExpression = HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString.expressionSuccess
}

/**
 * This class serves as model for all HttpRequestBuilders
 *
 * @param httpAttributes the base HTTP attributes
 */
final case class HttpRequestBuilder(commonAttributes: CommonAttributes, httpAttributes: HttpAttributes) extends RequestBuilder[HttpRequestBuilder] {

  private[http] def newInstance(commonAttributes: CommonAttributes): HttpRequestBuilder = new HttpRequestBuilder(commonAttributes, httpAttributes)

  def check(checks: HttpCheck*): HttpRequestBuilder = this.modify(_.httpAttributes.checks).using(_ ::: checks.toList)

  @deprecated("Please use ignoreProtocolChecks instead. Will be removed in 3.5.0", "3.4.0")
  def ignoreDefaultChecks: HttpRequestBuilder = ignoreProtocolChecks

  def ignoreProtocolChecks: HttpRequestBuilder = this.modify(_.httpAttributes.ignoreProtocolChecks).setTo(true)

  def silent: HttpRequestBuilder = this.modify(_.httpAttributes.silent).setTo(Some(true))

  def notSilent: HttpRequestBuilder = this.modify(_.httpAttributes.silent).setTo(Some(false))

  def disableFollowRedirect: HttpRequestBuilder = this.modify(_.httpAttributes.followRedirect).setTo(false)

  /**
   * @param responseTransformer transforms the response before it's handled to the checks pipeline
   */
  def transformResponse(responseTransformer: ResponseTransformer): HttpRequestBuilder =
    this.modify(_.httpAttributes.responseTransformer).setTo(Some(responseTransformer))

  def body(bd: Body): HttpRequestBuilder = this.modify(_.httpAttributes.body).setTo(Some(bd))

  def processRequestBody(processor: Body => Body): HttpRequestBuilder = this.modify(_.httpAttributes.body).using(_.map(processor))

  def bodyPart(bodyPart: BodyPart): HttpRequestBuilder = this.modify(_.httpAttributes.bodyParts).using(_ ::: List(bodyPart))

  def resources(res: HttpRequestBuilder*): HttpRequestBuilder = this.modify(_.httpAttributes.explicitResources).setTo(res.toList)

  /**
   * Adds Content-Type header to the request set with "multipart/form-data" value
   */
  def asMultipartForm: HttpRequestBuilder = header(HttpHeaderNames.CONTENT_TYPE, HttpRequestBuilder.MultipartFormDataValueExpression)
  def asFormUrlEncoded: HttpRequestBuilder = header(HttpHeaderNames.CONTENT_TYPE, HttpRequestBuilder.ApplicationFormUrlEncodedValueExpression)

  def formParam(key: Expression[String], value: Expression[Any]): HttpRequestBuilder = formParam(SimpleParam(key, value))
  def multivaluedFormParam(key: Expression[String], values: Expression[Seq[Any]]): HttpRequestBuilder = formParam(MultivaluedParam(key, values))

  def formParamSeq(seq: Seq[(String, Any)]): HttpRequestBuilder = formParamSeq(seq2SeqExpression(seq))
  def formParamSeq(seq: Expression[Seq[(String, Any)]]): HttpRequestBuilder = formParam(ParamSeq(seq))

  def formParamMap(map: Map[String, Any]): HttpRequestBuilder = formParamSeq(map2SeqExpression(map))
  def formParamMap(map: Expression[Map[String, Any]]): HttpRequestBuilder = formParam(ParamMap(map))

  private def formParam(formParam: HttpParam): HttpRequestBuilder =
    this.modify(_.httpAttributes.formParams).using(_ ::: List(formParam))

  def form(form: Expression[Map[String, Any]]): HttpRequestBuilder =
    this.modify(_.httpAttributes.form).setTo(Some(form))

  def formUpload(name: Expression[String], filePath: Expression[String])(implicit rawFileBodies: RawFileBodies): HttpRequestBuilder =
    bodyPart(BodyPart.rawFileBodyPart(Some(name), filePath, rawFileBodies))

  def requestTimeout(timeout: FiniteDuration): HttpRequestBuilder =
    this.modify(_.httpAttributes.requestTimeout).setTo(Some(timeout))

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def build(httpCaches: HttpCaches, httpProtocol: HttpProtocol, throttled: Boolean, configuration: GatlingConfiguration): HttpRequestDef = {

    val requestChecks = httpAttributes.checks

    val requestAndProtocolChecks =
      if (httpAttributes.ignoreProtocolChecks) {
        requestChecks
      } else {
        val protocolChecks = httpProtocol.responsePart.checks
        requestChecks ::: protocolChecks
      }

    val checks =
      if (requestAndProtocolChecks.exists(_.scope == Status)) requestAndProtocolChecks
      else requestAndProtocolChecks ::: List(RequestBuilder.DefaultHttpCheck)

    val sortedChecks = checks.zipWithIndex
      .sortBy { case (check, rank) =>
        (check.scope, rank)
      }
      .map { case (check, _) =>
        check
      }

    val resolvedFollowRedirect = httpProtocol.responsePart.followRedirect && httpAttributes.followRedirect

    val resolvedResponseTransformer = httpAttributes.responseTransformer.orElse(httpProtocol.responsePart.responseTransformer)

    val resolvedResources = httpAttributes.explicitResources.map(_.build(httpCaches, httpProtocol, throttled, configuration))

    val resolvedRequestExpression = new HttpRequestExpressionBuilder(commonAttributes, httpAttributes, httpCaches, httpProtocol, configuration).build

    val digests: Map[String, MessageDigest] =
      sortedChecks
        .map(_.wrapped)
        .collect { case check: ChecksumCheck[_] => check.algorithm -> MessageDigest.getInstance(check.algorithm) }
        .toMap

    val storeBodyParts = IsHttpDebugEnabled ||
      // we can't assume anything about if and how the response body will be used,
      // let's force bytes so we don't risk decoding binary content
      resolvedResponseTransformer.isDefined ||
      sortedChecks.exists(_.scope == Body)

    HttpRequestDef(
      commonAttributes.requestName,
      resolvedRequestExpression,
      HttpRequestConfig(
        checks = sortedChecks,
        responseTransformer = resolvedResponseTransformer,
        throttled = throttled,
        silent = httpAttributes.silent,
        followRedirect = resolvedFollowRedirect,
        digests = digests,
        storeBodyParts = storeBodyParts,
        defaultCharset = configuration.core.charset,
        explicitResources = resolvedResources,
        httpProtocol = httpProtocol
      )
    )
  }
}
