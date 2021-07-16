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

package io.gatling.charts.report

import io.gatling.charts.component.{ ComponentLibrary, GroupedCount, RequestStatistics, Statistics }
import io.gatling.charts.config.ChartsFiles
import io.gatling.charts.stats.RequestPath
import io.gatling.charts.template.{ ConsoleTemplate, GlobalStatsJsonTemplate, StatsJsTemplate }
import io.gatling.commons.shared.unstable.model.stats.{ GeneralStats, Group, GroupStatsPath, RequestStatsPath }
import io.gatling.commons.stats._
import io.gatling.commons.util.NumberHelper._
import io.gatling.core.config.GatlingConfiguration

import com.typesafe.scalalogging.StrictLogging

private[charts] class StatsReportGenerator(reportsGenerationInputs: ReportsGenerationInputs, chartsFiles: ChartsFiles, componentLibrary: ComponentLibrary)(
    implicit configuration: GatlingConfiguration
) extends StrictLogging {

  import reportsGenerationInputs._

  def generate(): Unit = {

    def percentiles(rank: Double, title: Double => String, total: GeneralStats, ok: GeneralStats, ko: GeneralStats) =
      new Statistics(title(rank), total.percentile(rank), ok.percentile(rank), ko.percentile(rank))

    def computeRequestStats(name: String, requestName: Option[String], group: Option[Group]): RequestStatistics = {

      val total = logFileReader.requestGeneralStats(requestName, group, None)
      val ok = logFileReader.requestGeneralStats(requestName, group, Some(OK))
      val ko = logFileReader.requestGeneralStats(requestName, group, Some(KO))

      val numberOfRequestsStatistics = new Statistics("request count", total.count, ok.count, ko.count)
      val minResponseTimeStatistics = new Statistics("min response time", total.min, ok.min, ko.min)
      val maxResponseTimeStatistics = new Statistics("max response time", total.max, ok.max, ko.max)
      val meanResponseTimeStatistics = new Statistics("mean response time", total.mean, ok.mean, ko.mean)
      val stdDeviationStatistics = new Statistics("std deviation", total.stdDev, ok.stdDev, ko.stdDev)

      val percentilesTitle = (rank: Double) => s"response time ${rank.toRank} percentile"

      val percentiles1 = percentiles(configuration.charting.indicators.percentile1, percentilesTitle, total, ok, ko)
      val percentiles2 = percentiles(configuration.charting.indicators.percentile2, percentilesTitle, total, ok, ko)
      val percentiles3 = percentiles(configuration.charting.indicators.percentile3, percentilesTitle, total, ok, ko)
      val percentiles4 = percentiles(configuration.charting.indicators.percentile4, percentilesTitle, total, ok, ko)
      val meanNumberOfRequestsPerSecondStatistics = new Statistics("mean requests/sec", total.meanRequestsPerSec, ok.meanRequestsPerSec, ko.meanRequestsPerSec)

      val groupedCounts = logFileReader
        .numberOfRequestInResponseTimeRange(requestName, group)
        .map { case (rangeName, count) =>
          GroupedCount(rangeName, count, total.count)
        }

      val path = requestName match {
        case Some(n) => RequestPath.path(n, group)
        case None    => group.map(RequestPath.path).getOrElse("")
      }

      new RequestStatistics(
        name,
        path,
        numberOfRequestsStatistics,
        minResponseTimeStatistics,
        maxResponseTimeStatistics,
        meanResponseTimeStatistics,
        stdDeviationStatistics,
        percentiles1,
        percentiles2,
        percentiles3,
        percentiles4,
        groupedCounts,
        meanNumberOfRequestsPerSecondStatistics
      )
    }

    def computeGroupStats(name: String, group: Group): RequestStatistics = {

      def groupStatsFunction: (Group, Option[Status]) => GeneralStats =
        if (configuration.charting.useGroupDurationMetric) {
          logger.debug("Use group duration stats.")
          logFileReader.groupDurationGeneralStats
        } else {
          logger.debug("Use group cumulated response time stats.")
          logFileReader.groupCumulatedResponseTimeGeneralStats
        }

      val total = groupStatsFunction(group, None)
      val ok = groupStatsFunction(group, Some(OK))
      val ko = groupStatsFunction(group, Some(KO))

      val numberOfRequestsStatistics = new Statistics("numberOfRequests", total.count, ok.count, ko.count)
      val minResponseTimeStatistics = new Statistics("minResponseTime", total.min, ok.min, ko.min)
      val maxResponseTimeStatistics = new Statistics("maxResponseTime", total.max, ok.max, ko.max)
      val meanResponseTimeStatistics = new Statistics("meanResponseTime", total.mean, ok.mean, ko.mean)
      val stdDeviationStatistics = new Statistics("stdDeviation", total.stdDev, ok.stdDev, ko.stdDev)

      val percentiles1 = percentiles(configuration.charting.indicators.percentile1, _ => "percentiles1", total, ok, ko)
      val percentiles2 = percentiles(configuration.charting.indicators.percentile2, _ => "percentiles2", total, ok, ko)
      val percentiles3 = percentiles(configuration.charting.indicators.percentile3, _ => "percentiles3", total, ok, ko)
      val percentiles4 = percentiles(configuration.charting.indicators.percentile4, _ => "percentiles4", total, ok, ko)
      val meanNumberOfRequestsPerSecondStatistics =
        new Statistics("meanNumberOfRequestsPerSecond", total.meanRequestsPerSec, ok.meanRequestsPerSec, ko.meanRequestsPerSec)

      val groupedCounts = logFileReader
        .numberOfRequestInResponseTimeRange(None, Some(group))
        .map { case (rangeName, count) =>
          GroupedCount(rangeName, count, total.count)
        }

      val path = RequestPath.path(group)

      new RequestStatistics(
        name,
        path,
        numberOfRequestsStatistics,
        minResponseTimeStatistics,
        maxResponseTimeStatistics,
        meanResponseTimeStatistics,
        stdDeviationStatistics,
        percentiles1,
        percentiles2,
        percentiles3,
        percentiles4,
        groupedCounts,
        meanNumberOfRequestsPerSecondStatistics
      )
    }

    val rootContainer = GroupContainer.root(computeRequestStats(ChartsFiles.GlobalPageName, None, None))

    val statsPaths = logFileReader.statsPaths

    val groupsByHierarchy: Map[List[String], Group] = statsPaths
      .collect {
        case GroupStatsPath(group)            => group
        case RequestStatsPath(_, Some(group)) => group
      }
      .map(group => group.hierarchy.reverse -> group)
      .toMap

    val seenGroups = collection.mutable.HashSet.empty[List[String]]

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    def addGroupsRec(hierarchy: List[String]): Unit = {

      if (!seenGroups.contains(hierarchy)) {
        seenGroups += hierarchy

        hierarchy match {
          case _ :: tail if tail.nonEmpty => addGroupsRec(tail)
          case _                          =>
        }

        val group = groupsByHierarchy(hierarchy)
        val stats = computeGroupStats(group.name, group)
        rootContainer.addGroup(group, stats)
      }
    }

    val requestStatsPaths = statsPaths.collect { case path: RequestStatsPath => path }
    requestStatsPaths.foreach { case RequestStatsPath(request, group) =>
      group.foreach { group =>
        addGroupsRec(group.hierarchy.reverse)
      }
      val stats = computeRequestStats(request, Some(request), group)
      rootContainer.addRequest(group, request, stats)
    }

    new TemplateWriter(chartsFiles.statsJsFile).writeToFile(new StatsJsTemplate(rootContainer, false).getOutput(configuration.core.charset))
    new TemplateWriter(chartsFiles.statsJsonFile).writeToFile(new StatsJsTemplate(rootContainer, true).getOutput(configuration.core.charset))
    new TemplateWriter(chartsFiles.globalStatsJsonFile).writeToFile(new GlobalStatsJsonTemplate(rootContainer.stats, true).getOutput)
    println(ConsoleTemplate.println(rootContainer.stats, logFileReader.errors(None, None)))
  }
}
