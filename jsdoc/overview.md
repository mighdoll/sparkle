---
layout: default
title: Components
---

## Chart Source
- **js/sg** - The core charting library for interactive server-centric charts.
Sg.js is built with d3, require.js, and when.js.
- **js/lib** - (To be removed: was dependent .js libraries. 
Most of the project's javascript libraries are now managed via [webjars](www.webjars.org).)

## Chart Dashboard 
- **js/admin** - Chart dashboard with UI controls for choosing data series, changing displayed chart type, etc.
The dashboard is built with sg.js, angular.js, angular-material, angular-tree-control, and ngFileUpload.

## Style
- **js/css** - Styling for sg.js charts.
- **js/font** - Default font for sg.js charts
- **js/admin/dataDashboardController.js**.  Styling for the default dashboard. 

## Tests
- **js/test/spec** - unit tests (using jasmine 2)
- **js/test/test** - utilities for unit tests.
