(function(global) {

  global.Korolev = (function() {
    var root = null,
      els = null,
      addHandler = null,
      removeHandler = null,
      scheduledAddHandlerItems = [],
      formDataProgressHandler = null,
      renderNum = 0,
      rootListeners = [],
      listenFun = null,
      historyHandler = null,
      initialPath = global.location.pathname;

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
          var children = node.childNodes;
          for (var i = 0; i < children.length; i++) {
            var child = children[i];
            var id = prefix + '_' + (i + 1);
            child.vId = id;
            els[id] = child;
            aux(id, child);
          }
        }
        root = rootNode;
        els = { "1": rootNode };
        aux("1", rootNode);
      },
      CleanRoot: function() {
        while (root.children.length > 0)
          root.removeChild(root.children[0]);
      },
      RegisterGlobalAddHandler: function(f) {
        addHandler = f;
      },
      RegisterFormDataProgressHandler: function(f) {
        formDataProgressHandler = f;
      },
      RegisterGlobalRemoveHandler: function(f) {
        removeHandler = f;
      },
      RegisterGlobalEventHandler: function(eventHandler) {
        listenFun = function(name, preventDefault) {
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
        listenFun('submit', true);
      },
      UnregisterGlobalEventHandler: function() {
        rootListeners.forEach(function(item) {
          root.removeEventListener(item.type, item.listener);
        });
        listenFun = null;
        rootListeners.length = 0;
      },
      ListenEvent: function(type, preventDefault) {
        listenFun(type, preventDefault);
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
      },
      RegisterHistoryHandler: function(handler) {
        global.addEventListener('popstate', historyHandler = function(event) {
          if (event.state === null) handler(initialPath);
          else handler(event.state);
        });
      },
      UnregisterHistoryHandler: function() {
        if (historyHandler !== null) {
          global.removeEventListener('popstate', historyHandler);
          historyHandler = null;
        }
      },
      ChangePageUrl: function(path) {
        console.log(path);
        if (path !== global.location.pathname)
          global.history.pushState(path, '', path);
      },
      UploadForm: function(id, descriptor) {
        var form = els[id];
        var formData = new FormData(form);
        var request = new XMLHttpRequest();
        var deviceId = getCookie('device');
        var uri = KorolevServerRootPath +
          'bridge' +
          '/' + deviceId +
          '/' + KorolevSessionId +
          '/form-data' +
          '/' + descriptor;
        request.open("POST", uri, true);
        request.upload.onprogress = function(event) {
          var arg = [descriptor, event.loaded, event.total].join(':');
          formDataProgressHandler(arg);
        }
        request.send(formData);
        return;
      }
    }
  })();

  global.document.addEventListener("DOMContentLoaded", function() {

    var deviceId = getCookie('device');
    var root = global.document.body;
    var loc = global.location;
    var connectionType = null;

    global.Korolev.RegisterRoot(root);

    function initializeBridgeWs() {    
      connectionType = "ws";
      var uri, ws;
      if (loc.protocol === "https:") uri = "wss://";
      else uri = "ws://";
      uri += loc.host + KorolevServerRootPath +
        'bridge/web-socket' +
        '/' + deviceId +
        '/' + KorolevSessionId;
      console.log('Try to open connection to ' + uri + ' using WebSocket');
      ws = new WebSocket(uri);
      ws.addEventListener('open', onOpen);
      global.Korolev.connection = ws;

      Bridge.webSocket(ws, function(res, err) {
        if (err) {
          setTimeout(initializeBridgeLongPolling, 1);
        }
      });

    }

    function initializeBridgeLongPolling() {
      connectionType = "lp";
      var uriPrefix = loc.protocol + "//" + loc.host + KorolevServerRootPath +
        'bridge/long-polling' +
        '/' + deviceId +
        '/' + KorolevSessionId +
        '/';

      console.log('Try to open connection to ' + uriPrefix + ' using long polling');

      function lpSubscribe(target, firstTime) {
        var request = new XMLHttpRequest();
        request.addEventListener('readystatechange', function() {
          if (request.readyState === 4) {
            var event = null;
            switch (request.status) {
              case 200:
                if (firstTime) {
                  if (typeof window.Event === "function") {
                    event = new Event('open');
                  } else {
                    event = document.createEvent('Event');
                    event.initEvent('open', false, false);
                  }
                  target.dispatchEvent(event);
                }
                if (typeof window.MessageEvent === "function") {
                  event = new MessageEvent('message', { 'data': request.responseText });
                } else {
                  event = document.createEvent('MessageEvent');
                  event.initMessageEvent('message', true, false, request.responseText, uriPrefix, "", window);
                }
                target.dispatchEvent(event);
                lpSubscribe(target, false);
                break;
              case 410:
                if (typeof window.Event === "function") {
                  event = new Event('close');
                } else {
                  event = document.createEvent('Event');
                  event.initEvent('close', false, false);
                }
                target.dispatchEvent(event);
                break;
              case 400:
                if (typeof window.ErrorEvent === "function") {
                  event = new ErrorEvent('error', {
                    error: new Error(request.responseText),
                    message: request.responseText
                  });
                } else {
                  event = document.createEvent('Event');
                  event.initEvent('error', false, false);
                }
                target.dispatchEvent(event);
                break;
            }
          }
        });
//        console.log("Polling from " + uriPrefix + 'subscribe');
        request.open('GET', uriPrefix + 'subscribe', true);
        request.send('');
      }

      function lpPublish(target, message) {
        var request = new XMLHttpRequest();
        request.addEventListener('readystatechange', function() {
          if (request.readyState === 4) {
            var event = null;
            switch (request.status) {
              case 400:
                if (typeof window.ErrorEvent === "function") {
                  event = new ErrorEvent('error', {
                    error: new Error(request.responseText),
                    message: request.responseText
                  });
                } else {
                  event = document.createEvent('Event');
                  event.initEvent('error', false, false);
                }
                target.dispatchEvent(event);
                break;
            }
          }
        });
        request.open('POST', uriPrefix + 'publish', true);
        request.setRequestHeader("Content-Type", "application/json");
        request.send(message);
      }

      var fakeWs = global.document.createDocumentFragment()
      global.Korolev.connection = fakeWs;
      fakeWs.send = function(message) {
        lpPublish(fakeWs, message);
      }
      fakeWs.addEventListener('open', onOpen);
      Bridge.webSocket(fakeWs, function(resolve, err) {
        if (err) {
          setTimeout(initializeBridgeWs, 2000);
        }
      });
      lpSubscribe(fakeWs, true);
    }

    function onOpen(event) {
      console.log("Connection opened.");
      event.target.addEventListener('close', onClose);
    }

    function onClose(event) {
      Korolev.UnregisterGlobalEventHandler();
      Korolev.UnregisterHistoryHandler();
      console.log("Connection closed. Global event handler us unregistered. Try to reconnect.");
      if (connectionType == "ws") setTimeout(initializeBridgeLongPolling, 2000);
      else setTimeout(initializeBridgeWs, 2000);
    }

    if (window.WebSocket === undefined)
      initializeBridgeLongPolling();
    else initializeBridgeWs();
  });

  function getCookie(name) {
    var matches = document.cookie.match(new RegExp(
      "(?:^|; )" + name.replace(/([\.$?*|{}\(\)\[\]\\\/\+^])/g, '\\$1') + "=([^;]*)"
    ));
    return matches ? decodeURIComponent(matches[1]) : undefined;
  }
})(this);
