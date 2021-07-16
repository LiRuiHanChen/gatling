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

package io.gatling.sbt

import sbt.testing.{ Fingerprint, Framework }

/**
 * Gatling's test framework.
 * Test classes are filtered to only keep classes matching [[GatlingFingerprint]].
 */
class GatlingFramework extends Framework {

  override val name: String = "gatling"

  override val fingerprints: Array[Fingerprint] = Array[Fingerprint](new GatlingFingerprint)

  override def runner(args: Array[String], remoteArgs: Array[String], testClassLoader: ClassLoader): GatlingRunner =
    new GatlingRunner(args, remoteArgs, testClassLoader)

}
