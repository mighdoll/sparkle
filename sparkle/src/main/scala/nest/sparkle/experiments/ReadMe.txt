one: SubType [fails]
. first order simple subtyping isn't enough
  . we can't specify the type of stream returned from a stream transformation correctly
  . we don't have any way to refer to the type of stream

two: HigherKindedTypeClassLabel [fails]
. adding a higher kinded type gives us a way to describe the type of the stream
. using the higher kinded in a typeclass to 'label' the stream type isn't enough
  . The label doesn't extend to cover the return type of the stream transformation
    . and so we need an unsafe typecast in the end
    
three: TypeMemberHigherKinds [fails]
. using a type member to describe the type of stream, and thus the return type of a stream transform
  also isn't quite right
. the problem is that the type member creates a path-dependent type
  . so mapping to a new stream type just creates a longer path.
  
four: HigherKindedTypeClassProxy [works]
. using a higher kinded typeclass proxy for stream succeeds. 
. the proxy stream interface is well-typed as a stream (and visible in the Stack)
. the implementation of Stream methods are separate from the data
- all stream methods are implemented with an extra parameter to reference the underlying type,
  which is weird for the implementor of the stream implementation
+ Stream implementations don't need to be subclasses: retroactive modeling is possible

five: HigherKindedTypeClassDirect [fails]
. attempt at using a stream typeclass without proxying, but realized that it doesn't 
  really make sense to do that. 
  . either the typeclass has to proxy to the underlying stream that has the data
  . or it needs to just be a label (but then we found it hard to type the returned stream type) 
  
six: HigherKindedUnconstrained [works]
. uses a higher kinded type to describe the stream implementation in Stream
. interestingly, the implementation type isn't constrained to be a subclass of Stream
  in the Stream or Stack type parameters. 
  . The actual implementation does constrain the implementation to be a stream 
    (because the Stack contains a Seq of Stream, and Stream implementations 
     will naturally name themselves as the implementation type)
- access to the implementation type requires a forwarder 'self' method, which is weird
  for the user of the class

seven: HigherKindedFBounded [works]
. like six, but additionally bounds the type of the stream implementation to be a subtype of Stream
. the additional typing seems safer, but adds some noise in the type signatures and
  may not actually buy us anything in practice.
  
