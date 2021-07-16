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

package io.gatling.core.config

import java.nio.file.{ Path, Paths }

import scala.util.Properties.{ envOrElse, propOrElse }

import io.gatling.commons.shared.unstable.util.PathHelper._

object GatlingFiles {

  /*
    增加本地调试路径
   */
//  private val GatlingHome: Path = Paths.get(envOrElse("GATLING_HOME", propOrElse("GATLING_HOME", ".")))
  private val GatlingHome: Path =
    Paths.get("/Users/lilei/.ivy2/local/io.gatling.highcharts/gatling-charts-highcharts-bundle/3.5.1.zuoye-SNAPSHOT/zips/gatling-charts-highcharts-bundle-3.5.1.zuoye-SNAPSHOT")

  private def resolvePath(path: Path): Path =
    (if (path.isAbsolute || path.exists) path else GatlingHome / path).normalize.toAbsolutePath

  def simulationsDirectory(configuration: GatlingConfiguration): Path = resolvePath(configuration.core.directory.simulations)
  def resourcesDirectory(configuration: GatlingConfiguration): Path = resolvePath(configuration.core.directory.resources)
  def binariesDirectory(configuration: GatlingConfiguration): Path =
    configuration.core.directory.binaries.map(path => resolvePath(path)).getOrElse(GatlingHome / "target" / "test-classes")
  def resultDirectory(runUuid: String, configuration: GatlingConfiguration): Path = resolvePath(configuration.core.directory.results) / runUuid

  def simulationLogDirectory(runUuid: String, create: Boolean, configuration: GatlingConfiguration): Path = {
    val dir = resultDirectory(runUuid, configuration)
    if (create) {
      dir.mkdirs()
    } else {
      require(dir.toFile.exists, s"simulation directory '$dir' doesn't exist")
      require(dir.toFile.isDirectory, s"simulation directory '$dir' is not a directory")
      dir
    }
  }
}
