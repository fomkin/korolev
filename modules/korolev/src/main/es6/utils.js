export function getDeviceId() {
  return getCookie('deviceId');
}

/** @param {string} name */
function getCookie(name) {
  var matches = document.cookie.match(new RegExp(
    "(?:^|; )" + name.replace(/([.$?*|{}()\[\]\\\/+^])/g, '\\$1') + "=([^;]*)"
  ));
  return matches ? decodeURIComponent(matches[1]) : undefined;
}

export class ConnectionLostWidget {

  /** @param {string} template */
  constructor(template) {
    /** @type {?Element} */
    this._element = null;
    this._template = template;
  }

  show() {

    if (this._element !== null)
      return;

    // Parse template
    var element = document.createElement('div');
    element.innerHTML = this._template;
    element = element.children[0];

    // Append to document body
    document.body.appendChild(element);
    this._element = element;
  }

  hide() {
    if (this._element !== null) {
      document.body.removeChild(this._element);
      this._element = null;
    }
  }
}
