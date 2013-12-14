var ScalaMeter = (function(parent) {
  var my = { name: "data" };
  my.index = [{"scope" : ["sum"], "name" : "Reactress", "file" : "..\/sum.Reactress.dsv"}, {"scope" : ["sum"], "name" : "Rx", "file" : "..\/sum.Rx.dsv"}, {"scope" : ["sum"], "name" : "ScalaReact", "file" : "..\/sum.ScalaReact.dsv"}, {"scope" : ["ReactAggregate"], "name" : "Reactress-O(logn)", "file" : "..\/ReactAggregate.Reactress-O(logn).dsv"}, {"scope" : ["ReactAggregate"], "name" : "Reactress-O(n)", "file" : "..\/ReactAggregate.Reactress-O(n).dsv"}, {"scope" : ["ReactSet"], "name" : "Reactress-O(1)", "file" : "..\/ReactSet.Reactress-O(1).dsv"}, {"scope" : ["ReactSet"], "name" : "Reactress-O(r)", "file" : "..\/ReactSet.Reactress-O(r).dsv"}];
  parent[my.name] = my;
  return parent;
})(ScalaMeter || {});
