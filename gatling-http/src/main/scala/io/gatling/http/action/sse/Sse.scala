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

package io.gatling.http.action.sse

import io.gatling.core.session._
import io.gatling.http.check.sse.SseMessageCheck
import io.gatling.http.request.builder.sse.SseConnectRequestBuilder

object Sse {
  private val DefaultSseName = SessionPrivateAttributes.PrivateAttributePrefix + "http.sse"

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def apply(requestName: Expression[String], sseName: Expression[String] = DefaultSseName.expressionSuccess): Sse = new Sse(requestName, sseName)

  def checkMessage(name: String): SseMessageCheck = SseMessageCheck(name, Nil, Nil)
}

class Sse(requestName: Expression[String], sseName: Expression[String]) {

  def sseName(sseName: Expression[String]): Sse = new Sse(requestName, sseName)

  def connect(url: Expression[String]): SseConnectRequestBuilder = SseConnectRequestBuilder(requestName, url, sseName)

  def setCheck: SseSetCheckBuilder = SseSetCheckBuilder(requestName, sseName, Nil)

  def close: SseCloseBuilder = SseCloseBuilder(requestName, sseName)
}
