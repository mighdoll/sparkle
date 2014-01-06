/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.graph

import java.io.BufferedReader
import scala.collection.mutable
import java.io.IOException
import scala.collection.Iterator

case class TsvReader(reader: BufferedReader) {
  
  /** Parse a tsv file into an array of strings per line */
  def lines(): Iterator[Array[String]] = {
    linesIterator(reader).map {_.split('\t') }
  }
  
  /** return an iterator over the lines from this buffered reader */
  def linesIterator(reader:BufferedReader):Iterator[String] = {
    val iterator = new Iterator[String]() {
      var nextLine:String = reader.readLine()
      
      def hasNext:Boolean = (nextLine != null)
      def next():String = {
        val line = nextLine        
        try {
          nextLine = reader.readLine()       
        } catch {
          case e:Exception =>
            Console.err.println(s"error reading file $e")
            nextLine = null
        }
        line
      }
    }    
    iterator
  }
}
