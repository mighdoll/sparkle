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

import scala.reflect.ManifestFactory
import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

object Instance {

  /** Create a class instance by calling its (hopefully only) constructor */
  def byClass[T](clazz: java.lang.Class[T])(args:AnyRef*): T = {
    val constructor = clazz.getConstructors()(0)
    constructor.newInstance(args:_*).asInstanceOf[T]
  }

  /** Create a class instance, given a fully qualified class name
   *  and arguments to its constructor.  */
  def byName[T](className: String)(args:AnyRef*): T = {
    val classLoader = getClass.getClassLoader
    val clazz = Class.forName(className, false, classLoader).asInstanceOf[Class[T]]
    byClass(clazz)(args:_*)
  }

  /** Return the singleton object for the passed object name
    * 
    * @param className fully qualified name of the object
    * @tparam T The type to return (parent class or trait)
    * @return the object
    */
  def objectByClassName[T](className: String): T = {
    val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)
    val module = runtimeMirror.staticModule(className)
    val clazz = runtimeMirror.reflectModule(module)
    val obj = clazz.instance.asInstanceOf[T]
    obj
  }

  /** Return the TypeTag for the given fully qualified class name */
  def typeTagByClassName[T](className: String): TypeTag[T] = {
    val classLoader = getClass.getClassLoader
    val runtimeMirror = universe.runtimeMirror(classLoader)
    val clazz = Class.forName(className, false, classLoader).asInstanceOf[Class[T]]
    val typeTag = universe.internal.manifestToTypeTag(runtimeMirror, ManifestFactory.classType(clazz)).asInstanceOf[TypeTag[T]]
    typeTag
  }
}
