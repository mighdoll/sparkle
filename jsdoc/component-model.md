---
layout: default
title: sg.js component model
---

## sg.js Javascript Component Model

Sg.js components follow the [d3 proposed object model](http://bost.ocks.org/mike/chart).
Components are function objects with getter and setter properties
that are draw by [joining data](#dataJoin) to DOM elements.

Sg.js components extend the basic d3 [object model](http://bost.ocks.org/mike/chart)
in the following ways.

* Sg.js [component configuration data](chart-configuration.html) is bound to DOM 
elements as well as arrays of (typically numeric) data items to draw. 
* Sg.js component private state, e.g. saving previous state needed to
trigger an animation to the new state.

Individual Sg.js components have the following capabilities:

* Create a component configurator

        my = myComponent();

* Get/set properties on the configurator by calling 

        my
         .property(value)
         .property2(value2);

* Pass per instantiation data and component config via .data()

        domSelection.data([myConfigAndData]);

* Instantiate a component instance by binding the component to a DOM node.  

        domSelection.call(my);
        

## Hierarchical Structure
Sg.js components are used hierarchically. 
A `chart` typically contains several subcomponents including e.g. 
a `sideAxis`, a `richAxis`, and a `barPlot`.

The [configuration data](chart-configuration.html) for a chart is 
also arranged hierarchically, 
so that the user of the chart api can pass a single nested configuration object 
to create a chart.
The chart internally will pass the appropriate subparts of the nested configuration object 
to the appropriate subcomponents.

## Definitions for Understanding the Component Model
* __component__ - A sg.js d3 module using the d3 conventions for creating, 
getting and setting properties, and component binding.  
* __component configurator__ <a name='configurator'/> - A caller visible object 
that the caller uses as a 'stamp' (or factory) for producing one or more 
component instances and binding them to DOM nodes.
* __component instance__ <a name='componentInstance'/> - A component closure attached 
to a single DOM node.  
This closure is produced internally inside the component and is not normally accessible to users
of the component. 
Only the [component configurator](#configurator) is normally accessible to component users.
* __data bind__ <a name='dataBind'/> store a reference to a data array in 
an HTML/SVG DOM node via .data().
* __component bind__<a name='componentBind'/> combine a component with an HTML/SVG+data node, 
via e.g. d3's `selection.call(myComponent)` operation.
* __attach__ <a name='attach'> - place an HTML/SVG node in the DOM tree, 
typically by making it a child of a provided parent.
* __data join__ <a name='dataJoin'/> - bind an array of data with an array of 
HTML/SVG nodes via d3's `selection.data()` operator. 
If there more or fewer data elements than nodes, 
the overflow is put in the d3 `.enter()` selection and the underflow
is put in the or d3 `.exit()` selection.  
Note that changing the contents of the data doesn't magically trigger anything in d3. 
A properly implemented component will, however, update the DOM to match the current state of 
the data when it is rebound ([component bound](#componentBind)) 
to the DOM node containing the data.
    
## To Draw a Component
To draw or redraw a component on the page, a code using a component will:

1. [attach](#attach) a container DOM node into which the component will draw 
if the container dom node doesn't already exist.
1. create a new [component configurator](#configurator) 
1. [data bind](#dataBind) store a reference the data to the container node.
1. [component](#componentBind) connect the component to the container node.
This creates a [component instance](#componentInstance)

The component will internally perform a [data join](#dataJoin):

1. Attach new HTML/SVG elements to the DOM for the visual representation of the component 
    _(especially on the first bind call)_
1. Update the visual display to match the (potentially updated) data bound to the DOM,
    including participating in any parent transitions. 
    _(typically on every bind call)_
1. Remove any extra HTML/SVG elements.
    _(only if previously drawn data has been removed from the data array)_
  
### Debugging: What's in .data()?  
There are several kinds of data stored in the DOM node of an sg.js component.
Note that the data is visible as the private porpoerty `__data__` in the DOM node.

1. _Data to be visualized._ e.g. an array of x,y pairs to describe a line or scatter plot.  
1. _Per component instance configuration_  Since the configurator is 'stamped' multiple times into the DOM, 
component users pass configuration that varies per instance 
to the component via changes in the .data configuration.  
1. _Internal state storage_ for the component's use.

### Implementation Tips for Component Authors
* __Configure fluently and via bound data.__ 
Most component configuration settings should be exposed two ways:  
  
  via the configurator:

      configurator.myProperty("foo");

  _and_ via data bound configuation data: 

      selection.data({myProperty:"foo"});  
      selection.call(configurator);
* __Handle transitions__  As bind calls propagate from component to subcomponent, components
  should inherit any active transitions by calling d3.transition(selection) internally.
  This enables a zoom transition to synchronize the transition across many components.
* __Cache local state in the DOM__  Sometimes components have some local state that they may
  need for e.g. a subsequent transition. Storing the data within the local closure ([component
  instance](#componentInstance)) will break if the caller chooses to use a new [configurator](#configurator). 
  Instead, store component state in a local property of the DOM node, e.g. this.__myComponent.
* __Reconfigure or cache subcomponents__  Components should guarantee the same result when re-bound to
  a new configurator with the same settings.  
  If a component uses a subcomponent, the component can either cache the subcomponent configurator in
  DOM state, or recreate and reconfigure the subcomponent [configurator](#configurator).  

