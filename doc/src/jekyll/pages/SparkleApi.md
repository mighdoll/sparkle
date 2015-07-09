---
layout: default
title: Sparkle Data API
---
# API Access to Server Data Series
The sparkle API enables clients to request data series from a server. 
API requests and responses are sent in JSON format[^binary] and delivered over http or websockets. 

## Overview
[Data Series](DataSeries.html) are ordered sequences of data items. 
Clients receive [Data Series](DataSeries.html) in response to their requests.
See [Data Series](DataSeries.html) for details of how data series are defined and encoded.

API requests typically receive responses containing a complete data series in a single json message. 
The API also supports persistent server pushed data streams. See [Streaming](Streaming.html).

The API includes a [selection](Selecting.html) mechanism for choosing data series
so that API clients can choose to have isolated from the current of data storage. 
The selection mechanism is pluggable, allowing servers to implement application specific selection.
See [Selection](Selecting.html).

The API exposes an extensible set of server data transformations. 
While clients can simply request raw data from a data series, server hosted data 
transformation typically reduces the amount of data that needs to be transmitted over the wire,
improving user latency. 
Server hosted transformations also enable consistency across client implementations.
See [Data Transformations](DataTransform.html).

Prebuilt data transformations for aggregation of time series data are included in sparkle,
and a plugin api allows building custom server hosted data transformations.
The API and prebuilt transformations are intended to support data visualization clients
that display charts and graphs.
A javascript client using the api to display live charts is included in sparkle.
See [SparkleJS](SparkleJs.html).

The API messaging layer also includes facilities for [Security](Security.html), message
routing, application tracing, and debugging. See [Envelopes](ProtocolEnvelopes.html).

[Errors](Errors.html) returned by the API are specific, tuned to the needs of a data series api.
Specific errors give API clients more guidance during development, 
and specific errors enable clients to implement custom behaviors based on the specific
error condition.

## Sample Visualization
(tbd)

## Sample JSON request and response
A sample javascript API client is available in [github](http://mighdoll.github.io/sparkle).

Here's a sample request and response:
Here's an example request:

    { requestId: 2,  
      realm: "perfMetrics",
      messageType:"StreamRequest",
      message: {
        sources: ["myTestRun/serverX/latency.p99"],
        transform: "summarizeMax",
        transformParameters: {
          ranges: [ { start: 1293840000000 } ],
          partByDuration: "1 day"
        }
      }
    }

The server responds:

    { requestId: 2,
      messageType: "Streams",
      message: {
        streams: [
          { streamId: 12,
            streamType: "KeyValue",
            data: [
              [1293840000000, 1.0],
              [1370044800000, 1.2],
              [1377993600000, 1.3],
              ...
            ]
          }
        ]
      }
    }


---
[^binary]: An optional binary encoding of the API is likely in a future version.

