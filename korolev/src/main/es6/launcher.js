import { Connection } from './connection.js';
import { Bridge, setProtocolDebugEnabled } from './bridge.js';
import { ConnectionLostWidget, getDeviceId } from './utils.js';

window['Korolev'] = {
  'setProtocolDebugEnabled': setProtocolDebugEnabled,
  'swapElementInRegistry': () => console.log("Korolev is not ready")
};

window.document.addEventListener("DOMContentLoaded", () => {

  let reconnect = true
  let config = window['kfg'];
  let clw = new ConnectionLostWidget(config['clw']);
  let connection = new Connection(
    getDeviceId(),
    config['sid'],
    config['r'],
    window.location
  );

  window['Korolev']['disconnect'] = () => {
    reconnect = false;
    connection.disconnect();
  }

  window['Korolev']['connect'] = () => connection.connect();

  connection.dispatcher.addEventListener('open', () => {
    clw.hide();
    let bridge = new Bridge(config, connection);
    window['Korolev']['swapElementInRegistry'] = (a, b) => bridge._korolev.swapElementInRegistry(a, b);
    window['Korolev']['element'] = (id) => bridge._korolev.element(id);
    let closeHandler = (event) => {
      bridge.destroy();
      clw.show();
      connection
        .dispatcher
        .removeEventListener('close', closeHandler);
    };
    connection
      .dispatcher
      .addEventListener('close', closeHandler);
  });

  connection.dispatcher.addEventListener('close', () => {
    if (reconnect) {
      connection.connect();
    }
  });

  connection.connect();
});
