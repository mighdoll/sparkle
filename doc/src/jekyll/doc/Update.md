---
layout: default
title: Update Message
---

Update Message
---
An update message carries new data for one or more streams. 

    { messageType: "Update",
      message: {
        streams: [
          { streamId: <nonnegative int53>, // stream to modify
            data: [<stream item>, ...],    // optional property. data to merge into
                                           // the stream (merge semantics
                                           // depend on the stream type, see below)
            delete: [key,...]              // (optional, only for keyedArray streams.)  Deletes
                                           // each key, value identified by key in the delete
                                           // array. (each key is a string or number)
            end: true                      // optional, indicates this stream is done
          },
          ...
        ]
      }
    }

## Updates are Server Pushed
After sending an Streams message to start a stream, the server may send an update
message at any time.

The client should interpret the data property in an Update payload as a modification 
to any existing data that was received for the same streamId. See details below. 

#### data
Data contains an array of data items. 
Data items formats are described in [DataSeries](DataSeries.html).

#### delete
An optional `delete` property describes data items to be removed. 
Each element in the the `delete` array references a key in the data stream.

In general, the server should not send `delete` keys that have not been received by the client.
The client should ignore any `delete` keys that it hasn't seen before. 
Deletion is not persistent, subsequent update messages may restore the key.

Deletion is logically applied before `data` updates. 
A key listed both in `data` and in `delete` is replaced with the new value in `data`.

`delete` is only applicable to [key value](DataSeries.html#KeyValue) streams. 

#### end
Optional field indicating that an data stream has completed.
No more data will be sent over a completed stream.

### Updates to KeyValue streams
Update messages on the same streamId replace values in the stream by sending data with the same key. 

Update message on the same streamId delete values by setting the optional delete field.

    delete: [1234123, 1237000]       // delete items with keys 1234123 and 1237000

### Stream Updates to Value streams
Update messages to the same streamId append elements to Value stream. 

Update messages cannot remove element from the Value stream, 
nor may they modify elements in the Value stream. 

### Bundling messages
The server is recommended to bundle stream updates into the same Update message 
if they are semantically simultaneous. 
e.g. all the updates to streams at time :00 should be sent in the same update message. 
Clients will typically process an update message in its entirety, 
so bundling the updates into one message can reduce UI 'jitter' when updating multiple visualized streams.

