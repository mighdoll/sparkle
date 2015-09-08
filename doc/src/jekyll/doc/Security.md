---
layout: default
title: Security
---

##Security
Sparkle connections will typically be carried over TLS encrypted websockets. 
As such, Sparkle inherits the security properties of TLS and websockets. 

###Confidentiality, Integrity, Server Authentication 
TLS for Sparkle will normally be configured for certificate based server authentication, 
as well as end to end encryption of the websocket handshake and data streams. 
Sparkle inherits the confidentiality, integrity and server authentication features from TLS.

### Client Authentication  
The sparkle data server implementation provides application hooks for authorizing protocol 
clients on a per request basis. Unauthorized requests return [613 - "authorization failed"](Errors.html#613).

Client authentication is largely outside the scope of the Sparkle data protocol. 
Nonetheless, here's some advice on how to do client authentication:

#### Option 1: Use http headers.
Like other http/websocket servers, Sparkle servers may use http headers for client authentication. 
A custom header is recommended. 

Browser clients should be careful not to rely on headers that are sent by the browser user agent automatically, 
such as session cookies or basic authentication. 
Even though cookie based session authentication over TLS is considered safe in the http context, it is not safe for websockets. 
It's not safe because websocket requests are not limited by the browser same origin policy. 
A malicious page can trigger requests to a protected websocket and the browser will forward cookies (or basic auth headers), 
handing the malicious web page full control of an authenticated websocket.  

To defend against this sort of websocket CSRF attack, 
the server can verify that the Origin header also sent by the browser matches the expected page. 
This defends against the attack, but requires potentially awkward hostname bookkeeping on the server side 
especially in the typical case where the html/js host page is on a different host from the web socket data page.  

Browser clients are advised to authenticate with a custom http header. 
The sparkle sample client implementation will use 'X-sparkle-authentication'. 
Custom headers are not vulnerable to the websocket CSRF attack, 
because they will not be automatically forwarded by the browser user agent. 

### Option 2: TLS based client authentication. 

### Option 3:  Authentication inside the json protocol.
Sparkle API requests messages may carry optional authentication parameters within the json messages sent to the server.


