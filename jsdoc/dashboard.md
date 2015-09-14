---
layout: default
title: Sparkle Graph Dashboard
---

Sparkle Graph Dashboard
---

The Sparkle Graph Dashboard includes:

* An autocomplete based chooser for selecting data series from the server.
* A widget for uploading .csv/.tsv files via drag and drop.
* A control panel for changing graph types, summary parameters, time series and non time series, etc.

## Key Components

##  \<chart\>
The `<chart>` directive allows embedding a chart inside an angular web page. 
The chart directive uses a provided chartData object. 
The chartData object is passed directly to sg.js as the [chart configuration](chart-configuration.html),
allowing external control of all chart configuration.

## \<chart-panel\>
The `<chart-panel>` directive creates a interactive control panel to adjust chart settings
including: 

* chart type (line, scatter, bar, etc.), 
* interpolation options (linear, basis spline, etc.), 
* summary options (min, max, average, etc.), 
* grouping options (daily, hourly, automatic, etc.),
* y axis locking
* and other options.

## \<chart-with-settings\>
The `<chart-with-settings>` directive combines a `<chart>` with a `<chart-panel>` 
and includes a base [chart configuration](chart-configuration.html).


## \<column-picker\>
The `<column-picker>` directive displays an autocomplete and treecontrol based UI for  
choosing a data series from the server. 
Selected data series may be downloaded as .tsv files, or plotted on the chart.

