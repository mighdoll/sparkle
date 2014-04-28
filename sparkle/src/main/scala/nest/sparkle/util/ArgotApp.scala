/* Copyright 2014  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.util

import org.clapper.argot.ArgotParser
import org.clapper.argot.FlagOption
import org.clapper.argot.ArgotUsageException

/** a utility trait for making a main class that uses Argot command line parsing.  */
trait ArgotApp extends App {
  def app[T](parser: ArgotParser, help: FlagOption[Boolean])(fn: =>T):T = {
    try {
      parser.parse(args)

      help.value.foreach { v =>
        Console.println(parser.usageString())
        sys.exit(0)
      }

      fn

    } catch {
      case e: ArgotUsageException =>
        println(e.message)
        sys.exit(1)
    }

  }
}
