---
layout: default
title: Errors
---

## Request Success

### 0 - "OK"
The request was successful. No data is transferred with an OK response. 
Reference to client requestId is required.

A typical successful response from a StreamRequest will send a Streams message containing one or more streams of data (not a Status OK). 

## Session Status

### 800 - "session is now invalid. re-authenticate"
Client should close the session/socket, apply for new authentication credentials (how to do get credentials is outside the scope of Sparkle), and then it may retry the request with new credentials. Typically this status references the session, not any particular stream or request.

### 801 - "relocate"
Client should close the session/socket, request new service routing info (how to get routing is outside the scope of Sparkle), 
and then the client may retry the request at a likely different service. 
Typically this status references the session, not any particular stream or request.

## Request Errors
The following messages are sent in response to a client request (and always include a requestId property). 

### 600 - "dataSet <dataSetName> not found"

### 601 - "column <columnName> not found in dataset"

### 602 - "transform <transformName> not supported"

### 603 - "parameter error in transform request"

### 604 - "parameter error in source selector"
A custom source selector (Custom Source Selectors) was specified but the parameters to that selector were not valid.

### 605 - "source selector not found"
A custom source selector (Custom Source Selectors) was specified but the specified selector is not available on this server. Clients should not retry as subsequent requests will also fail.

### 606 - "request format error"
A client sent message was structured incorrectly, 
e.g. a missing required property, or an array property where an object property was expected. 
Servers should provide some explanation in the response text as to which part of the message was incorrect. 
Clients should not retry as subsequent requests will also fail.

### 610 - "authentication malformed"
Presented authentication were not in the correct format. 
Clients should not retry, as subsequent requests will also fail. 

### 611 - "authentication failed"
Presented authentication credentials are invalid. 
Clients should not retry, as subsequent requests will also fail. 

### 612 - "authentication missing"
Presented authentication credentials are missing and are required by this service. 
Clients should not retry, as subsequent requests will also fail. 

### 613 - "authorization failed"
Presented authentication credentials are valid, 
but the user is not authorized to access the requested source columnPath or perform the requested transform. 
Clients should not retry, as subsequent requests will also fail.

### Redirects
600, 601, and 602 status messages may optionally include a redirect property containing a target url on a different server. Clients should retry the request at the target url. 

## Other Errors

### 750 - "server internal error"
The server has failed unexpectedly. 
References a request or a stream. 
No further response or update on the stream should be expected.
The client should submit a new request and expect the response will come over a new stream.



