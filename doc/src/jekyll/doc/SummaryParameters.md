---
layout: default
title: SummaryParameters
---

SummaryParameters
---

    transform: "<SummaryTransformName>",
    transformParameters: {
      ranges: [<interval selector>, ...]      // (optional) ranges of the input to summarize
      intoDurationParts: <positive int53>,    // (optional) group into n parts, with each part equal in duration
      nPartsByDuration: "<duration>",         // (optional) group by duration
      nPartsByCount: <positive int53>,        // (optional) group by counting items
      intoCountParts: <positive int53>,       // (optional) group into n parts, with total/n items in each part
      emitEmptyPeriods: <boolean>,            // (optional) emit a key for parts w/o data
      timeZoneId: "<olson code>",             // (optional) use this timezone, not gmt
      ongoingPeriod: "<duration>"             // (optional) time window for ongoing data
    }

### ranges
See [Range Selection](RangeSelection.html).
If ranges is unspecified, the entire input is summarized.

## Grouping into partitions
The summary transforms divide each input data series into partitions. 
Clients have four options to specify partitioning: `nPartsByDuration`, 
`nPartsByCount`, `intoCountParts`, and `intoDurationParts`. 
Only one partitioning may be specified at a time.
If no partitioning is specified, the transform will reduce each specified range
into a single part.

If the final partition available in the source series is incomplete at the
time of the request, the server will nonetheless send a summary. 
However, if the client requests an [ongoingPeriod](#ongoingPeriod), 
subsequent [Update](#Update.html) messages will update the partial summary. 


#### intoDurationParts
Divide into partitions based on a duration. See [duration string](#duration).

The partitions each begin on an even boundary of the duration. 
e.g. if '1 month' is specified, all partitions will begin at midnight
of the first day of the month in the local timezone. 
Note that partitions are 

If the range does not begin on a partition boundary, 
it is rounded (backwards) in the reported partitions to begin 
on a partition boundary. 
A rounded partition summary may begin prior to the requested range start,
but will not summarize data prior to the requested range start.

#### nPartsByDuration
Divide into `nPartsByDuration` partitions by dividing the range into
duration sized segments and assigning the data items to each segment.
The partition sizes are rounded to the nearest human friendly duration.
e.g. if a client proposes rounding a weeks worth of data into 5 `intoDurationParts`, 
the server will choose '1 day' sized partitions.

`nPartsByDuration` partitioning is similar to `intoDurationParts`, 
but the duration is calculated automatically.

#### nPartsByCount
Divide into partitions based on a count of the elements.

#### intoCountParts
Divide into `intoCountParts` partitions by counting the number of data items
in the entire range and distributing the items into partitions with 
the equal numbers of elements.

#### timeZoneId <a name='timeZoneId'/>
Specify the local timezone for `intoDurationParts`, `nPartsByDuration`,
and ongoingPeriod calculations.

#### ongoingPeriod <a name='ongoingPeriod'></a>
Request that the server send data updates at the frequency specified by this period.
If new data is available for the requested range, 
the server will send an [Update](Update.html) message after each `ongoingPeriod`.

For summarizing transforms, the first update will typically contain data that 
updates the value of the last summary that was sent to the client. 
e.g. if the summary is 'intoCountParts`.

that revises the total of the partition


The period is specified as a [duration string](#duration).

#### emitEmptyPeriods 
If true, the server will emit a key and an empty value `[]` for periods that are
missing in the source data.

### max partitions
A server configurable value controls the maximum number of partitions 
created by any transform. 
By default the maximum is 3000 data items.

## Duration string <a name="duration"></a>
A time period specified by a string like "1 month". 
The duration string must consist of a positive integer, 
followed by a space, followed by a time period word.

Supported time periods words are: 

* "microsecond", 
* "millisecond", 
* "second", 
* "minute", 
* "hour", 
* "day", 
* "week", 
* "month", 
* "year" 
* and plurals of the above.

