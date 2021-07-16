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

package io.gatling.http.request.builder.polling

import scala.concurrent.duration.FiniteDuration

import io.gatling.core.session._
import io.gatling.http.action.HttpActionBuilder
import io.gatling.http.action.polling.{ PollingStartBuilder, PollingStopBuilder }
import io.gatling.http.request.builder.HttpRequestBuilder

object Polling {
  private val DefaultPollerName = SessionPrivateAttributes.PrivateAttributePrefix + "http.polling"

  val Default: Polling = new Polling(DefaultPollerName)
}

final class Polling(pollerName: String) {

  def pollerName(pollerName: String): Polling = new Polling(pollerName)

  def every(period: FiniteDuration): PollingEveryStep = new PollingEveryStep(pollerName, period)

  def stop: HttpActionBuilder = new PollingStopBuilder(pollerName)
}

final class PollingEveryStep(pollerName: String, period: FiniteDuration) {

  def exec(requestBuilder: HttpRequestBuilder): HttpActionBuilder =
    new PollingStartBuilder(pollerName, period, requestBuilder)
}
