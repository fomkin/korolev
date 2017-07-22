import { getCookie } from './utils.js';
import { Korolev } from './korolev.js';
import { createWebSocketBridge } from './bridge.js';

const MinReconnectTimeout = 200;
const MaxReconnectTimeout = 5000;

let KorolevConfig = window['KorolevConfig'];
let korolev = new Korolev(KorolevConfig);
 
window['Korolev'] = {
  'SetRenderNum': korolev.SetRenderNum.bind(korolev),
  'RegisterRoot': korolev.RegisterRoot.bind(korolev),
  'CleanRoot': korolev.CleanRoot.bind(korolev),
  'RegisterFormDataProgressHandler': korolev.RegisterFormDataProgressHandler.bind(korolev),
  'RegisterGlobalEventHandler': korolev.RegisterGlobalEventHandler.bind(korolev),
  'UnregisterGlobalEventHandler': korolev.UnregisterGlobalEventHandler.bind(korolev),
  'ListenEvent': korolev.ListenEvent.bind(korolev),
  'Create': korolev.Create.bind(korolev),
  'CreateText': korolev.CreateText.bind(korolev),
  'Remove': korolev.Remove.bind(korolev),
  'ExtractProperty': korolev.ExtractProperty.bind(korolev),
  'SetAttr': korolev.SetAttr.bind(korolev),
  'RemoveAttr': korolev.RemoveAttr.bind(korolev),
  'Focus': korolev.Focus.bind(korolev),
  'RegisterHistoryHandler': korolev.RegisterHistoryHandler.bind(korolev),
  'UnregisterHistoryHandler': korolev.UnregisterHistoryHandler.bind(korolev),
  'ChangePageUrl': korolev.ChangePageUrl.bind(korolev),
  'UploadForm': korolev.UploadForm.bind(korolev),
  'ReloadCss': korolev.ReloadCss.bind(korolev)
} 

window.document.addEventListener("DOMContentLoaded", function() {

  var deviceId = getCookie('device');
  var root = window.document.body;
  var loc = window.location;
  var connectionType = null;
  var selectedConnectionType = null;
  var reconnectTimeout = MinReconnectTimeout;

  korolev.RegisterRoot(root);

  function initializeBridgeWs() {
    // When WebSocket is not supported always use Long Polling
    if (window.WebSocket === undefined) {
      initializeBridgeLongPolling();
      return;
    }
    connectionType = "ws";
    var uri, ws;
    if (loc.protocol === "https:") uri = "wss://";
    else uri = "ws://";
    uri += loc.host + KorolevConfig['serverRootPath'] +
      'bridge/web-socket' +
      '/' + deviceId +
      '/' + KorolevConfig['sessionId'];
    console.log('Try to open connection to ' + uri + ' using WebSocket');
    ws = new WebSocket(uri);
    ws.addEventListener('open', onOpen);

    createWebSocketBridge(ws, function(res, err) {
      if (err) reconnect()
    });

  }

  function initializeBridgeLongPolling() {
    connectionType = "lp";
    var uriPrefix = loc.protocol + "//" + loc.host + KorolevConfig['serverRootPath'] +
      'bridge/long-polling' +
      '/' + deviceId +
      '/' + KorolevConfig['sessionId'] +
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
                if (typeof Event === "function") {
                  event = new Event('open');
                } else {
                  event = document.createEvent('Event');
                  event.initEvent('open', false, false);
                }
                target.dispatchEvent(event);
              }
              if (typeof MessageEvent === "function") {
                event = new MessageEvent('message', { 'data': request.responseText });
              } else {
                event = document.createEvent('MessageEvent');
                event.initMessageEvent('message', true, false, request.responseText, uriPrefix, "", window);
              }
              target.dispatchEvent(event);
              lpSubscribe(target, false);
              break;
            case 410:
              if (typeof Event === "function") {
                event = new Event('close');
              } else {
                event = document.createEvent('Event');
                event.initEvent('close', false, false);
              }
              target.dispatchEvent(event);
              break;
            case 400:
              if (typeof ErrorEvent === "function") {
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
            default:
              if (typeof ErrorEvent === "function") {
              var message = "Unknown error";
                event = new ErrorEvent('error', {
                  error: new Error(message),
                  message: message
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
            case 0:
            case 400:
              if (typeof ErrorEvent === "function") {
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

    var fakeWs = window.document.createDocumentFragment();
    fakeWs.close = function() {
      var event = new Event('close');
      fakeWs.dispatchEvent(event);
    };
    fakeWs.send = function(message) {
      lpPublish(fakeWs, message);
    };
    fakeWs.addEventListener('open', onOpen);
    createWebSocketBridge(fakeWs, function(resolve, err) {
      if (err) reconnect()
    });
    lpSubscribe(fakeWs, true);
  }

  var connectionLostWidget = null;

  function reconnect() {
    // Create connection lost widget
    if (connectionLostWidget === null) {
      connectionLostWidget = document.createElement('div');
      connectionLostWidget.innerHTML = KorolevConfig['connectionLostWidget'];
      connectionLostWidget = connectionLostWidget.children[0];
      document.body.appendChild(connectionLostWidget);
    }
    // Prepare reconnecting
    korolev.UnregisterGlobalEventHandler();
    korolev.UnregisterHistoryHandler();
    console.log("Connection closed. window event handler us unregistered. Try to reconnect.");
    if (selectedConnectionType === 'ws') {
      setTimeout(initializeBridgeWs, reconnectTimeout);
    }
    else {
      if (connectionType === 'ws') setTimeout(initializeBridgeLongPolling, reconnectTimeout);
      else setTimeout(initializeBridgeWs, reconnectTimeout);
    }
    reconnectTimeout = Math.min(reconnectTimeout * 2, MaxReconnectTimeout);
  }

  function onOpen(event) {
    if (connectionLostWidget !== null) {
      document.body.removeChild(connectionLostWidget);
      connectionLostWidget = null;
    }
    console.log("Connection opened.");
    selectedConnectionType = connectionType;
    reconnectTimeout = MinReconnectTimeout;
    event.target.addEventListener('close', onClose);
  }

  function onClose(event) {
    reconnect()
  }

  // First attempt is always should be done using WebSocket
  initializeBridgeWs();
});
