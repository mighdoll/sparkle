define(["jquery", "sg/data"], function($, data) {
  'use strict'; 

  describe("websocket", function () {
    it("should get an echo from the echo server", function (done) {
      var socket = new WebSocket("ws://localhost:3333/echo");
      socket.onmessage = function(m) {
        console.log("got message");
        expect(m.data).toEqual("hello");
        done();
      };

      socket.onopen = function(o) {
        console.log("socket open", o); 
        socket.send("hello");
      };
    });

    it("should get the right data from a Raw request", function (done) {
      function received(data) {
        expect(data.length).toEqual(2751);
        done();
      }
      var parameters = {};
      data.columnRequestSocket("Raw", parameters, "epochs", "p99", received);
    });

  });

});
