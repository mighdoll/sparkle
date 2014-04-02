package nest.sparkle.util

import rx.lang.scala.Subject

/** PublishSubject is private in the 0.17.2 version of RxJava.  Remove this file once RX fixes that. */

/**
 * Copyright 2013 Netflix, Inc.
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

object PublishSubject {
  def apply[T](): PublishSubject[T] = new PublishSubject[T](rx.subjects.PublishSubject.create[T]())
}

class PublishSubject[T](val asJavaSubject: rx.subjects.PublishSubject[T]) extends Subject[T] 
