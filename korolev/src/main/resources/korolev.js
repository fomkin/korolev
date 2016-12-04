(function(global) {

  global.Korolev = (function() {
    var root = null;
    var els = null;
    var addHandler = null;
    var removeHandler = null;
    var scheduledAddHandlerItems = [];
    var renderNum = 0;
    var rootListeners = [];

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
      SetRenderNum: function(n) {
        renderNum = n;
      },
      RegisterRoot: function(rootNode) {
        function aux(prefix, node) {
          var children = node.children;
          for (var i = 0; i < children.length; i++) {
            var child = children[i];
            var id = prefix + '_' + i;
            child.vId = id;
            els[id] = child;
            aux(id, child);
          }
        }
        root = rootNode;
        els = { "0": rootNode };
        aux("0", rootNode);
      },
      CleanRoot: function() {
        while (root.children.length > 0)
          root.removeChild(root.children[0]);
      },
      RegisterGlobalAddHandler: function(f) {
        addHandler = f;
      },
      RegisterGlobalRemoveHandler: function(f) {
        removeHandler = f;
      },
      RegisterGlobalEventHandler: function(eventHandler) {
        var listen = function(name, preventDefault) {
          var listener = function(event) {
            if (event.target.vId) {
              if (preventDefault) {
                event.preventDefault();
              }
              eventHandler(renderNum + ':' + event.target.vId + ':' + event.type);
            }
          }
          root.addEventListener(name, listener);
          rootListeners.push({ 'listener': listener, 'type': name });
        }
        listen('click');
        listen('submit', true);
        listen('mousedown');
        listen('mouseup');
      },
      UnregisterGlobalEventHandler: function() {
        rootListeners.forEach(function(item) {
          root.removeEventListener(item.type, item.listener);
        });
        rootListeners.length = 0;
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

    var deviceId = getCookie('device');
    var sessionId = Math.random().toString(36);
    console.log(deviceId);
    var root = document.body;
    var loc = window.location;
    var wsUri;
    if (loc.protocol === "https:") wsUri = "wss://";
    else wsUri = "ws://";

    wsUri += loc.host + loc.pathname +
      '/bridge' +
      '/' + deviceId +
      '/' + sessionId;
    global.Korolev.RegisterRoot(root);

    function initializeBridge() {
      var ws = new WebSocket(wsUri)
      ws.addEventListener('open', onOpen);
      Bridge.webSocket(ws).catch(function(errorEvent) {
        // Try to reconnect after 2s
        setTimeout(initializeBridge, 2000);
      });
    }

    function onOpen(event) {
      console.log("Connection opened.");
      event.target.addEventListener('close', onClose);
    }

    function onClose(event) {
      Korolev.UnregisterGlobalEventHandler();
      console.log("Connection closed. Global event handler us unregistered. Try to reconnect.");
      initializeBridge();
    }

    initializeBridge()
  });

  function getCookie(name) {
    var matches = document.cookie.match(new RegExp(
      "(?:^|; )" + name.replace(/([\.$?*|{}\(\)\[\]\\\/\+^])/g, '\\$1') + "=([^;]*)"
    ));
    return matches ? decodeURIComponent(matches[1]) : undefined;
  }
})(this);
