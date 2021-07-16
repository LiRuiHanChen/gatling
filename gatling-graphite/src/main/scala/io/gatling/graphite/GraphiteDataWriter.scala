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

package io.gatling.graphite

import scala.collection.mutable

import io.gatling.commons.util.Clock
import io.gatling.commons.util.Collections._
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.stats.message.ResponseTimings
import io.gatling.core.stats.writer._
import io.gatling.core.util.NameGen
import io.gatling.graphite.message.GraphiteMetrics
import io.gatling.graphite.sender.MetricsSender
import io.gatling.graphite.types._

import akka.actor.ActorRef

final case class GraphiteData(
    metricsSender: ActorRef,
    requestsByPath: mutable.Map[GraphitePath, RequestMetricsBuffer],
    usersByScenario: mutable.Map[GraphitePath, UserBreakdownBuffer],
    format: GraphitePathPattern
) extends DataWriterData

private[gatling] class GraphiteDataWriter(clock: Clock, configuration: GatlingConfiguration) extends DataWriter[GraphiteData] with NameGen {

  def newResponseMetricsBuffer: RequestMetricsBuffer =
    new HistogramRequestMetricsBuffer(configuration)

  private val flushTimerName = "flushTimer"

  def onInit(init: Init): GraphiteData = {
    import init._

    val metricsSender: ActorRef = context.actorOf(MetricsSender.props(clock, configuration), genName("metricsSender"))
    val requestsByPath = mutable.Map.empty[GraphitePath, RequestMetricsBuffer]
    val usersByScenario = mutable.Map.empty[GraphitePath, UserBreakdownBuffer]

    val pattern: GraphitePathPattern = new OldGraphitePathPattern(runMessage, configuration)

    usersByScenario.update(pattern.allUsersPath, new UserBreakdownBuffer(scenarios.sumBy(_.totalUserCount.getOrElse(0L))))
    scenarios.foreach(scenario => usersByScenario += (pattern.usersPath(scenario.name) -> new UserBreakdownBuffer(scenario.totalUserCount.getOrElse(0L))))

    startTimerAtFixedRate(flushTimerName, Flush, configuration.data.graphite.writePeriod)

    GraphiteData(metricsSender, requestsByPath, usersByScenario, pattern)
  }

  def onFlush(data: GraphiteData): Unit = {
    import data._
    val requestsMetrics = requestsByPath.view.mapValues(_.metricsByStatus).to(Map)
    val usersBreakdowns = usersByScenario.view.mapValues(_.breakDown).to(Map)

    // Reset all metrics
    requestsByPath.foreach { case (_, buff) => buff.clear() }

    sendMetricsToGraphite(data, clock.nowSeconds, requestsMetrics, usersBreakdowns)
  }

  private def onUserMessage(scenario: String, isStart: Boolean, data: GraphiteData): Unit = {
    import data._
    usersByScenario(format.usersPath(scenario)).record(isStart)
    usersByScenario(format.allUsersPath).record(isStart)
  }

  private def onResponseMessage(response: ResponseMessage, data: GraphiteData): Unit = {
    import data._
    import response._
    val responseTime = ResponseTimings.responseTime(startTimestamp, endTimestamp)
    if (!configuration.data.graphite.light) {
      requestsByPath.getOrElseUpdate(format.responsePath(name, groupHierarchy), newResponseMetricsBuffer).add(status, responseTime)
    }
    requestsByPath.getOrElseUpdate(format.allResponsesPath, newResponseMetricsBuffer).add(status, responseTime)
  }

  override def onMessage(message: LoadEventMessage, data: GraphiteData): Unit = message match {
    case UserStartMessage(scenario, _) => onUserMessage(scenario, isStart = true, data)
    case UserEndMessage(scenario, _)   => onUserMessage(scenario, isStart = false, data)
    case response: ResponseMessage     => onResponseMessage(response, data)
    case _                             =>
  }

  override def onCrash(cause: String, data: GraphiteData): Unit = {}

  def onStop(data: GraphiteData): Unit = {
    cancelTimer(flushTimerName)
    onFlush(data)
  }

  private def sendMetricsToGraphite(
      data: GraphiteData,
      epoch: Long,
      requestsMetrics: Map[GraphitePath, MetricByStatus],
      userBreakdowns: Map[GraphitePath, UserBreakdown]
  ): Unit = {

    import data._
    metricsSender ! GraphiteMetrics(format.metrics(userBreakdowns, requestsMetrics), epoch)
  }

}
