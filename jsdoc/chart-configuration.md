---
layout: default
title: sg.js chart data structures
---
## Example Chart Configuration

Here's an example chart configuration for a line chart with showing a handful of settings in use:

          { title: "my favorite chart",
            timeSeries: true,
            showXAxis: true,
            margin: {top: 20, right: 50, bottom: 50, left: 75},
            transformName: "reduceMax",
            size: [600, 500],
            padding:[5, 5],    
            groups: [
              { axis: sideAxis(),
                named: [
                  { columnPath: "my/data",
                    transformName: "reduceMax",
                    grouping: "1 day",                  
                    plot: {
                      plotter: linePlot(),
                      color: "blue",
                      strokeWidth: 2,
                      interpolate: 'basis'
                    }
                  }
                ]
              }
            ],
            serverConfigWhen: request.jsonWhen("/serverConfig")
          }


## Chart configuration Reference
    Chart
      Must be set by caller
        .serverConfigWhen: When    -- when that completes with server configuration {port: wsocket port})

      May be set by caller
        ?.groups:[SeriesGroup]     -- data sets and associated plotters 
        ?.styles:String            -- set these css classes on the chart (so that css rules can style it)
        ?.title:Strin              -- text title of the chart
        ?.margin:CssBox            -- {top:X, right:X, bottom:X, left:X} object describing 
                                      space to leave around the plot area for axes, etc.
        ?.titleMargin
        ?.padding: [Number,Number] -- [width, height] in pixels to leave around the plotting area
                                      inside the chart axes.  
                                      (e.g. so that large scatter plot symbols are not clipped.)
        ?.transitionTime:          -- time in milliseconds for chart animations after a zoom
        ?.size:[Number,Number]     -- [width, height] in pixels of the svg window containing the chart
        ?.plotter:PlotInfo         -- plotter to use if no plotter is specified with the series
        ?.dataApi:DataApi          -- data access library to fetch data series
        ?.showLegend:Boolean       -- display a 'key' describing the color of each data series
                                      (true by default)
        ?.timeSeries:Boolean       -- x axis is in epoch milliseconds, display as time strings 
        ?.showXAxis:Boolean        -- display the x axis at the bottom of the chart (true by default)
        ?.lockYAxis:Boolean        -- true to prevent the y axis from rescaling with zooms (false by default)
        ?.xScale:d3.scale          -- scale to use for the xAxis, (utc time by default)
        ?.transformName:String     -- transform to use to summarize data to fit to display (Raw by default)

      Set by the Chart Component, readable by the caller 
        _.api: RedrawAPI             -- redraw API for external callers
        _.domainExtent: [Date, Date] -- min and max X value of any series in the chart
        _.displayDomain:[Date,Date]  -- current zoom, modified by the chart component in response to zoom events

    BarPlot
      ?.color: String               -- color to draw the bars
      ?.barWidth: Number            -- maximum width of the bar in pixels 
                                        (the bar plotter will use thinner bars if necessary to fit
                                         the bars in the available chart width)
      ?.barMargin: Number           -- minimum space between bars in pixels

    AreaPlot
      ?.color: String               -- color to draw the line at the top of the area
      ?.strokeWidth: Number         -- width of the line at the top of the area
      ?.fillColor: String           -- color of the filled area
      ?.interpolate: String         -- d3 interpolation style for the line and area (e.g. "monotone")

    LinePlot 
      ?.color: String               -- color to draw the line at the top of the area
      ?.strokeWidth: Number         -- width of the line at the top of the area
      ?.interpolate: String         -- d3 interpolation style for the line and area (e.g. "monotone")
      _.removeOtherPlots: Boolean   -- true to remove all other plots for this series
                                       (useful internally when switching between area and line plots)

    ScatterPlot
        ?.plot: Plotter               -- plotter component for the individual marks (dots in the scatter plot)
                                         the plotter component will be called for each data item
                                         and should support .color() and .fillColor() setters

      These are set on the Plotter or on the configurator
        ?.color: String               -- css color to draw the line for the mark
        ?.fillColor: String           -- css color to fill the mark

## SeriesGroup
 A series group describes a collection of data series that are plotted against the same Y axis.

    SeriesGroup 
       .named: [NamedSeries]  -- data series to fetch from the server
       ?.label: String         -- axis label  
       ?.orient: String        -- "left" or "right" side for axis
       ?.zeroLock: Boolean     -- true to always start the y scale at 0
       ?.lockYAxis: Boolean    -- true to prevent the Y axis from rescaling on zoom
       ?.error: String         -- display this error message rather than data
       ?.color: [d3.scale.ordinal with color values]


       _.series: [Series]     -- set by the chart after fetching series metadata from the server
     
    NamedSeries
       .columnPath: String        -- "path/to/data" strings that identify the data on the server
       ?.label: String            -- label for the data series 
                                    (default: trailing path element in columnPath)
       ?.grouping: String         -- group size for server aggregation, e.g. '1 day'
                                     (default: automatic grouping based on chart pixel width)


## Other internal data structures

### Zoombrush 
A zoom brush event is reported as a custom DOM event by the zoom component.

    One of the following properties will be set:
      ?.extent:[Number,Number]    -- extent from the underlying brush.  If present, this field indicates 
                                     the user has just released the mouse at the end of a brush gesture
      ?.zoomReset:Boolean         -- true if the user double clicked, (indicating user wants to reset the zoom 
                                    to a default level).  False or undefined otherwise.
      ?.transitionEnd             -- true if the zoom was bound to a transition and has just completed a zoom
                                    out animation

    Legend
      an array of [LegendItem] each LegendItem
        .label: String              -- text to display for the item
        .color: String              -- .css fill style for the legend color swatch 

      the array itself has the property
        .orient: String             -- "left" or "right" orientation for the legend

### CssBox

    CssBox
      .top:Number
      .left:Number
      .bottom:Number
      .right:Number

