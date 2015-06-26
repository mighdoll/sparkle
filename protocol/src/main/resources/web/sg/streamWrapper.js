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

  /** pop the head of the stream, returniapng the value at the head of the stream */
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

