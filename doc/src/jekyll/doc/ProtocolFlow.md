---
layout: default
title: Protocol Flow
---

Protocol Message Flows
---

##Typical Session Flow
Sparkle client sessions typically begin with the client sending [StreamRequest](StreamRequest.html) 
message to the server.
The server responds with a [Streams](Streams.html) message containing an initial set of data. 
The server subsequently sends [Update](Update.html) messages asynchronously to the client as more data is available. 

Clients may send multiple requests at once if they choose. 
Servers may deliver responses to client requests in any order. 
To enable clients to align responses with requests, responses are labeled with the id of the request.

## Streams in a Session
The server initiates a stream by sending Streams message. Once created, streams can endure for the length of the session. Sessions are bound the duration of a single websocket connection. Streams are addressable by id (streamId). StreamIds are unique within the session.

## Sparkle Message Layer
Sparkle messages are carried by the Distribution layer. 
The following sections describes the messages for realms that support sparkle data messaging. 
Other json protocols carried over the distribution layer can use their own message types.

## Client -> Server messages
There are two types of messages currently defined for sending messages to the service:  
A [StreamRequest](StreamRequest.html) requests data from the service, and typically transforms it. There are many transforms built-in. The set of transforms is extensible per application. 
A StreamControl command to slow or stop the flow of messages on a stream. 

## Server -> Client messages
Three types of messages can be sent from server to client: 
[Status](Status.html), [Streams](Streams.html), and [Update](Update.html). 
Messages that are responses to client requests are tagged with the requestId of the client message. 
Note that responses to outstanding requests may come out of order and may be intermixed
with server pushed messages.

## Sparkle Client Responsibilities
Sparkle clients must maintain a mapping of active streamIds to client objects for the duration of the session.   
Sparkle clients must generate requestIds and maintain a table of outstanding requests to match responses.
To free client resources, sparkle clients should drop streams they are no longer using 
(or close the websocket session).
