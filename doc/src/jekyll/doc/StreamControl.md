---
layout: default
title: Stream Control Message
---

StreamControl
---
Clients may send a StreamControl message to servers to limit the flow of data over a stream. 
See [Streams](Streams.html) for details. 
The server will respond with a Status message. 

    { requestId: <nonnegative int53>,  
      messageType: "StreamControl",
      message: {
        streamId: <nonnegative int53>,
        request: <nonnegative int53>   // increase the limit of data items by this amount 
      }
    }

## StreamId
The `streamId` must match the streamId from a previously received [Streams](Streams.html) message.

## Request
{% markdown sparkle/requestParam.md %}

If the request returns multiple streams, the requestMore value applies to each stream independently. 

