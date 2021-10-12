/*
 * Copyright 2017-2020 Aleksey Fomkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package korolev.akka

import scala.concurrent.duration._

case class AkkaHttpServerConfig(maxRequestBodySize: Int = AkkaHttpServerConfig.DefaultMaxRequestBodySize,
                                outputBufferSize: Int = AkkaHttpServerConfig.DefaultOutputBufferSize,
                                wsStreamedCompletionTimeout: FiniteDuration = AkkaHttpServerConfig.DefaultWsStreamedCompletionTimeout,
                                wsStreamedParallelism: Int = AkkaHttpServerConfig.DefaultWsStreamedParallelism)

object AkkaHttpServerConfig {
  val DefaultMaxRequestBodySize: Int = 8 * 1024 * 1024
  val DefaultOutputBufferSize: Int = 1000
  val DefaultWsStreamedCompletionTimeout: FiniteDuration = 30.seconds
  val DefaultWsStreamedParallelism: Int = 2
}
