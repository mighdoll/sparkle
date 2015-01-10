define(["lib/when/when"], 
    function(when) {

  /** connect to a websocket and return an enhanced websocket wrapper */
  function connect(url) {
    var socket = new WebSocket(url);
    socket.onopen = function(o) {
      console.log("socket open", o); 
    };
    socket.onerror = function(e) {
      console.log("socket error", e); 
    };
    socket.onclose = function(s) {
      console.log("socket close", s); 
    };
    socket.message = function(m) {
      console.log("socket message", m); 
    };

    return socket;
  }

  return {
    connect:connect
  };

});
