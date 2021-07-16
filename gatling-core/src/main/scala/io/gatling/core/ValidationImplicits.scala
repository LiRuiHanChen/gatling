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

package io.gatling.core

import scala.language.reflectiveCalls
import scala.reflect.ClassTag

import io.gatling.commons.Exclude
import io.gatling.commons.util.TypeCaster
import io.gatling.commons.validation._
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session._
import io.gatling.core.session.el._
import io.gatling.core.structure.{ ChainBuilder, ScenarioBuilder }

sealed trait NonValidable

@SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
object NonValidable {
  private val exclude = Exclude.list[NonValidable]
  implicit val a1, a2 = exclude[SessionAttribute]
  implicit val b1, b2 = exclude[ChainBuilder]
  implicit val c1, c2 = exclude[ScenarioBuilder]
  implicit val d1, d2 = exclude[ActionBuilder]

  // Partially apply the type to be compatible with context bounds:
  type Types[Scope] = {
    type DoesNotContain[X] = Exclude[Scope, X]
  }
}

class NoUnexpectedValidationLifting[T](value: T) {
  def map[A](f: T => A): Validation[A] = throw new UnsupportedOperationException("Not supposed to be ever called")
  def flatMap[A](f: T => Validation[A]): Validation[A] = throw new UnsupportedOperationException("Not supposed to be ever called")
  def mapError(f: String => String): Validation[T] = throw new UnsupportedOperationException("Not supposed to be ever called")
  def foreach(f: T => Any): Unit = throw new UnsupportedOperationException("Not supposed to be ever called")
  def withFilter(p: T => Boolean): Validation[T] = throw new UnsupportedOperationException("Not supposed to be ever called")
  def filter(p: T => Boolean): Validation[T] = throw new UnsupportedOperationException("Not supposed to be ever called")
  def onSuccess(f: T => Any): Unit = throw new UnsupportedOperationException("Not supposed to be ever called")
  def onFailure(f: String => Any): Unit = throw new UnsupportedOperationException("Not supposed to be ever called")
  def recover[A >: T](v: => A): Validation[A] = throw new UnsupportedOperationException("Not supposed to be ever called")
  def toOption: Option[T] = throw new UnsupportedOperationException("Not supposed to be ever called")
}

trait ValidationImplicits {

  import NonValidable._

  implicit def stringToExpression[T: TypeCaster: Types[NonValidable]#DoesNotContain: ClassTag](string: String): Expression[T] = string.el
  implicit def value2Success[T: Types[NonValidable]#DoesNotContain](value: T): Validation[T] = value.success
  implicit def value2Expression[T: Types[NonValidable]#DoesNotContain](value: T): Expression[T] = value.expressionSuccess
  implicit def value2NoUnexpectedValidationLifting[T](value: T): NoUnexpectedValidationLifting[T] = new NoUnexpectedValidationLifting(value)
}
