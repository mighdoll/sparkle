---
layout: default
title: Status Message
---

Status Message
---

Status Message
---
A status message may be sent from service to client to indicate the status of a request, a stream, or the session.  
Most commonly, status message are returned as error responses to a client request.  See [Errors](Errors.html).
  
    { requestId: <nonnegative int53>,         // required if this is status of client request 
      realm: "<realm>",                       // (optional) realm scope for this status
      messageType: "Status",
      message: {
        code: <nonNegative int53>,            // status code
        description: "<descriptive message>"  // description of the error
      }
    }

Status messages may reference a stream by including a streamId property instead of a requestId property. 

Status messages may reference the current session by including neither a streamId property nor a requestId property. 

Status messages referencing a stream or the current session may be pushed asynchronously by the server.

The code portion of the status message is fixed by this protocol definition. 
The description is intended for developer communication, not for parsing by client protocol implementations. 
Accordingly, description messages may vary, 
e.g. compatible servers may add more contextual description of error causes.

## code 
The `code` field contains a positive whole number identifying specific errors or success.
Codes are specified [here](Errors.html).

## description
The `description` field contains a non-normative text description of the status condition intended
to aid developers. 
The `description` field is not intended to be shown to non-developer users.
Conformant servers may vary the `description` field on a per request basis 
to provide more information to developers.

