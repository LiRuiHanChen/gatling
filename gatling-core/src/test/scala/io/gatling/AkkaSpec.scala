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

package io.gatling

import scala.concurrent.Await
import scala.concurrent.duration._

import io.gatling.core.EmptySession

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }

abstract class AkkaSpec extends TestKit(ActorSystem()) with BaseSpec with ImplicitSender with EmptySession {
  override def afterAll(): Unit = {
    val whenTerminated = system.terminate()
    Await.result(whenTerminated, 2.seconds)
  }
}
