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

package io.gatling.core.util.cache

import java.util.concurrent.ConcurrentMap

import scala.collection.immutable.Queue

import com.github.benmanes.caffeine.cache.{ Caffeine, LoadingCache }

object Cache {

  def newConcurrentCache[K, V](maxSize: Long): ConcurrentMap[K, V] =
    Caffeine.newBuilder
      .asInstanceOf[Caffeine[Any, Any]]
      .maximumSize(maxSize)
      .build[K, V]
      .asMap

  def newConcurrentLoadingCache[K, V](maxSize: Long, f: K => V): LoadingCache[K, V] =
    Caffeine.newBuilder
      .asInstanceOf[Caffeine[Any, Any]]
      .maximumSize(maxSize)
      .build(key => f(key))

  def newImmutableCache[K, V](maxCapacity: Int): Cache[K, V] = new Cache[K, V](Queue.empty, Map.empty, maxCapacity)
}

class Cache[K, V](queue: Queue[K], map: Map[K, V], maxCapacity: Int) {

  def put(key: K, value: V): Cache[K, V] = {
    if (map.get(key).contains(value) || maxCapacity == 0) {
      this

    } else if (map.size == maxCapacity) {
      val (removedKey, newQueue) = queue.dequeue
      val newMap = map - removedKey + (key -> value)
      new Cache(newQueue.enqueue(key), newMap, maxCapacity)

    } else {
      val newQueue = queue.enqueue(key)
      val newMap = map + (key -> value)
      new Cache(newQueue, newMap, maxCapacity)
    }
  }

  def remove(key: K): Cache[K, V] =
    if (map.contains(key)) {
      val newQueue = queue.filter(_ != key)
      val newMap = map - key
      new Cache(newQueue, newMap, maxCapacity)

    } else {
      this
    }

  def get(key: K): Option[V] = map.get(key)
}
