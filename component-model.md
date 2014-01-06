### How to write sparkle-graph components.

#### Components should follow the d3 proposed object model so that component users can:
* Create a component configurator

        my = myComponent();

* Get/set properties on the configurator by calling 

        my
         .property(value)
         .property2(value2);

* Pass per instantiation data and config via .data()

        domSelection.data([myConfigAndData]);

* Instantiate a component instance by binding the component to a DOM node.  

        domSelection.call(my);
        

### Definitions
* __component__ - A d3 module using the d3 conventions for creating, getting setting properties, 
  and data binding.  See Components above.
* __component configurator__ - a caller visible object that the caller uses as a 'stamp' for producing 
  one or more component instances and binding them to DOM nodes.
* __component instance__ - a component closure attached to a single DOM node.  This closure is produced
  internally inside the component and is not normally accessible to component users.
* __bind__ - combine data with an HTML/SVG node via .data(), or combine a component with an HTML/SVG+data node, via
  e.g. selection.call(myComponent).
* __attach__ - place an HTML/SVG node in the DOM tree, typically by making it a child of a provided parent.
* __data join__ - bind an array of data with an array of HTML/SVG nodes via d3's selection.data()
    operator.  If there more or fewer data elements than nodes, the overflow underflow is put in the .enter() 
    or .exit() selections respectively.  (Note that changing the contents of the data doesn't magically
    trigger anything in d3.  However, a properly implemented component will redraw to match the current 
    state of the data when it is rebound.
    
#### **Bind** triggers the component to do several things:
1. Attach new HTML/SVG elements to the DOM for the visual representation of the component 
    _(typically only on the first bind call)_
1. Update the visual display to match the (potentially updated) data bound to the DOM,
    including participating in any parent transitions. 
    _(typically on every bind call, updating the data)_
1. Remove any extra HTML/SVG elements.
    _(rarely executed, only if the structure of the data changes)_
  
### What's in .data()?  
_There are several kinds of data stored in the __data__ field of a DOM node for a component._
1. Data to be visualized.  e.g. an array of x,y pairs to describe a line or scatter plot.  
  This is the canonical use of d3 data binding.
1. Per component instance configuration.  Since the configurator is 'stamped' 
  multiple times into the DOM, any configuration that varies per instance should be passed 
  to the component via .data too.  

### Implmentation tips for components:
* __Configure fluently and via bound data__  Most component configuration settings should be exposed two ways:
  via the configurator.myProperty("foo") _and_ via bound data:  selection.data({myProperty:"foo"}); 
  selection.call(configurator);
* __Handle transitions__  As bind calls propagate from component to subcomponent, components
  should inherit any active transitions by calling d3.transition(selection) internally.  This
  enables a zoom transition to synchronize the transition across many components.
* __Cache local state in the DOM__  Sometimes components have some local state that they may
  need for e.g. a subsequent transition.  Storing the data within the local closure (component
  instance) will break if the caller chooses to reconfigure the component.  Instead, store
  the data in a local property of the DOM node, e.g. this.__myComponent.
* __Reconfigure or cache subcomponents__  Components should guarantee the same result when re-bound to
  a new configurator with the same settings.  If your component uses a subcomponent, it
  can either cache the subcomponent configurator, or recreate one.  (sg/util.attachComponent() caches 
  the configurator in the DOM.)
* __Documentating a component__ _TBD_
* __Testing a component__ _TBD_

