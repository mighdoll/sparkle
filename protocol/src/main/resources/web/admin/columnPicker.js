define(['admin/app', 'sg/util'],
  function (app, _util) {
    app.directive('columnPicker', ['$q', function($q) {

      /**
        return a nested tree of child paths from a collection of slash separated strings.
        e.g. given a/b/c and a/b/d/e
        produce:
          { a: {
              b : {
                c : {},
                d : {
                  e : {
                  }
                }
              }
            }
          }
       */
      function listToTree(list) {
        var tree = {};
        var split = list.map(function(item) {
          var pieces = item.split("/");
          var subTree = tree;
          pieces.forEach ( function(piece) {
            if (subTree[piece] === undefined) {
              subTree[piece] = {};
            }
            subTree = subTree[piece];
          });
        });
        return tree;
      }

      /** Return a subtree of objects in the form expected by the tree-control
        * from a subtree of objects containing other objects. Also adds columnPath
        * and leaf properties to the nodes. */
      function createArrayTree(subTree, pathPrefix) {
        var array = [];
        for (var child in subTree) {
          var prefix = pathPrefix ? pathPrefix + "/" : "";
          var columnPath = prefix + child;
          array.push({
            name: child,
            children: createArrayTree(subTree[child], columnPath),
            leaf: !hasProperties(subTree[child]),
            columnPath: columnPath
          });
        }
        return array;
      }

      /** add a leafParent property for nodes in the tree-control arrayTree that
        * are the parents of only leaf nodes. */
      function addLeafParent(arrayTree) {
        forAllNodes(arrayTree, function(elem) {
          var leafParent = elem.children.every(function(child) {
            return child.leaf;
          });
          elem.leafParent = leafParent;
        });
      }

      /** run a function over all nodes in an tree-control arrayTree */
      function forAllNodes(arrayTree, fn) {
        arrayTree.forEach(function(elem) {
          fn(elem);
          forAllNodes(elem.children, fn);
        });
      }

      /** return a flat list of all the nodes in the arrayTree */
      function allNodes(arrayTree) {
        var nodes = [];
        forAllNodes(arrayTree, function(elem) {
          nodes.push(elem);
        });
        return nodes;
      }

      /** Return a tree-control style tree from a list of slash separated strings.
        * annotates the tree with 'leaf' and 'leafParent' properties for the leaves
        * of the tree (and parents of only leaf nodes). */
      function childrenTree(list) {
        var rawTree = listToTree(list);
        var childrenArrayTree = createArrayTree(rawTree);
        addLeafParent(childrenArrayTree);
        return childrenArrayTree;
      };

      /** when an autocomplete entry is selected, calculate the tree for the columns tree */
      function autoCompleteSelection(item) {
        var $scope = this;
        if (item == undefined) {
          $scope.treeModel = [];
        } else {
          $scope.findChildren(item).then(function(results) {
              $scope.$apply(function() {
                var arrayTree = childrenTree(results);
                $scope.treeModel = arrayTree;
                $scope.expandedNodes = allNodes(arrayTree);
              });
           });
         }
      }

      /** plot button clicked */
      function plotButton(node) {
        var $scope = this;
        $scope.plotData(node.columnPath);
      }

      /** download button clicked */
      function downloadButton(node) {
        var $scope = this;
        $scope.downloadFile(node.columnPath);
      }

      return {
        restrict: 'E',
        templateUrl: 'partials/column-picker.html',

        scope: {
          search: '=',
          findChildren: '=',
          downloadFile: '=',
          plotData: '='
        },

        controller: function($scope) {
          // for autocomplete
          $scope.selectionChange = autoCompleteSelection;
          $scope.selectedItem = null;
          $scope.term = null;

          // for tree-control
          $scope.plot = plotButton;
          $scope.download = downloadButton;
          $scope.treeModel = [];
          $scope.selectedTreeNode = [];
          $scope.expandedNodes = [];
          $scope.treeOptions = {
              nodeChildren: "children",
              dirSelectable: true,
              injectClasses: {  // mostly unused, could remove most of these
                  ul: "a1",
                  li: "a2",
                  liSelected: "a7",
                  iExpanded: "a3",
                  iCollapsed: "a4",
                  iLeaf: "a5",
                  label: "a6",
                  labelSelected: "a8"
              }
          }


        }
      };

  }]);
});