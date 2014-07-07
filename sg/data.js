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

define(["lib/when/monitor/console", "sg/request", "sg/util"], 
    function(_console, request) {

   // these need to be unique per session/socket..
   // (but it doesn't hurt to make them unique across all connectons)
   var nextRequestId = 0; 
   function requestId() {
     return nextRequestId++;
   }

/** Fetch data items from the server. Return a When that completes with the raw data 
  * array from the server-sent Streams message.  
  *
  *   transform:String  -- server transformation/summarization function to use
  *   transformParamsters:Object -- server transformation parameters
  *   dataSet:String -- 'path' to the column on the server
  *   column:String -- name of the column on the server
  */
  function columnRequestHttp(transform, transformParameters, dataSet, column) {
    var sourceSelector = [dataSet + "/" + column];
    return streamRequestHttp(transform, transformParameters, sourceSelector);
  }

/** Fetch data items from the server over a websocket connection. Returns the 
  * websocket. Calls a function with the raw data array that arrives
  * 
  *   transform:String  -- server transformation/summarization function to use
  *   transformParamsters:Object -- server transformation parameters
  *   dataSet:String -- 'path' to the column on the server
  *   column:String -- name of the column on the server
  *   dataFn:function -- called with the raw data from the server each time
  *     more data arrives.
  */
  function columnRequestSocket(transform, transformParameters, dataSet, column, 
      dataFn) {
    //console.log("columnRequestSocket called for:", dataSet, column, transform, transformParameters);
    var sourceSelector = [dataSet + "/" + column];

    var socket = new WebSocket("ws://localhost:3333/data"); // TODO don't hardcode data endpoint
    var receivedHead = false;
    socket.onmessage = function(messageEvent) {
      //console.log("columnRequestSocket got message:", messageEvent.data);
      if (!receivedHead) {
        var data = streamsResponse(messageEvent.data);
        receivedHead = true;
        dataFn(data);
      } else {
        var data = updateResponse(messageEvent.data);
        dataFn(data);
      }
    };

    socket.onopen = function() {
      var message = streamRequestMessage(sourceSelector, transform, transformParameters);
      //console.log("columnRequestSocket sending message:", message);
      socket.send(message);
    };
  }

/** Fetch data items from the server. Return a When that completes with the raw data 
  * array from the server-sent Streams message.  
  *
  *   transform:String  -- server transformation/summarization function to use
  *   transformParamsters:Object -- server transformation parameters
  *   sourceSelector:Array  -- one or more columnPaths or custom source selector objects
  */
  function streamRequestHttp(transform, transformParameters, sourceSelector) {
    var uri = "/v1/data"; 

    var message = streamRequestMessage(sourceSelector, transform, transformParameters);
    return request.jsonPost(uri, message).then(streamsResponse);
  }

  /** convert [Millis,Number] to [Date, Number] format */
  function millisToDates(jsonArray) {
    var data = jsonArray.map( function (row) { 
      var time = new Date(row[0]);
      return [time, row[1]]; 
    });
    return data;
  }

  /**
   * Get the columnPath names for named DataSet 
   * @param dataSetName
   */
  function getDataSetColumns(dataSetName) {
      var url = "/v1/columns/" + dataSetName;
      return request.jsonWhen(url);
  }
        
  /**
   * Get the child dataset names for named DataSet 
   * @param dataSetName
   */
  function getDataSetChildren(dataSetName) {
      var url = "/v1/datasets/" + dataSetName;
      return request.jsonWhen(url);
  }

  /** process a Streams message from the server */
  function streamsResponse(message) {
    var streamsMessage = JSON.parse(message);
    return streamsMessage.message.streams[0].data;
    // TODO: Handle more than one stream of data in response
  }

  /** process an Update message from the server */
  function updateResponse(message) {
    var updateMessage = JSON.parse(message);
//    console.log("updateMessage", updateMessage);
    return updateMessage.message.data;
    // TODO: match streamId (to handle multiple streams over the socket)
  }


  /** Return a StreamRequestMessage json string, suitable for sending a StreamRequest to the server */
  function streamRequestMessage(sourceSelector, transform, transformParameters) {
    /** Construct a StreamRequest to request a transform from the server */
    streamRequestObject = {
      sources: sourceSelector,  
      sendUpdates: false, // TODO fix for websockets
      requestMore: 1000,
      transform: transform,
      transformParameters: copyDefined(transformParameters)
    };

    var messageId = requestId();
    var distributionMessage = {
      requestId: messageId,
      realm: {
        name: "sparkle"
      },
      messageType: "StreamRequest",
      message: streamRequestObject,
      traceId: "trace-" + messageId
    };

    return JSON.stringify(distributionMessage);
  }


  return {
    columnRequestSocket:columnRequestSocket,
    columnRequestHttp:columnRequestHttp,
    streamRequestHttp:streamRequestHttp,
    millisToDates:millisToDates,
    getDataSetColumns:getDataSetColumns,
    getDataSetChildren:getDataSetChildren
  };

});
