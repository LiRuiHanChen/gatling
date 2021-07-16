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

package io.gatling.core.session

import io.gatling.commons.stats.Status
import io.gatling.commons.validation._
import io.gatling.core.action.Action

import com.typesafe.scalalogging.StrictLogging

sealed trait Block extends Product with Serializable

sealed trait CounterBlock extends Block {
  def counterName: String
}

object LoopBlock extends StrictLogging {

  def unapply(block: Block): Option[String] = block match {
    case ExitAsapLoopBlock(counterName, _, _) => Some(counterName)
    case ExitOnCompleteLoopBlock(counterName) => Some(counterName)
    case _                                    => None
  }

  def continue(continueCondition: Expression[Boolean], session: Session): Boolean = continueCondition(session) match {
    case Success(eval) => eval
    case Failure(message) =>
      logger.error(s"Condition evaluation crashed with message '$message', exiting loop")
      false
  }
}

final case class ExitOnCompleteLoopBlock(counterName: String) extends CounterBlock

final case class ExitAsapLoopBlock(counterName: String, condition: Expression[Boolean], exitAction: Action) extends CounterBlock

final case class TryMaxBlock(counterName: String, tryMaxAction: Action, status: Status) extends CounterBlock

final case class GroupBlock(groups: List[String], startTimestamp: Long, cumulatedResponseTime: Int, status: Status) extends Block
