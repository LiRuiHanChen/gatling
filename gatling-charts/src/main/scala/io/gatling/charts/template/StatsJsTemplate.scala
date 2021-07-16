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

package io.gatling.charts.template

import java.nio.charset.Charset

import io.gatling.charts.FileNamingConventions
import io.gatling.charts.component.RequestStatistics
import io.gatling.charts.report.Container.{ Group, Request }
import io.gatling.charts.report.GroupContainer
import io.gatling.charts.util.JsHelper._

private[charts] class StatsJsTemplate(stats: GroupContainer, outputJson: Boolean) {

  private def fieldName(field: String): String = if (outputJson) s""""$field"""" else field

  def getOutput(charset: Charset): String = {

    def renderStats(request: RequestStatistics, path: String): String = {
      val jsonStats = new GlobalStatsJsonTemplate(request, outputJson).getOutput

      s"""${fieldName("name")}: "${escapeJsIllegalChars(request.name)}",
${fieldName("path")}: "${escapeJsIllegalChars(request.path)}",
${fieldName("pathFormatted")}: "$path",
${fieldName("stats")}: $jsonStats"""
    }

    def renderSubGroups(group: GroupContainer): Iterable[String] =
      group.groups.values.map { subGroup =>
        s""""${subGroup.name.toGroupFileName(charset)}": {
          ${renderGroup(subGroup)}
     }"""
      }

    def renderSubRequests(group: GroupContainer): Iterable[String] =
      group.requests.values.map { request =>
        s""""${request.name.toRequestFileName(charset)}": {
        ${fieldName("type")}: "$Request",
        ${renderStats(request.stats, request.stats.path.toRequestFileName(charset))}
    }"""
      }

    def renderGroup(group: GroupContainer): String =
      s"""${fieldName("type")}: "$Group",
${renderStats(group.stats, group.stats.path.toGroupFileName(charset))},
${fieldName("contents")}: {
${(renderSubGroups(group) ++ renderSubRequests(group)).mkString(",")}
}
"""

    if (outputJson)
      s"""{
  ${renderGroup(stats)}
}"""
    else
      s"""var stats = {
    ${renderGroup(stats)}
}

function fillStats(stat){
    $$("#numberOfRequests").append(stat.numberOfRequests.total);
    $$("#numberOfRequestsOK").append(stat.numberOfRequests.ok);
    $$("#numberOfRequestsKO").append(stat.numberOfRequests.ko);

    $$("#minResponseTime").append(stat.minResponseTime.total);
    $$("#minResponseTimeOK").append(stat.minResponseTime.ok);
    $$("#minResponseTimeKO").append(stat.minResponseTime.ko);

    $$("#maxResponseTime").append(stat.maxResponseTime.total);
    $$("#maxResponseTimeOK").append(stat.maxResponseTime.ok);
    $$("#maxResponseTimeKO").append(stat.maxResponseTime.ko);

    $$("#meanResponseTime").append(stat.meanResponseTime.total);
    $$("#meanResponseTimeOK").append(stat.meanResponseTime.ok);
    $$("#meanResponseTimeKO").append(stat.meanResponseTime.ko);

    $$("#standardDeviation").append(stat.standardDeviation.total);
    $$("#standardDeviationOK").append(stat.standardDeviation.ok);
    $$("#standardDeviationKO").append(stat.standardDeviation.ko);

    $$("#percentiles1").append(stat.percentiles1.total);
    $$("#percentiles1OK").append(stat.percentiles1.ok);
    $$("#percentiles1KO").append(stat.percentiles1.ko);

    $$("#percentiles2").append(stat.percentiles2.total);
    $$("#percentiles2OK").append(stat.percentiles2.ok);
    $$("#percentiles2KO").append(stat.percentiles2.ko);

    $$("#percentiles3").append(stat.percentiles3.total);
    $$("#percentiles3OK").append(stat.percentiles3.ok);
    $$("#percentiles3KO").append(stat.percentiles3.ko);

    $$("#percentiles4").append(stat.percentiles4.total);
    $$("#percentiles4OK").append(stat.percentiles4.ok);
    $$("#percentiles4KO").append(stat.percentiles4.ko);

    $$("#meanNumberOfRequestsPerSecond").append(stat.meanNumberOfRequestsPerSecond.total);
    $$("#meanNumberOfRequestsPerSecondOK").append(stat.meanNumberOfRequestsPerSecond.ok);
    $$("#meanNumberOfRequestsPerSecondKO").append(stat.meanNumberOfRequestsPerSecond.ko);
}
"""
  }
}
