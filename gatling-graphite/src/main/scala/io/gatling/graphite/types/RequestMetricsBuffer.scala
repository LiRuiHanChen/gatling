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

package io.gatling.graphite.types

import io.gatling.commons.stats.Status

private[graphite] trait RequestMetricsBuffer {
  def add(status: Status, time: Long): Unit
  def clear(): Unit
  def metricsByStatus: MetricByStatus
}

private[graphite] final case class MetricByStatus(ok: Option[Metrics], ko: Option[Metrics], all: Option[Metrics])
private[graphite] final case class Metrics(
    count: Long,
    min: Int,
    max: Int,
    mean: Int,
    stdDev: Int,
    percentile1: Int,
    percentile2: Int,
    percentile3: Int,
    percentile4: Int
)
