Notes on typing for transforms and columns in SparkleTime.
-----
Source columns contain keys and values of a fixed type within the column.  Different columns can use differing types.  
The type of the column keys and values is not discernible from the name of the column, but the types can be
 reported after the column metadata is fetched from storage.  A column, accordingly, looks like this:
     trait Column[T,U] {
       def keyType:TypeTag[_] = ???
       def valueType:TypeTag[_] = ???
     }

Callers working with columns will ask Storage for a column instance of type Column[_,_],
since they don't know the type of the column data in advance.  Those callers who plan to
take advantage of particular key or value typing will then query the key or value 
types, run time pattern match on acceptable types, and cast to a typed version of Column.
e.g. to a Column[Long, _]. 
 
Transforms normally take type parameters including typeclass bounds so that the compiler can offer guidance
on constructing and combining transforms.  

Protocol request messages (StreamRequest) typically specify source columns and transforms
by string name.  The compiler can't statically determine whether requests and transforms
are type compatible.  

Currently the Transform engine is the nexus of the conversion from dynamically requested 
Columns and transformations to static types.  The transform engine currently does a pattern 
match on the supported source column types and transforms.  
