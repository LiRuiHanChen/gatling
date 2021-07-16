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

package io.gatling.commons.util

import java.util.concurrent.atomic.AtomicInteger

sealed trait CyclicCounter {
  def nextVal: Int
}

object CyclicCounter {
  final class ThreadSafe(max: Int) extends CyclicCounter {
    private val counter = new AtomicInteger
    def nextVal: Int = counter.getAndIncrement % max
  }

  final class NonThreadSafe(max: Int) extends CyclicCounter {
    private var counter = 0
    def nextVal: Int = {
      val current = counter
      counter += 1
      if (counter == max) {
        counter = 0
      }
      current
    }
  }
}
