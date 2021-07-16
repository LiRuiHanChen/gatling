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

package io.gatling.http.check.body

import io.gatling.commons.validation._
import io.gatling.core.check.{ CheckMaterializer, Preparer }
import io.gatling.core.check.css.{ CssCheckType, CssSelectors }
import io.gatling.http.check.{ HttpCheck, HttpCheckMaterializer }
import io.gatling.http.check.HttpCheckScope.Body
import io.gatling.http.response.Response

import jodd.lagarto.dom.NodeSelector

object HttpBodyCssCheckMaterializer {

  private val ErrorMapper: String => String = "Could not parse response into a Jodd NodeSelector: " + _

  def instance(selectors: CssSelectors): CheckMaterializer[CssCheckType, HttpCheck, Response, NodeSelector] = {
    val preparer: Preparer[Response, NodeSelector] = response => safely(ErrorMapper)(selectors.parse(response.body.chars).success)
    new HttpCheckMaterializer[CssCheckType, NodeSelector](Body, preparer)
  }
}
