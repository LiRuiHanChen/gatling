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

package io.gatling.jdbc

import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.feeder.{ FeederBuilderBase, InMemoryFeederSource, SourceFeederBuilder }
import io.gatling.jdbc.feeder.JdbcFeederSource

object Predef {

  def jdbcFeeder(url: String, username: String, password: String, sql: String)(implicit configuration: GatlingConfiguration): FeederBuilderBase[Any] =
    SourceFeederBuilder(InMemoryFeederSource(JdbcFeederSource(url, username, password, sql)), configuration)
}
