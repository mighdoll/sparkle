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

define([], function() {

/** Return a stream wrapper on a provided array of pairs.  The array should have two
 * elements, the first element is a key, the second is the value.  The array should
 * be sorted by key.  */
return function(data) {
  var position = 0;

  /** return the time at the head of the stream, or undefined if the stream is finished */
  function peekKey() {
    if (position >= data.length) {
      return undefined;
    }
    return data[position][0];
  }

  /** pop the head of the stream, returning the value at the head of the stream */
  function popValue() {
    if (position >= data.length) {
      return undefined;
    }
    var result = data[position][1];
    position += 1;
    return result;
  }

  /** pop the head of the stream, returning the key or undefined if the stream is empty */
  function popIfKey(key) {
    if (peekKey() == key) {
      return popValue();
    } else {
      return undefined;
    }
  }

  return {
    peekKey: peekKey,
    popIfKey: popIfKey
  };
};

});

