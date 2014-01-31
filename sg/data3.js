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

/** Fetch data points from the server.  Return a When that completes with an array:[Date, Number].
  *
  * params {
  *   ?.domain:[Date, Date] -- time range to fetch
  *   ?.extraBeforeAfter:Boolean -- server should return an extra point before and after the domain
  *   ?.approxMaxPoints:Number -- server should return about this many points
  *   ?.summary:String  -- server summarization function to use
  *   ?.filter:[String] -- only return points with these values
  */
return function(setName, column, params) {
  var uri = "/v1/data"; 

  return request.jsonPost(uri, streamRequestMessage()).then(streamsResponse);

  /** process a Streams message from the server */
  function streamsResponse(message) {
    var streamsMessage = JSON.parse(message);
    return reformat(streamsMessage.message.streams[0].data);
  }

  /** convert to [Date,Number] format */
  function reformat(jsonArray) {
    var data = jsonArray.map( function (row) { 
      var time = new Date(row[0]);
      return [time, row[1]]; 
    });
    return data;
  }

  /** Construct a StreamRequest to request a transform from the server */
  function streamRequest() {
    var start = params.domain ? params.domain[0].getTime() : null,
        end = params.domain ? params.domain[1].getTime() : null;

    return {
      sendUpdates:false,  // optional
      itemLimit: 0,       // optional
      sources: [setName + "/" + column],
      transform: "SummarizeMax",
      transformParameters: {
        maxResults: params.approxMaxPoints,
        start: start,
        end: end,
        edgeExtra: params.extraBeforeAfter 
      }
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
}

});
