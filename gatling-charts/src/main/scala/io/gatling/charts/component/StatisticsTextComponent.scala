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

package io.gatling.charts.component

import io.gatling.commons.shared.unstable.model.stats.GeneralStats
import io.gatling.commons.util.NumberHelper._
import io.gatling.core.config.GatlingConfiguration

private[charts] object Statistics {

  def printable[T: Numeric](value: T): String =
    value match {
      case GeneralStats.NoPlotMagicValue => "-"
      case _: Int | _: Long              => value.toString
      case _                             => implicitly[Numeric[T]].toDouble(value).toPrintableString
    }
}

private[charts] final class Statistics[T: Numeric](val name: String, val total: T, val success: T, val failure: T) {
  def all: List[T] = List(total, success, failure)
}

private[charts] final case class GroupedCount(name: String, count: Long, total: Long) {
  val percentage: Int = if (total == 0) 0 else (count.toDouble / total * 100).round.toInt
}

private[charts] final class RequestStatistics(
    val name: String,
    val path: String,
    val numberOfRequestsStatistics: Statistics[Long],
    val minResponseTimeStatistics: Statistics[Int],
    val maxResponseTimeStatistics: Statistics[Int],
    val meanStatistics: Statistics[Int],
    val stdDeviationStatistics: Statistics[Int],
    val percentiles1: Statistics[Int],
    val percentiles2: Statistics[Int],
    val percentiles3: Statistics[Int],
    val percentiles4: Statistics[Int],
    val groupedCounts: Seq[GroupedCount],
    val meanNumberOfRequestsPerSecondStatistics: Statistics[Double]
)

private[charts] class StatisticsTextComponent(implicit configuration: GatlingConfiguration) extends Component {

  override def html: String = s"""
                        <div class="infos">
                            <div class="infos-in">
	                        <div class="infos-title">STATISTICS</div>
                                <div class="repli"></div>                               
                                <div class="info">
                                    <h2 class="first">Executions</h2>
                                    <table>
                                        <thead>
                                            <tr><th></th><th>Total</th><th>OK</th><th>KO</th></tr>
                                        </thead>
                                        <tbody>
                                            <tr>
                                                <td class="title"></td>
                                                <td id="numberOfRequests" class="total"></td>
                                                <td id="numberOfRequestsOK" class="ok"></td>
                                                <td id="numberOfRequestsKO" class="ko"></td>
                                            </tr>
                                            <tr>
                                                <td class="title">Mean <abbr title="Count of events per second">cnt/s</abbr></td>
                                                <td id="meanNumberOfRequestsPerSecond" class="total"></td>
                                                <td id="meanNumberOfRequestsPerSecondOK" class="ok"></td>
                                                <td id="meanNumberOfRequestsPerSecondKO" class="ko"></td>
                                            </tr>
                                        </tbody>
                                    </table>
                                    <h2 class="second">Response Time (ms)</h2>
                                    <table>
                                        <thead>
                                            <tr>
                                                <th></th>
                                                <th>Total</th>
                                                <th>OK</th>
                                                <th>KO</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <tr>
                                                <td class="title">Min</td>
                                                <td id="minResponseTime" class="total"></td>
                                                <td id="minResponseTimeOK" class="ok"></td>
                                                <td id="minResponseTimeKO" class="ko"></td>
                                            </tr>
                                            <tr>
                                                <td class="title">${configuration.charting.indicators.percentile1.toRank} percentile</td>
                                                <td id="percentiles1" class="total"></td>
                                                <td id="percentiles1OK" class="ok"></td>
                                                <td id="percentiles1KO" class="ko"></td>
                                            </tr>
                                            <tr>
                                                <td class="title">${configuration.charting.indicators.percentile2.toRank} percentile</td>
                                                <td id="percentiles2" class="total"></td>
                                                <td id="percentiles2OK" class="ok"></td>
                                                <td id="percentiles2KO" class="ko"></td>
                                            </tr>
                                            <tr>
                                                <td class="title">${configuration.charting.indicators.percentile3.toRank} percentile</td>
                                                <td id="percentiles3" class="total"></td>
                                                <td id="percentiles3OK" class="ok"></td>
                                                <td id="percentiles3KO" class="ko"></td>
                                            </tr>
                                            <tr>
                                                <td class="title">${configuration.charting.indicators.percentile4.toRank} percentile</td>
                                                <td id="percentiles4" class="total"></td>
                                                <td id="percentiles4OK" class="ok"></td>
                                                <td id="percentiles4KO" class="ko"></td>
                                            </tr>
                                            <tr>
                                                <td class="title">Max</td>
                                                <td id="maxResponseTime" class="total"></td>
                                                <td id="maxResponseTimeOK" class="ok"></td>
                                                <td id="maxResponseTimeKO" class="ko"></td>
                                            </tr>
                                            <tr>
                                                <td class="title">Mean</td>
                                                <td id="meanResponseTime" class="total"></td>
                                                <td id="meanResponseTimeOK" class="ok"></td>
                                                <td id="meanResponseTimeKO" class="ko"></td>
                                            </tr>
                                            <tr>
                                                <td class="title">Std Deviation</td>
                                                <td id="standardDeviation" class="total"></td>
                                                <td id="standardDeviationOK" class="ok"></td>
                                                <td id="standardDeviationKO" class="ko"></td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </div>
"""

  val js = ""

  val jsFiles: Seq[String] = Seq.empty
}
