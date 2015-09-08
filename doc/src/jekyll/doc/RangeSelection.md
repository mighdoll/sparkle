---
layout: default
title: Range Selection
---

Range Selection <a name="Ranges"></a>
---

Range parameters allow specifying intervals of the source data. 

    ranges: [
      { start: <json value, typically usec>,  // (optional) inclusive start of the range
        until: <json value, typically usec>,  // (optional) exclusive end of the range
        limit: <posInt>,                      // (optional) for half-bounded ranges: 
                                              //    limit to this number of items 
        timeZoneId: "<olson code>"            // (optional) timezone of the underlying UTC
                                              //    timestamps, .e.g. "Europe/Paris"
      },
      ...
    ]

### start
`start` specifies the start of the range. 
The result is inclusive of the start value. 

### until
`until` specifies the end of the range. The range is exclusive of the end value. 

### Half bounded ranges
If `start` or `until` is unspecified, the interval is bounded only on one side. 

If both `start` and `until` are unspecified, 
the interval is half-bounded by first key in the source data at the time the request is processed 
(or by the first key to arrive subsequently if there is no source data at request time).   

### limit 
`limit` allows clients to place a maximum on the number of source items produced by a half-bounded range. 
`limit` is useful for example for clients to request the most recent item, 
or to request a single extra item before and after a bounded range. 

Note that `limit` regulates the number items requested from the data series to be processed by the transform, 
and is not intended to control of the size of the output to the client. 
Clients who wish to control the number of items produced by a transform should instead adjust
the [Summary Parameters](#SummaryParameters).


### Multiple Ranges
Ranges is an array: multiple intervals may specified. 
Unless specified otherwise by the specific transform, 
each interval is processed independently by the source transform.
For example, a summarizing transform will produce at least one summary value for each interval, 
it will not combine multiple intervals into one block to summarize.

