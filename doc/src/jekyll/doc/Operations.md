---
layout: default
title: Operational Issues
---

Operational Issues
---

### Load Balancing
Normally sparkle servers will be deployed behind a load balancer. 
In order to keep websocket sessions alive through some load balancers, 
e.g. in Amazon's elastic load balancer, the client or server need to send a packet periocially
to keep the socket alive through the load balancer.
The [Websocket spec](http://tools.ietf.org/html/rfc6455#section-5.5.2) includes 
a frame for Ping/Pong messages that can be used for keeping load balancers alive. 

### Sessions and Server Affinity
Sparkle sessions are tied to a websocket and cannot be resumed. 
In case of communication failure, the client must repeat all requests over 
a new websocket to register for subsequent server updates. 
The new websocket need not be on the same Sparkle server. 

Clients are normally expected to have only one websocket open at a time, sessions are not shared across websockets.

### Multiple Sparkle Servers
Sparkle requests include a realm, for potentially supporting a future proxy using 
the Sparkle json protocol distribution layer. 
A future proxy that could route to multiple backend Sparkle clusters, 
e.g. to direct CPU expensive computations onto different servers. 
A future proxy could also carry payloads for other json encoded protocols, 
allowing sharing of the same websocket with other services.
