---
layout: default
title: API Protocol Introduction
---

API Protocol Introduction
---

The Sparkle data protocol is a simple json formatted protocol intended to carry streams of data over a websocket to visualization clients. 

Key features include:

* Delivery of [data series](DataSeries.html) to data clients.
* Requesting server side [data transformation](DataTransform.html), e.g. to aggregate time series data into monthly averages.
* Push style data [streaming](Streaming.html) from server to client.
* Detailed [error](Errors.html) reporting.
* Delivery over http and websockets.

Sparkle Data Layer <a name="SparkleDataLayer"></a>
---

The Sparkle Data layer messages manage the core control and delivery of tranformed
[data series](DataSeries.html) from the server.

* [Selecting](Selecting.html) data on the server.
* [Transforming](DataTransform.html) data on the server, e.g. aggregation.
* [Stream control](Streaming.html), for controlling flows of data that continue indefinitely.


Json API Protocol Structure
---
Requests may be sent to the server over the data websocket or as HTTP POST messages to the "/data" endpoint. 

Requests and responses use a common json message format. 
The json fields in a message are divided into three layers[^OSI]. 

* A [Distribution layer](Distribution.html) is responsible to for delivering messages 
over multiplexed connections to/from potentially multiple servers.
* A Message layer tags messages by type.
* A [Sparkle Data layer](#SparkleDataLayer) allows selecting and transforming server hosted data, 
and delivers data series in streams to clients. 

[^OSI]: These json api protocol layers are at the application layer in OSI terms.

Example API Request and Response
---

Here's an example request:

    { requestId: 2,                                     # Distribution layer: id for multiplexed requests 
      realm: {name: "perfMetrics"},                     # . routing information for multiple servers
      messageType:"StreamRequest",                      # . data layer message type 
      message: {                                        # 
        sources: ["myTestRun/serverX/latency.p99"],     # Data layer: select data source
        transform: "reduceMax",                         # Data layer transform: apply server side Max aggregation
        transformParameters: {                          #  
          ranges: [ { start: 1388534400000} ],          # . select range of data
          intoDurationParts: "1 day"                    # . aggregate time series by day
        }
      }
    }

And here's an example response: a Streams message containing a key value data stream:

    { requestId: 2,                                     # Distribution layer: tag request/responses with ids        
      messageType: "Streams",                           # Message layer: response operates on the data layer
      message: {                                        # Data layer: 
        streams: [                                      #  . response contains one stream
          { streamId: 12,                               #  . streams id (to support subsequent pushed updates) 
            metadata: "myTestRun/serverX/latency.p99",  #  . tag to help client match data stream to request
            streamType: "KeyValue",                     #  . data contains keys and values
            data: [                                     #  
              [1388534400000, [1.0]],                   #  . data items
              [1388620800000, [1.2]],
              [1388707200000, [1.3]],
              ...
            ]
          }
        ]
      }
    }

---
