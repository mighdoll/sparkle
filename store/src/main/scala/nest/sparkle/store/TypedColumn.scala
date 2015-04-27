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

package nest.sparkle.store
import scala.reflect.runtime.universe._

/** Extractor that matches the dynamic type of a column and returns a provided static type if it matches */
abstract class TypedColumn[T: TypeTag, U: TypeTag] {
  def unapply(column: Column[_, _]): Option[Column[T, U]] = {
    (column.keyType.tpe, column.valueType.tpe) match {
      case (keyTag, valueTag) =>   // SCALA, is this right? I think probably needs to <:< or =:=
        val castColumn = column.asInstanceOf[Column[T, U]]
        Some(castColumn)
      case _ => None
    }
  }
}

object LongDoubleColumn extends TypedColumn[Long, Double]
object LongLongColumn extends TypedColumn[Long, Long]

