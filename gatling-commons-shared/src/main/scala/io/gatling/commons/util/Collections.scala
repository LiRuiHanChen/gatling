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

object Collections {

  var injectionProfileUserCount: scala.collection.mutable.Map[String, java.lang.Long] = scala.collection.mutable.Map()

  implicit class PimpedIterable[A](val seq: Iterable[A]) extends AnyVal {

    def sumBy[B](f: A => B)(implicit num: Numeric[B]): B = {
      var sum = num.zero

      /**
       * 收集预热用户数和并发用户数
       */
      for (x <- seq) {
        sum = num.plus(sum, f(x))
        injectionProfileUserCount.put(x.getClass.getSimpleName, java.lang.Long.parseLong(sum.toString))
      }
      sum
    }
  }

  @deprecated("Will be removed once FrontLine stop supporting Gatling 3.3", "3.4.0")
  implicit class PimpedTraversableOnce[A](val t: TraversableOnce[A]) extends AnyVal {

    def sumBy[B](f: A => B)(implicit num: Numeric[B]): B = {
      var sum = num.zero
      t.iterator.foreach { x =>
        sum = num.plus(sum, f(x))
      }
      sum
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.ArrayEquals"))
  @deprecated("Will be removed once FrontLine stop supporting Gatling 3.4", "3.5.0")
  implicit class PimpedIterator[A](val it: Iterator[A]) extends AnyVal {

    def lift(i: Int): Option[A] = {
      var j = 0
      var found: Option[A] = None
      while (it.hasNext && found.isEmpty) {
        val value = it.next()
        if (i == j) {
          found = Some(value)
        }
        j += 1
      }
      found
    }
  }

}
