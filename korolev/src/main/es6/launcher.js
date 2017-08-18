import { Korolev } from './korolev.js';
import { Connection } from './connection.js';
import { Bridge, setProtocolDebugEnabled } from './bridge.js';
import { ConnectionLostWidget, getCookie } from './utils.js';

let config = window['KorolevConfig'];
let korolev = new Korolev(config);

// Korolev instance should be visible from
// global scope by legacy bridge design causes.
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
  'SetStyle': korolev.SetStyle.bind(korolev),
  'RemoveStyle': korolev.RemoveStyle.bind(korolev),
  'Focus': korolev.Focus.bind(korolev),
  'RegisterHistoryHandler': korolev.RegisterHistoryHandler.bind(korolev),
  'UnregisterHistoryHandler': korolev.UnregisterHistoryHandler.bind(korolev),
  'ChangePageUrl': korolev.ChangePageUrl.bind(korolev),
  'UploadForm': korolev.UploadForm.bind(korolev),
  'ReloadCss': korolev.ReloadCss.bind(korolev)
};

// Export `setProtocolDebugEnabled` function
// to global scope
window['Bridge'] = {
  'setProtocolDebugEnabled': setProtocolDebugEnabled
};

window.document.addEventListener("DOMContentLoaded", () => {

  korolev.RegisterRoot(window.document.body);

  let clw = new ConnectionLostWidget(config['connectionLostWidget']);

  let connection = new Connection(
    getCookie('device'),
    config['sessionId'],
    config['serverRootPath'],
    window.location
  );

  connection.dispatcher.addEventListener('open', () => {
    clw.hide();
    var bridge = new Bridge((data) => connection.send(JSON.stringify(data)));
    var messageHandler = (event) => bridge.receive(JSON.parse(event.data));
    var closeHandler = (event) => {
      connection.dispatcher.removeEventListener('message', messageHandler);
      connection.dispatcher.removeEventListener('close', closeHandler);
      korolev.UnregisterGlobalEventHandler();
      korolev.UnregisterHistoryHandler();
      clw.show();
    }
    connection.dispatcher.addEventListener('message', messageHandler);
    connection.dispatcher.addEventListener('close', closeHandler);
  });

  connection.dispatcher.addEventListener('close', () => {
    connection.connect();
  });

  connection.connect();
});
