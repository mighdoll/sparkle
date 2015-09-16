---
layout: default
title: Streaming
---
###Streaming responses
API requests typically receive responses containing a complete data series in a single json message. 
In addition, the API supports persistent, server pushed data streams. 

Many data series are _inherently streams_ - unending sequences of data representing new sensor values recorded, 
or new computed values based on data changing over time.
API clients can request that the server push ongoing updates to these data series. 

### Streaming Implementation status
Streaming is only partially supported in the current server and javascript client (as of September 15, 2015).
Stay tuned.
