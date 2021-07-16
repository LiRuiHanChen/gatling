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

package io.gatling.core.stats.writer

sealed abstract class RecordHeader(val value: String)
object RunRecordHeader extends RecordHeader("RUN")
object RequestRecordHeader extends RecordHeader("REQUEST")
object UserRecordHeader extends RecordHeader("USER")
object GroupRecordHeader extends RecordHeader("GROUP")
object ErrorRecordHeader extends RecordHeader("ERROR")
object AssertionRecordHeader extends RecordHeader("ASSERTION")

sealed abstract class RawRecord(header: RecordHeader, recordLength: Int) {
  def unapply(array: Array[String]): Option[Array[String]] =
    if (array.length >= recordLength && array(0) == header.value) Some(array) else None
}

object RawRunRecord extends RawRecord(RunRecordHeader, 6)
object RawRequestRecord extends RawRecord(RequestRecordHeader, 7)
object RawUserRecord extends RawRecord(UserRecordHeader, 4)
object RawGroupRecord extends RawRecord(GroupRecordHeader, 6)
object RawErrorRecord extends RawRecord(ErrorRecordHeader, 3)
object RawAssertionRecord extends RawRecord(AssertionRecordHeader, 2)
