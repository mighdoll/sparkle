---
layout: default
title: Data Series
---

###Data Series
A data series is a sequence of data items. Data items are either values or key-value pairs.

The canonical example of a data series is a sequence of sensor data. 
e.g. an array of timestamped sensor samples. 

In the sparkle json API, a simple time series of numeric values is encoded like this: 

     [ [<timestamp_1>, [<data_1>]], 
       [<timestamp_2>, [<data_2>]], 
       ...
     ] 

Here's a typical time-value series:
{% markdown sparkle/typical-time-value.md %}

In API responses, a data series is labelled as 'KeyValue' or 'Value'. 
KeyValue and Value items are not intermixed within the same data series.

### Value Types
Values may be scalar values (numbers, strings or booleans), records (json objects), or arrays. 
Numbers are restricted to IEEE-754 double precision for javascript compatibility.
Data values may be optional (nullable). Optional values are encoded as single element arrays.

## Consistency of value types
Values are generally required to be of consistent type to ease decoding.

All values must be of the same type (number, string, boolean, record, or array) in the data series.
i.e. String, number, boolean, record, and array values may not be intermixed in the same series. 
Element within an array must also all be the same type (number, string, boolean, record, or array).

The structure of record values (objects) is not required to be constant across the series. 
However, like named record fields must have consistent type across the series.

See below for examples of valid data values.

### Key Types
The API permits keys of any sortable scalar value (strings or numbers). 
As with values, numbers are restricted to IEEE-754 double precision for javascript compatibility.

### Value Series
In a Value series, data items are simply json values or optional json values.

    [ <value>,    // item 1
      <value>,    // item 2
      ...
    ]

    [ [<optional_value>],    // item 1
      [<optional_value>],    // item 2
      ...
    ]

Here's an example value series with record types:

    [ {'name': 'tom', 'height': 165, 'weight': 70}, 
      {'name': 'sally', 'height': 145, 'weight': 45}, 
      {'name': 'tom', 'height': 130, 'weight': 55}
      ...
    ] 

Subsequent items delivered in the same series append items to the end of the sequence.

#### KeyValue Series <a name="KeyValue"></a>

In KeyValue series, items are two element arrays. 

    [ [<key>, [<optional_value>]],    // item 1
      [<key>, [<optional_value>]],    // item 2
      ...
    ]

The first element is a string or a number. 
The first element is interpreted as a unique key for the lifetime of the series. 
Every item in a KeyValue series must contain a key.

The second element is the value at that key. 
The value is wrapped inside an array containing either zero or one element.
An empty array indicates an empty (null) value at that key.
A non-empty array contains the value at that key.
The value may be any json value type: strings, numbers, arrays, or json objects.

Here's a typical time value series:
{% markdown sparkle/typical-time-value.md %}

The value can be any json type, including an array. Here are some examples of other series.

    [ [1234123, ["start"]], [1237000, ["stop"]] ]  // string values for e.g. labeled events
    [ [1234123, [[3.5, 3.9, 27]]] ]                // multiple values per time (e.g. heat map)

And note that the first element (key) can be a string, not just a number:

    [ ["ny", [27]], ["bos", [3.5]] ]               // data labeled by string (e.g. bar chart)

Clients should interpret items with a key previously used in the series as carrying a replacement value. 

Items may be deleted by sending an empty array for the value.

    [ ["ny", []] ]                                 // delete value for key "ny"

#### Unique Time Keys
Because duplicate keys represent overwriting in a KeyValue series, 
special care may be required for time-value series. 
If multiple values may appear at the same time key, 
subsequent values will replace prior values at the same time key.

One option is to use a higher pseudo-resolution in the time keys than is present in the underlying data.
For this reason, the API typically uses microsecond precision time keys 
even though data is typically recorded at millisecond resolution or less.
The microsecond portion of the time key can then be used as a sequence number to distinguish hundreds
of values within the same millisecond. [^composite-keys]

[^composite-keys]: A subsequent release is likely to support true composite keys.

----

