---
layout: default
title: Stream Message
---

Streams 
---
A Streams message is sent to the client when the server is starting 
one or more data streams in response to a client [StreamRequest](StreamRequest.html). 
The Streams message lists the streams that the server will send.
Optionally and typically, the streams message also includes a first set of stream data items for those streams. 

    { requestId: 2,                        // (required) references the client request
      messageType: "Streams",
      message: {
        streams: [
          { streamId: <nonnegative int53>, // id for the stream, unique in the session
            metadata: {varies},            // (optional) additional info about the stream
            streamType: "<streamType>",    // describes shape of the data stream elements
            data: [<stream item>, ...],    // (optional) initial data for this stream 
            end: true,                     // (optional) indicates stream is complete 
          },
          …
        ]
      }
    }

#### streamId
`streamId` is a server generated, numeric identifier for the stream. 
`streamId` is a positive integer small enough to fit inside a javscript number.
`streamId` is guaranteed by the server to be unique in this session.
Typically the server will generate stream ids with a simple counter within the session, but this is not required.

#### metadata

`metadata` is an optional field used by data transforms to attach additional information about a stream.
Metadata must be a json object. 

In cases where a transform produces multiple streams, 
`metadata` provides a way for transfomrs identify which stream 

Note that metadata is sent only with the start of the stream, not with each Update. 

#### streamType
`streamType` is a string that describes the structure of stream items that will be sent over the stream.
Supported stream types are: "KeyValue" and "Value".

#### data
Data contains the actual stream items. 
See [Data Series] for details.

####end 
`end` indicates that the stream ends with data provided in this message. 
Servers should always set `end` to true for messages delivered over http. 
Over websockets, `end` is assumed to be false if it is not present.
