(function(global) {

  global.Korolev = (function() {
    var root = null;
    var els = null;
    var addHandler = null;
    var removeHandler = null;
    var scheduledAddHandlerItems = [];

    function scheduleAddHandler(element) {
      if (!addHandler)
        return;
      if (scheduledAddHandlerItems.length == 0) {
        setTimeout(function() {
          scheduledAddHandlerItems.forEach(addHandler);
          scheduledAddHandlerItems.length = 0;
        }, 0);
      }
      scheduledAddHandlerItems.push(element);
    }
    return {
      RegisterRoot: function(node) {
        root = node;
        els = { "0": node };
      },
      RegisterGlobalAddHandler: function(f) {
        addHandler = f;
      },
      RegisterGlobalRemoveHandler: function(f) {
        removeHandler = f;
      },
      RegisterGlobalEventHandler: function(eventHandler) {
        var listen = function(name) {
          root.addEventListener(name, function(event) {
            if (event.target.vId)
              eventHandler(event.target.vId + ':' + event.type);
          });
        }
        listen('click');
        listen('submit');
        listen('mousedown');
        listen('mouseup');
      },
      Create: function(id, childId, tag) {
        var parent = els[id],
          child = els[childId],
          newElement;
        if (!parent) return;
        newElement = document.createElement(tag);
        newElement.vId = childId;
        scheduleAddHandler(newElement);
        if (child && child.parentNode == parent) {
          parent.replaceChild(newElement, child);
        } else {
          parent.appendChild(newElement);
        }
        els[childId] = newElement;
      },
      CreateText: function(id, childId, text) {
        var parent = els[id],
          child = els[childId],
          newElement;
        if (!parent) return;
        newElement = document.createTextNode(text);
        newElement.vId = childId;
        if (child && child.parentNode == parent) {
          parent.replaceChild(newElement, child);
        } else {
          parent.appendChild(newElement);
        }
        els[childId] = newElement;
      },
      Remove: function(id, childId) {
        var parent = els[id],
          child = els[childId];
        if (!parent) return;
        if (child) {
          if (removeHandler) removeHandler(child);
          parent.removeChild(child);
        }
      },
      ExtractProperty: function(id, propertyName) {
        var element = els[id];
        return element[propertyName];
      },
      SetAttr: function(id, name, value, isProperty) {
        var element = els[id];
        if (isProperty) element[name] = value
        else element.setAttribute(name, value);
      },
      RemoveAttr: function(id, name, isProperty) {
        var element = els[id];
        if (isProperty) element[name] = undefined
        else element.removeAttribute(name);
      }
    }
  })();

  document.addEventListener("DOMContentLoaded", function() {
    global.Korolev.RegisterRoot(document.body);
    var loc = window.location;
    var wsUri;
    if (loc.protocol === "https:") wsUri = "wss://";
    else wsUri = "ws://";
    wsUri += loc.host + loc.pathname + "/bridge";
    Bridge.webSocket(wsUri)
  });

})(this);
