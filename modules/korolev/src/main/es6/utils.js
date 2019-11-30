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

export function encodeRFC5987ValueChars(str) {
  return encodeURIComponent(str).
      // Note that although RFC3986 reserves "!", RFC5987 does not,
      // so we do not need to escape it
      replace(/['()]/g, escape). // i.e., %27 %28 %29
      replace(/\*/g, '%2A').
      // The following are not required for percent-encoding per RFC5987,
      // so we can allow for a little better readability over the wire: |`^
      replace(/%(?:7C|60|5E)/g, unescape);
}
