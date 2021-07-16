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

import java.lang

import scala.collection.mutable

import io.gatling.commons.util.Collections
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.stats.writer.RunMessage
import io.gatling.graphite.bean._
import io.gatling.graphite.types._

abstract class GraphitePathPattern(runMessage: RunMessage, configuration: GatlingConfiguration) {

  def allUsersPath: GraphitePath

  def usersPath(scenario: String): GraphitePath

  def allResponsesPath: GraphitePath

  def responsePath(requestName: String, groups: List[String]): GraphitePath

  var metricsTempEntry: (GraphitePath, Option[Metrics]) = null
  var exitFlag: Boolean = false
  var koCount: Long = 0
  val requestKoPercentage: Long = configuration.index.requestKoPercentage

  val injectionProfileCountMap: mutable.Map[String, lang.Long] = Collections.injectionProfileUserCount
  var rampUserCount: java.lang.Long = 0
  var constantUserCount: java.lang.Long = 0
  collectNumberOfUsers()
  val thresholdValue: Long = (this.rampUserCount / 100) * requestKoPercentage

  def metrics(userBreakdowns: Map[GraphitePath, UserBreakdown], responseMetricsByStatus: Map[GraphitePath, MetricByStatus]): Iterator[(String, Long)] = {
    val userMetrics = userBreakdowns.iterator.flatMap(byProgress)

    val targetResponseMetrics =
      if (configuration.data.graphite.light)
        responseMetricsByStatus.get(allResponsesPath).map(m => Iterator.single(allResponsesPath -> m)).getOrElse(Iterator.empty)
      else
        responseMetricsByStatus.iterator

    val responseMetrics = targetResponseMetrics.flatMap(byStatus).flatMap(byMetric)

    //只要在有response有结果时才进行指标统计
    if (metricsTempEntry != null) {
      byFlag(configuration, koCount)
    }

    (userMetrics ++ responseMetrics)
      .map { case (path, value) => (metricRootPath / path).pathKey -> value }
  }

  private def byProgress(metricsEntry: (GraphitePath, UserBreakdown)): Seq[(GraphitePath, Long)] = {
    val (path, usersBreakdown) = metricsEntry
    Seq(
      activeUsers(path) -> usersBreakdown.active,
      waitingUsers(path) -> usersBreakdown.waiting,
      doneUsers(path) -> usersBreakdown.done
    )
  }

  private def byStatus(metricsEntry: (GraphitePath, MetricByStatus)): Seq[(GraphitePath, Option[Metrics])] = {
    val (path, metricByStatus) = metricsEntry

    if (metricByStatus.ko.isDefined && path.path.head.equals("allRequests")) {
      val count = metricByStatus.ko.toList.head.count
      koCount = koCount + count
    }
    Seq(
      okResponses(path) -> metricByStatus.ok,
      koResponses(path) -> metricByStatus.ko,
      allResponses(path) -> metricByStatus.all
    )
  }

  private def byFlag(configuration: GatlingConfiguration, koCount: Long): Unit = {
    checkStatistic(configuration, koCount)
  }

  /**
   * 增加指标数据处理
   * 如果95线大于配置文件中 responseTimePercentiles3指定值,退出标记为:true
   */
  private def percentileStatistics(metricsTempEntry: (GraphitePath, Option[Metrics]), configuration: GatlingConfiguration): Unit = {
    metricsTempEntry match {
      case (path, Some(m)) =>
        if (m.percentile3 >= configuration.index.responseTimePercentiles3) {
          this.exitFlag = true
        }
    }
  }

  private def byMetric(metricsEntry: (GraphitePath, Option[Metrics])): Seq[(GraphitePath, Long)] =
    metricsEntry match {
      case (path, None) => Seq(count(path) -> 0)
      case (path, Some(m)) =>
        metricsTempEntry = metricsEntry
        Seq(
          count(path) -> m.count,
          min(path) -> m.min,
          max(path) -> m.max,
          mean(path) -> m.mean,
          stdDev(path) -> m.stdDev,
          percentiles1(path) -> m.percentile1,
          percentiles2(path) -> m.percentile2,
          percentiles3(path) -> m.percentile3,
          percentiles4(path) -> m.percentile4
        )
    }

  /**
   * 判断ko数量
   * 读取配置中的百分比例
   *
   * @param configuration
   * @param koCount
   */
  def checkKoCount(configuration: GatlingConfiguration, koCount: Long): Unit = {
    if (koCount > thresholdValue) {
      this.exitFlag = true
    }
  }

  /**
   * 1、判断响应时间(95线)
   * 2、判断ko量
   */
  private def checkStatistic(configuration: GatlingConfiguration, koCount: Long): Unit = {
    // 判断95线指标
    percentileStatistics(metricsTempEntry, configuration)
    // 判断ko数量
    checkKoCount(configuration, koCount)
    stopTest(this.exitFlag)
  }

  /**
   * 停止测试
   *
   * @param exitFlag 标记
   */
  def stopTest(exitFlag: Boolean): Unit = {
    if (exitFlag) {
      System.exit(1)
    }
  }

  /**
   * 脚本中的需要的用户数量
   * 示例:
   * AtOnceOpenInjection -> 60, ConstantRateOpenInjection -> 1060, ShortScenarioDescription -> 1560, RampRateOpenInjection -> 1560
   *
   * @return 持续数量
   */
  def collectNumberOfUsers(): java.lang.Long = {
    if (injectionProfileCountMap.nonEmpty) {
      // 持续数量（持续时间相关联）
      this.constantUserCount = injectionProfileCountMap.getOrElse("ConstantRateOpenInjection", 1)
      // 预热数量  ShortScenarioDescription 与RampRateOpenInjection值相同,但是RampRateOpenInjection可能为空
      this.rampUserCount = injectionProfileCountMap.getOrElse("RampRateOpenInjection", 1)
    }
    this.rampUserCount
  }

  protected def metricRootPath: GraphitePath

  protected def activeUsers(path: GraphitePath): GraphitePath

  protected def waitingUsers(path: GraphitePath): GraphitePath

  protected def doneUsers(path: GraphitePath): GraphitePath

  protected def okResponses(path: GraphitePath): GraphitePath

  protected def koResponses(path: GraphitePath): GraphitePath

  protected def allResponses(path: GraphitePath): GraphitePath

  protected def count(path: GraphitePath): GraphitePath

  protected def min(path: GraphitePath): GraphitePath

  protected def max(path: GraphitePath): GraphitePath

  protected def mean(path: GraphitePath): GraphitePath

  protected def stdDev(path: GraphitePath): GraphitePath

  protected def percentiles1(path: GraphitePath): GraphitePath

  protected def percentiles2(path: GraphitePath): GraphitePath

  protected def percentiles3(path: GraphitePath): GraphitePath

  protected def percentiles4(path: GraphitePath): GraphitePath
}
