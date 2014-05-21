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

define(["lib/when/monitor/console", "sg/request"], 
    function(_console, request) {

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
  function columnRequest(transform, transformParameters, dataSet, column) {
    var sourceSelector = [dataSet + "/" + column];
    return streamRequest(transform, transformParameters, sourceSelector);
  }

/** Fetch data items from the server. Return a When that completes with the raw data 
  * array from the server-sent Streams message.  
  *
  *   transform:String  -- server transformation/summarization function to use
  *   transformParamsters:Object -- server transformation parameters
  *   sourceSelector:Array  -- one or more columnPaths or custom source selector objects
  */
  function streamRequest(transform, transformParameters, sourceSelector) {
    var uri = "/v1/data"; 

    return request.jsonPost(uri, streamRequestMessage()).then(streamsResponse);

    /** process a Streams message from the server */
    function streamsResponse(message) {
      var streamsMessage = JSON.parse(message);
      return streamsMessage.message.streams[0].data;
      // TODO: Handle more than one stream of data in response
    }

    /** Construct a StreamRequest to request a transform from the server */
    function createStreamRequest() {
      return {
        sources: sourceSelector,  
        sendUpdates: false, // TODO fix for websockets
//        itemLimit: 0,     // for websockets
        requestMore: 1000,
        transform: transform,
        transformParameters: copyDefined(transformParameters)
      };
    }

    /** Return a StreamRequestMessage json string, suitable for sending a StreamRequest to the server */
    function streamRequestMessage() {
      var messageId = requestId();
      var distributionMessage = {
        requestId: messageId,
        realm: "sparkle",
        messageType: "StreamRequest",
        message: createStreamRequest(),
        traceId: "trace-" + messageId
      };
      return JSON.stringify(distributionMessage);
    }
  };

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

  return {
    columnRequest:columnRequest,
    streamRequest:streamRequest,
    millisToDates:millisToDates,
    getDataSetColumns:getDataSetColumns,
    getDataSetChildren:getDataSetChildren
  };

});
