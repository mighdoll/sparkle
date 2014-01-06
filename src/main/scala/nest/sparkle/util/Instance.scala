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

package nest.sparkle.util

object Instance {

  /** Create an class instance by calling its (hopefully only) constructor */
  def byClass[T](clazz: java.lang.Class[T])(args:AnyRef*): T = {
    val constructor = clazz.getConstructors()(0)
    return constructor.newInstance(args:_*).asInstanceOf[T]
  }

  /** Create an class instance, given a class fully qualified class name 
   *  and arguments to its constructor.  */
  def byName[T](named: String)(args:AnyRef*): T = {
    val classLoader = this.getClass().getClassLoader()
    val clazz = Class.forName(named, false, classLoader).asInstanceOf[Class[T]]
    byClass(clazz)(args:_*)
  }
  
}
