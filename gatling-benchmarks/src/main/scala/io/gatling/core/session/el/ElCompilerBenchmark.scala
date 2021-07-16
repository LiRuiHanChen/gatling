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

package io.gatling.core.session.el

import io.gatling.Utils._
import io.gatling.commons.validation.Validation
import io.gatling.core.ValidationImplicits
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.Session

import org.openjdk.jmh.annotations.Benchmark

object ElCompilerBenchmark extends ValidationImplicits {

  private implicit val config = GatlingConfiguration.loadForTest()
  private val charset = config.core.charset

  private val Session1 = Session("Scenario", 0, null).set("id", 3)
  private val Template = ElCompiler.compile[String](resourceAsString("sample-el.json", charset))
}

class ElCompilerBenchmark {
  import ElCompilerBenchmark._

  @Benchmark
  def testBasic(): Validation[String] =
    Template(Session1)
}
