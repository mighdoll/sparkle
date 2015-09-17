---
layout: default
title: Protocol Flow
---

Protocol Message Flows
---

##Typical Session Flow
1. Sparkle client sessions typically begin with the client sending [StreamRequest](StreamRequest.html) 
message to the server.
1. The server responds with a [Streams](Streams.html) message containing an initial set of data. 
1. The server subsequently sends [Update](Update.html) messages asynchronously to the client as more data is available. 


## Streams in a Session
The server initiates a stream by sending Streams message. 
Once created, streams can endure for the length of the session. 
Sessions are currently limited to the duration of a single websocket connection. 
Streams are addressable by id (streamId). 
StreamIds are unique within the session.

## Sparkle Message Layer
Sparkle messages are carried by the Distribution layer. 
The following sections describes the messages for realms that support sparkle data messaging. 
Other json protocols carried over the distribution layer can use their own message types.

## Client -> Server messages
There are two types of messages currently defined for sending messages to the service:  

  1. A [StreamRequest](StreamRequest.html) requests data from the service, and typically transforms it. 
  There are many transforms built-in.  Data servers can be extended to support application specific transforms.
  1. A [StreamControl](StreamControl.html) command to slow or stop the flow of messages on a stream. 

## Server -> Client messages
Three types of messages can be sent from server to client: 

  1. [Status](Status.html), 
  1. [Streams](Streams.html), 
  1. [Update](Update.html). 


## Multiplexing
Clients may send multiple requests without waiting for responses.
Servers deliver responses to outstanding client requests in arbitrary order. 
Servers may push messages to currently active streams at any time.

Because responses to outstanding requests may come out of order and may be intermixed
with server pushed messages, 
responses are labeled with the [requestId](Distribution.html#requestId) of the client request.
The [requestId](Distribution.html#requestId) enables clients to align responses with requests.

## Sparkle Client Responsibilities
Sparkle clients must maintain a mapping of active streamIds to client objects for the duration of the session.   
Sparkle clients must generate requestIds and maintain a table of outstanding requests to match responses.
To free client resources, sparkle clients should drop streams they are no longer using 
(or close the websocket session).
