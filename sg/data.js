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

define(["jslib/when/monitor/console", "sg/request"], 
    function(_console, request) {

/** Fetch data points from the server.  Return a When that completes with the raw data 
  * array from the server sent Streams message.  
  *
  * Utility functions are available to convert incoming streams to convenient forms:
  *   * [Milliseconds,Value] to [Date, Value].  millisToDates()
  *   * [String,Value] to {name1: value1, name2: value2}. toObject()
  *
  * params: 
  *   .transform:String  -- server transformation/summarization function to use
  *   ?.domain:[Date, Date] -- time range to fetch
  *   ?.edgeExtra:Boolean -- server should return an extra point before and after the domain
  *   ?.maxResults:Number -- server should return no more than this many points
  */
  var returnFn = function(setName, column, params) {
    var uri = "/v1/data"; 

    return request.jsonPost(uri, streamRequestMessage()).then(streamsResponse);

    /** process a Streams message from the server */
    function streamsResponse(message) {
      var streamsMessage = JSON.parse(message);
      return streamsMessage.message.streams[0].data;
    }

    /** Construct a StreamRequest to request a transform from the server */
    function streamRequest() {
      // we send millis over the wire, so convert from the Date objects we use locally
      var start = params.domain ? params.domain[0].getTime() : null,
          end = params.domain ? params.domain[1].getTime() : null;

      var transformParameters = {
          maxResults: params.maxResults,
          start: start,
          end: end,
          edgeExtra: params.edgeExtra
      };

      return {
//        sendUpdates:false,  // for websockets
//        itemLimit: 0,       // for websockets
        sources: [setName + "/" + column],  
        transform: params.transform,
        transformParameters: copyDefined(transformParameters)
      };
    }

    /** Return a StreamRequestMessage json string, suitable for sending a StreamRequest to the server */
    function streamRequestMessage() {
      var distributionMessage = {
        requestId: 11,
        realm: "foo",
        messageType: "StreamRequest",
        message: streamRequest(),
        traceId: "13"
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

  /** convert [String,Value] to {name1: value1, name2: value2}.  */
  function toObject(jsonArray) {
    var result = {};
    jsonArray[0].forEach( function(element) {
      result[element[0]] = element[1];
    });
    return result;
  }
        
  /**
   * Get the full columnPaths for named DataSet 
   * @param dataSetName
   */
  function getDataSetColumns(dataSetName) {
      var url = "/v1/columns/" + dataSetName;
      return request.jsonWhen(url);
  }

  returnFn.millisToDates = millisToDates;
  returnFn.toObject = toObject;
  returnFn.getDataSetColumns = getDataSetColumns;
      
  return returnFn;

});
