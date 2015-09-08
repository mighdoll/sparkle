---
layout: default
title: Requests
---
##Requests
There are two types of messages currently defined for sending messages to the service:  
A StreamRequest requests data from the service, and typically transforms it. There are many transforms built-in. The set of transforms is extensible per application. 
A StreamControl command to slow or stop the flow of messages on a stream. 

###StreamRequest
A stream request asks the server to transform some source events and return the result as a sparkle network stream. StreamRequests typically do some calculation on one or more source columns within a hierarchy of dataSets and return a Stream message as a result. 

    { requestId: <nonnegative int53>,   // client specified request id
      messageType: "StreamRequest",
      message: {
        sendUpdates: false, // (optional, true by default) if false, the server 
                            // will not send Update messages (only Streams or Error)
        request: <non-negative int53>,   // (optional, 0 by default) flow control
        sources: [<sourceSelector>, …], // columnPath or custom source selector 
        transform: "<transformName>",
        transformParameters: { <parameters to this transformation> } 
      }
    }

###RequestMore 
RequestMore is intended for flow control / backpressure. RequestMore sets a limit on the total number of stream items that the server is permitted to send over the lifetime of each stream sent in response to this request. The server will not send more items than the sum of the RequestMore values sent by the client in the StreamRequest and subsequent StreamControl messages.

Clients may regulate the flow of items they will receive over a stream by setting requestMore to a low value, and then periodically sending a StreamControl message with a requestMore value set. Servers should generally buffer (or be willing to regenerate) items that exceed the current requestMore limit in the stream (although this is stream specific). 

A requestMore value of 0 means that the server can always send data whenever it is available. If the request returns multiple streams, the requestMore value applies to each stream independently. 

###SendUpdates
SendUpdates:false limits the server to sending a single response to the StreamRequest. 
Transform implementations are advised to return a 'complete snapshot' of current data in response to SendUpdates:false requests. 
For example, a StreamRequest that asks for a slice of the last 10 minutes of time series data 
should return all the data from 10 minutes ago until the present time in a single response. 
In contrast, a SendUpdates:true request would be expected to continue sending Update message as time progresses forwards, 
and could in unlikely cases spread the initial response over multiple Update messages.
