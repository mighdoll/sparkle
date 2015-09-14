---
layout: default
title: Stream Request Message
---

StreamRequest
----
A stream request message requests data from the server, 
optionally specifing server transformation of the data series. 
The results are returned as one or more data series inside one or more streams. 

    { requestId: <nonnegative int53>,   
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

#### sources <a name="sources"></a>
Identifies the server data series to transform and deliver.
There are two forms of source selector, simple and custom.

Each request may specify an array of simple selectors or an array
of custom selectors (but not a mix of simple and custom).

Note that the response may contain more or fewer streams than are selected
via `sources`. 
For example, a transform may choose to combine multiple sources
and produce a single output stream.

The typical `sources` array contains just a single simple selector string, 
and returns a single stream in response.


### Simple source selectors
Simple source selectors specify one or more data series by name. 

Data series names are typically constructed as slash separated strings. 
String names may contain ascii letters, numbers, dots, underscores, slashes, and dashes. 
Case is significant. 

The slash separation convention is not required, but it is typically handy to
organize data series hierarchically.  
Also, tools like the admin console take advantage of the convention to enable
default browsing the available data series.

    sources: ["<alpha/beta/carrot>", …]

### Custom source selectors
Application specific selectors are built as server plugins. 
Custom selectors are take 
that take arbitrary parameters. 

    sources: [
      { selector: "<selector>",          // type of selector
        selectorParameters: {<varies>}   // parameters to the selector
      },
      ...
    ]

#### sendUpdates
If set to false, the server will buffer and send all data in a single response.
No ongoing stream data will be sent.

SendUpdates:false limits the server to sending a single response to the StreamRequest. 
Transform implementations are advised to return a 'complete snapshot' of current data in 
response to SendUpdates:false requests. 
For example, a StreamRequest that asks for a slice of the last 10 minutes of time series data 
should return all the data from 10 minutes ago until the present time in a single response. 
In contrast, a SendUpdates:true request would be expected to continue sending Update message 
as time progresses forwards.

#### request
Maximum number of data items to deliver over this stream until updated by subsequent StreamControl messages. 

{% markdown sparkle/requestParam.md %}

If the request returns multiple streams, the requestMore value applies to each stream independently. 
See [Streaming](Streaming.html) for details.

####transform
Identifies the name of the data transformation.

`transform` must be a string containings ascii letters, numbers, dots, and underscores. 
Case is significant

####transformParameters
An arbitrary json object that will be passed to the transform. 
See (Built in Transforms](DataTransforms.html) for details of the prebuit transforms.
