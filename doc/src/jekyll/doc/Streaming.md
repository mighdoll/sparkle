---
layout: default
title: Streaming
---
###Streaming responses
API requests typically receive responses containing a complete data series in a single json message. 
In addition, the API supports persistent server pushed data streams. 

Many data series are inherently streams - unending sequences of data representing new sensor values recorded, 
or new computed values based on data changing over time.
API clients can request that the server push ongoing updates to series. 
