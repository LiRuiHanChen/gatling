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

package io.gatling.core.check.regex

import io.gatling.commons.validation._
import io.gatling.core.check._

object RegexExtractors {

  def find[X: GroupExtractor](name: String, pattern: String, occurrence: Int, patterns: Patterns): FindCriterionExtractor[String, String, X] =
    new FindCriterionExtractor[String, String, X](
      name,
      pattern,
      occurrence,
      patterns.find(_, pattern, occurrence).success
    )

  def findAll[X: GroupExtractor](name: String, pattern: String, patterns: Patterns): FindAllCriterionExtractor[String, String, X] =
    new FindAllCriterionExtractor[String, String, X](
      name,
      pattern,
      patterns.findAll(_, pattern).liftSeqOption.success
    )

  def count(name: String, pattern: String, patterns: Patterns): CountCriterionExtractor[String, String] = {
    new CountCriterionExtractor[String, String](
      name,
      pattern,
      prepared => Some(patterns.count(prepared, pattern)).success
    )
  }
}
