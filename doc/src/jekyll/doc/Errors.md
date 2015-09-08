---
layout: default
title: Errors
---

Errors
---
Errors are carried inside [Status Messages](Status.html) sent from client to server.
Status messages are typically be sent in response to requests, or asynchronously pushed by the server.

## Request Success

### 0 - "OK" <a name="0"></a> 
The request was successful. No data is transferred with an OK response. 
Reference to client requestId is required.

A typical successful response from a StreamRequest will send a Streams message containing one or more streams of data (not a Status OK). 

## Session Status

### 800 - "session invalid" <a name="800"></a> 
Client should close the session/socket, apply for new authentication credentials (how to do get credentials is outside the scope of Sparkle), and then it may retry the request with new credentials. Typically this status references the session, not any particular stream or request.

### 801 - "relocate" <a name="801"></a> 
Client should close the session/socket, request new service routing info (how to get routing is outside the scope of Sparkle), 
and then the client may retry the request at a likely different service. 
Typically this status references the session, not any particular stream or request.

## Request Errors
The following messages are sent in response to a client request (and always include a requestId property). 

### 600 - "dataSet <dataSetName> not found" <a name="600"></a> 

### 601 - "column <columnName> not found in dataset" <a name="601"></a> 

### 602 - "transform <transformName> not supported" <a name="602"></a> 

### 603 - "parameter error in transform request" <a name="603"></a> 

### 604 - "parameter error in source selector" <a name="604"></a> 
A custom source selector (Custom Source Selectors) was specified but the parameters to that selector were not valid.

### 605 - "source selector not found" <a name="605"></a> 
A custom source selector (Custom Source Selectors) was specified but the specified selector is not available on this server. Clients should not retry as subsequent requests will also fail.

### 606 - "request format error" <a name="606"></a> 
A client sent message was structured incorrectly, 
e.g. a missing required property, or an array property where an object property was expected. 
Servers should provide some explanation in the response text as to which part of the message was incorrect. 
Clients should not retry as subsequent requests will also fail.

### 610 - "authentication malformed" <a name="610"></a> 
Presented authentication were not in the correct format. 
Clients should not retry, as subsequent requests will also fail. 

### 611 - "authentication failed" <a name="611"></a> 
Presented authentication credentials are invalid. 
Clients should not retry, as subsequent requests will also fail. 

### 612 - "authentication missing" <a name="612"></a> 
Presented authentication credentials are missing and are required by this service. 
Clients should not retry, as subsequent requests will also fail. 

### 613 - "authorization failed" <a name="613"></a> 
Presented authentication credentials are valid, 
but the user is not authorized to access the requested source columnPath or perform the requested transform. 
Clients should not retry, as subsequent requests will also fail.

## Redirects <a name="redirects"></a> 
600, 601, and 602 status messages may optionally include a redirect property containing a target url on a different server. Clients should retry the request at the target url. 

## Other Errors

### 750 - "server internal error" <a name="750"></a> 
The server has failed unexpectedly. 
References a request or a stream. 
No further response or update on the stream should be expected.
The client should submit a new request and expect the response will come over a new stream.



