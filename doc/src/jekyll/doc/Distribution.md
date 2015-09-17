---
layout: default
title: Protocol Envelopes
---


Distribution Layer
---
The data protocol wraps sparkle messages in a distribution layer json envelope. 
The distribution layer includes addressing to carry messages and responses through potential server proxies, 
as well as some debugging and tracing tags.

## Json API Over Websockets 
The server allows carrying api requests over HTTP or Websockets. 
Websockets is typicaly preferable, 
for efficiency and to enable server pushed streams.

### Framing
Each sparkle message is carried in a single websocket message. 
i.e. Sparkle API json message are not split across websocket data frames.

###Message Ordering
Messages from a single client or server sender arrive at the recipient in the order that they are sent. 
However servers are not required to respond to requests in the order that they are received. 
 
## Distribution Layer 
Messages to and from the server look like this:
 
    { requestId: <nonnegative int53>, // (optional) client provided request id
      traceId: "<string>"             // (optional) tracing id for debugging
      realm: {                        // (optional) realm for auth/partitioned backend
        name: "<string>",             // scope for this realm
        id: "<string>",               // (optional) user id (to server only)
        auth: "authToken"             // (optional) authentication token (to server only)
      },
      messageType: "<messageType>",   // type of message (scoped to the realm)  
      message: { <varies> },          // message contents (may be empty)
    }

#### requestId  <a name='requestId'/>
RequestIds are optional client supplied tags for requests, used to enable response multiplexing 
over websockets.
Servers copy the requestId into the server response message. 
No more than one server response is sent to a request. 
However, the server may respond to outstanding requests in any order. 
RequestIds allow the client to match responses with requests.

RequestIds are non-negative integers less than 2^53. 
RequestIds must not be reused by a client while a response is outstanding over a given websocket.
Clients are advised to use an incrementing counter for generating requestIds. 

Clients are recommended to use requestIds particularly when using websockets. 

#### traceId
TraceId is a string intended for operational debugging. 
The server will record this traceId with internal logging, 
and propagate the traceId to subsidiary requests to other servers.  

#### realm
All protocol messages are optionally scoped by realm. 
Realm provides a scope for authentication/authorization and for future sparkle proxies to route to multiple backend services. 

#### name
Realm name uniquely identifies the scope of this realm. 
Name is a case sensitive string containing letters, numbers and/or underscores. 
Realm name is required when a realm object is provided. 

#### id
Realm id holds a client or user id. 
Servers will typically authorize users to access only a portion of the data that they hold. 
Servers providing addressable data may scope the data and reuse addresses for each client id, the addressing needn't be global. 
id is only sent from a client to a server, never from a server to a protocol client.

#### auth
Realm auth contains an arbitrary json string for authenticating a realm id. Typically this is a security token used to authenticate an id within the realm. auth is only sent to the server, never from the server to a protocol client.

## MessageType
MessageType is a case sensitive string containing letters, numbers and/or underscores. 
MessageType tags the type 

## Message
Message is a json object that holds the payload contents. 

Messages are tagged with messageType to enable recipient protocol engines to partially interpret the message without examining the message contents. Message types are case sensitive strings containing letters, numbers and/or underscores. 

Message may be an empty json object {}, e.g. for messages that are distinguished solely by messageType.


