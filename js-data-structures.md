Dashboard
  .charts:[DashboardChartData] -- array of charts to create
  .zoomTogether:Boolean        -- if true, zooming one graph zooms them all
  
ChartData 
    .maxDomain:[Date,Date]     -- maximum allowable zoom, set externally (or leave blank and
                                  it will be defaulted to defaults to time domain of first 
                                  data set)
    .displayDomain:[Date,Date] -- current zoom, modified by the chart component in response to zoom events
    ?.groups:[SeriesGroup]     -- data sets and associated plotters 
    ?.style                    -- set this css class on the chart (so that css rules can style it)
    ?.title
    ?.margin
    ?.size
    ?.plotter:PlotInfo
    ?.showLegend:Boolean       -- display a 'key' describing the color of each data series
                                  (true by default)
    ?.timeSeries:Boolean       -- x axis is in epoch milliseconds, display as time strings 
    ?.xScale:d3.scale          -- scale to use for the xAxis, (utc time by default)
    ?.showAxis:Boolean         -- display the x axis at the bottom of the chart (true by default)
    ?.transformName:String     -- transform to use to summarize data to fit to display (Raw by default)

SeriesGroup 
   .label: String              
   .series: [Series]
   .axis: AxisGroup             
   ?.color: [d3.scale.ordinal with color values]
 
Series
  .set:String            -- name of the data set that holds the series e.g. "default"
  .name:String           -- name of the column inside data series e.g. "p99"             // LATER rename to column?
  .range:Array2[Double]  -- minimum and maximum of the data set values
  .domain:Array2[Date]   -- minimum and maximum input to the data set (e.g. start and end time)
  ?.error:String         -- data failed to load, display this error string instead
  ?.label:String         -- label of the data series (.name if not specified)  e.g. "p99"
  ?.plot:PlotInfo        -- plot style for this data series
   .data                 -- two dimensional array of data elements [time:msec, value:double].  (this property is
                            added internally with results from the dataFetch function)   
   .displayDomain:[Date, Date] -- current zoom, set by the chart component so that series can see it  
   .displayRange:[Double, Double] -- current range of the data, set by the sideAxis so that series can see it   
   .plotSize             -- size of chart plot area, set by the chart so that series plotters can see it 
   .xScale               -- time scale, set by the chart so that series plotters can see it
   .yScale               -- vertical scale set by sideAxis so that series plotters can see it 
   .color                -- color of this series.  added internally by sideAxis          
   .labelLayer           -- svg 'g' container in front of the plot area, but unclipped by zoom animation
   .categories:[String or Number]  -- if set, only display these values.  If valueCodes 
                            is present, then each category is decoded with the valueCodes table.
   .valueCodes:Object    -- key:value table if set, use this relation to translate values in the dataset and
                            the .categories property (e.g. for category labels)
   ._codeMap:d3.map       -- d3.map version of valueCodes
   ._invertedCodes:d3.map -- set internally, the inverse of the valueCodes map

PlotInfo
  .plotter                    -- component to do the plotting, e.g. linePlot
  ?.strokeWidth               -- an example of an additional property (these are passed to the plotter)
  ?.named:[NamedSeriesGroup]  -- (if plotter is a group plotter) array of groups to plot 

DashboardChartData 
  (like ChartData, except use NamedSeriesGroup in place of SeriesGroup)

NamedSeriesGroup 
   .label: String                 -- label for the axis
   .named: Array[NamedSeries]     -- server names for data set and column
   .error:String                  -- display this error message rather than data

NamedSeries
   .name:String              -- "dataSetName/column" strings that identify the data set & column on the server
   ?.label:String            -- label for the data series (column is used if .label is not specified)

AxisGroup     // descriptor for binding a SideAxis 
  label:String                    -- label for this axis
  colors:d3.ordinal.scale         -- colors being plotted against
  orient:String                   -- "left" or "right" side for the axis
  series:[Series]                 -- all data series being plotted against this axis
  chartMargin:[Number,Number]     -- internal margin in the chart
  plotSize:[Number,Number]        -- size of the plot area
  zeroLock:Boolean                -- locked minimum axis value at zero (handy for e.g. bar charts)

CssBox
  .top:Number
  .left:Number
  .bottom:Number
  .right:Number

MaxLockData
  .locked:Boolean               -- true to display 'lock' mode
  .lockRange:[Number,Number]    -- data range in lock mode 
  .maxValue:String              -- maximum value of data range in lock mode in display format

LegendData
  .label:String   -- the legend text, 
  .color:String   -- .css fill style for the legend color swatch (typically a color)

Categorized
  .color:String               -- color to draw the marks and label for this category
  .series[SeriesDescriptor]   -- series from which 
  .name:String                -- label for the category
  .data:[Date]                -- time series data array for this category

SymbolMark  (constructed by adding fields to a Categorized)
  .color:String               -- color to draw the marks and label for this category
  ?.plot: {                   -- optional options 
    ?.symbol:D3.svg.symbol    -- d3 symbol 
    ?.size:Number             -- size in __square__ pixels.  (i.e. height is sqrt(size))
    ?.type:String             -- d3.symbol.type (e.g. "circle", "diamond", "cross")
  } 

ZoomBrush event (reported as a custom event by the zoom component)
  .extent:[Number,Number]    -- extent from the underlying brush.  If present, this field indicates 
                                 the user has just released the mouse at the end of a brush gesture
  .zoomReset:Boolean         -- true if the user double clicked, (indicating user wants to reset the zoom 
                                to a default level).  False or undefined otherwise.
  .transitionComplete        -- true if the zoom was bound to a transition and has just completed a zoom
                                out animation


