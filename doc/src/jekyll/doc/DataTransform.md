---
layout: default
title: Data Transformation
---

Data Transforms
---
Sparkle data servers host an extensible set of data transforms that clients can use.
Transforms map input data series to output data, adapting data formats 
and applying mathematical functions as data passes through the transform.

## Specifying Transforms in the Sparkle Data Protocol
Sparkle clients specify source columns and transformations in the data protocol 
by sending [StreamRequest](StreamRequest.html) messages. 
The StreamRequest includes two properties for transforms: 
the `transform` property and the `transformParameters` property. 
`transform` identifies which transform to run, 
and `transformParameters` carries configuration for the selected transform in the form of a json object. 

TransformParameters are arbitrary json objects. 
Each transform specifies the format of its expected transformParameters. 
Sparkle data protocol clients are expected to provide transformParameters 
in the requisite format for each transform.

Transform implementors should use reuse the properties from the [built in transforms](BuiltInTransforms.html)
where possible. 
e.g. Rather than inventing a new way to specify data slices on time series streams, 
a custom transform should include [range](BuiltInTransforms.html#range).

## Built In Transforms
Built in transforms are provided for timeseries streams.

The built in transforms enable clients to:

* fetch raw unprocessed data
* select time slices for time value streams
* group time slices into calendar periods
* aggregate values from each group with basic statistical functions (e.g. min,max,mean).

See [Built in Tranforms](BuiltInTransforms.html) for details.

## Custom Transforms

The Sparkle data server library is extensible, 
developers may add custom transforms that are hosted alongside (or even replace) the built in transforms. 
See _custom-transforms_ in the [reference.conf](https://github.com/mighdoll/sparkle/blob/master/sparkle/src/main/resources/reference.conf) file for details.

