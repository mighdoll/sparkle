`request` is intended for flow control / backpressure. 

`request` sets a limit on the total number of stream items that the server 
is permitted to send over the lifetime of a stream. 
The server will not send more items than the 
sum of the `request` values sent by the client in the StreamRequest and subsequent StreamControl messages.

Clients may regulate the flow of items they will 
receive over a stream by setting requestMore to a low value, 
and then periodically sending a StreamControl message with a requestMore value set. 
Servers may buffer items that overflow the current `request` limit in the stream, 
or they may discard overflow data items that the client is currently unwilling to receive.
The overflow behavior is transform specific.

`request` is a non-negatie whole number, small enough to fit in a javascript numeric value.
A `request` value of 0 means that the server can send data whenever it is available. 
