define(["lib/when/monitor/console", "sg/request", "sg/util", "lib/when/when"],
    function(_console, request, _util, when) {

   function webSocketUriWhen(serverConfigWhen) {
     return serverConfigWhen.then(function(serverConfig) {
       var host = parseUrl(document.documentURL).hostname;
       var port = serverConfig.webSocketPort;
       return "ws://" + host + ":" + port + "/data";
     }).otherwise(rethrow);
   }

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
  *   serverConfigWhen - when.js promise with server config, like ports
  *   transform:String  -- server transformation/summarization function to use
  *   transformParamsters:Object -- server transformation parameters
  *   dataSet:String -- 'path' to the column on the server
  *   column:String -- name of the column on the server
  *   dataFn:function -- called with the raw data from the server each time
  *     more data arrives.
  *   errorFn:function -- called if there's an error from the server
  */
  function columnRequestSocket(serverConfigWhen, transform, transformParameters, dataSet, column,
      dataFn, errorFn) {
    //console.log("columnRequestSocket called for:", dataSet, column, transform, transformParameters);
    var sourceSelector = [dataSet + "/" + column];

    webSocketUriWhen(serverConfigWhen).then( function(uri) {
      var socket = new WebSocket(uri);
      var receivedHead = false;

      socket.onmessage = function(messageEvent) {
        //console.log("columnRequestSocket got message:", messageEvent.data);
        if (!receivedHead) {
          receivedHead = true;
          headResponse(messageEvent.data, dataFn, errorFn);
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
    }).otherwise(rethrow);


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

  /** return a When with the results from the head response from the server. The When
    * resolves successfully with the data from a Streams message, or fails with the
    * error from the server Status message */
  function streamsResponse(message) {
    var deferred = when.defer(),
        promise = deferred.promise;

    function dataReceived(data) {
      deferred.resolve(data);
    }

    function errorReceived(err) {
      deferred.reject(err);
    }

    headResponse(message, dataReceived, errorReceived);

    return promise;
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

  /** handle the first response coming back on a server stream */
  function headResponse(message, dataFn, errorFn) {
    var streamsMessage = JSON.parse(message);
    var messageType = streamsMessage.messageType.toLowerCase();
    if (messageType == 'status') {
      errorFn(streamsMessage.message);
    } else if (messageType == 'streams') {
      var streams = streamsMessage.message.streams;
      if (streams.length == 0) {
        dataFn([]);
      } else {
        // TODO: Handle more than one stream of data in response
        dataFn(streams[0].data);
      }
    }
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
      emitEmptyPeriods: false,
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
