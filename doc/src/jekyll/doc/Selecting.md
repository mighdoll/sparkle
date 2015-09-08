---
layout: default
title: Selecting Data
---

Selecting the Source
---

Data series stored on the sparkle data server are named by path separated strings.
Clients may request data in one of two ways: by name or by custom selectors. 
In both cases, the client sets the [sources](StreamRequest.html#sources) field 
in the [StreamRequest](StreamRequest.html) message to select the source data.

The simplest way to select data series is to specify them by string name.

    sources: ["<alpha/beta/carrot>", …]

See [StreamRequest](StreamRequest.html) for details.


Custom Source Selectors <a name='customSelectors'/>
---
In addition to using simple source selectors,
developers can extend the Sparkle Data server by defining application 
specific source selectors.  

### Abstracting data series names
Custom selectors are handy to simply provide a layer of indirection
between the data series names on the server. 

Sparkle installations with separately managed clients are advised to hide
specific series names from clients, and instead provide an indirection layer
via custom selectors. 
Indirecting enables teams to deploy new storage layouts in the data server 
without deploying new clients.

Installations with only internal web clients can simply deploy a new
version of the web client, and so may not need this layer of indirection.


### Rich custom selectors
Custom selectors can also provide a richer and application specific features
for clients to choose data series to fetch and transform.

For example, a performance dashboard might use custom selector to select latency metrics 
only from columns containing reporting certain errors. 
Imagine a Sparkle implementation stores performance metrics in separate 
series for each http response code for each request target. 
The sparkle server for this application can be extended to understand a 
"HttpResponseStreams" selector that returns all of the source event streams measuring latency of 
requests to a certain server type with a certain response code:

    sources: [
      { selector:"HttpResponseStreams",                      
        selectorParameters: {
          responseCodes: [401, 403],
          server:"logUpload.*"
        }
      }
    ]

Note that a single custom selector may return any number of source event streams.
