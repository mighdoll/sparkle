---
layout: default
title: StreamDrop Message
---

StreamDrop Message
---
A drop message from client to server tells the server to stop sending updates to a stream.

    { requestId: <nonnegative int53>,
      messageType: "StreamDrop",  
      message: {
        streamId:<nonnegative int53>
      }
    }

See [Streaming](Streaming.html) for a discussion of data streaming.

#### streamId
The `streamId` must match the streamId from a previously received [Streams](Streams.html) message.

