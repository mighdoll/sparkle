---
layout: default
title: Built in Transforms
---

Built in Data Transforms
---

## Raw
The raw transform simply copies its input to its output.
 
    { requestId: <nonnegative int53>,   
      messageType: "StreamRequest",
      message: {
        sendUpdates: false, 
        request: <non-negative int53>,   
        sources: [<sourceSelector>, …], 
        transform: "Raw",                 // the raw transform
        transformParameters: {
          ranges: [<interval selectors>]  // optional ranges of the input to copy
        }
      }
    }

#### ranges
See [Range Selection](RangeSelection.html).
If ranges is unspecified, the entire input is summarized.

## Time Series Summary Transforms
The time series summarizing transforms work on key-value source columns, 
with numeric keys intepreted as epoch milliseconds, and numeric values.

The summary transforms divide the input data series into partitions, 
and then apply a summary function to each partition. 

    { requestId: <nonnegative int53>,   
      messageType: "StreamRequest",
      message: {
        sendUpdates: false, 
        request: <non-negative int53>,   
        sources: [<sourceSelector>, …], 
        transform: "<SummaryTransformName>",      // name of this transform
        transformParameters: {
          ranges: [<interval selector>, ...]      
          nPartsByDuration: <nonnegative int53>, 
          nPartsByCount: <nonnegative int53>,      
          intoCountParts: <nonnegative int53>,   
          intoDurationParts: "<duration>", 
          emitEmptyPeriods: <boolean>,            
          timeZoneId: "<olson code>",             
          ongoingPeriod: "<duration>"       
        }
      }
    }

### ReduceMin
The summarized value for each partition is the minimum value in that partition.

### ReduceMax
The summarized value for each partition is the maximum value in that partition.

### ReduceMean
The summarized value for each partition is the mean value in that partition.

### ReduceAverage
Alias for ReduceMean

### ReduceSum
The summarized value for each partition is the sum of values in that partition.

### ReduceCount
The summarized value for each partition is the count of values in that partition.

See [Summary Transform Parameters](SummaryParameters.html) for details of properties
available for controlling summarization.

