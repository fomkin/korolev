import { getCookie } from './utils.js';

/** @enum {number} */
export const CallbackType = {
  DOM_EVENT: 0, // `$renderNum:$elementId:$eventType`
  FORM_DATA_PROGRESS: 1, // `$descriptor:$loaded:$total`
  EXTRACT_PROPERTY_RESPONSE: 2, // `$descriptor:$value`
  HISTORY: 3  // URL
}

export const PropertyType = {
  STRING: 0,
  NUMBER: 1,
  BOOLEAN: 2,
  OBJECT: 3,
  ERROR: 4
}

export class Korolev {

  /**
   * @param {Object} config
   * @param {function(CallbackType, string)} callback
   */
  constructor(config, callback) {
    /** @type {Object} */
    this.config = config;
    /** @type {HTMLElement} */
    this.root = document.body;
    /** @type {Object} */
    this.els = {};
    /** @type {number} */
    this.renderNum = 0;
    /** @type {Array} */
    this.rootListeners = [];
    /** @type {?function(Event)} */
    this.historyHandler = null;
    /** @type {string} */
    this.initialPath = window.location.pathname;
    /** @type {function(CallbackType, string)} */
    this.callback = callback;

    this.listenRoot = (name, preventDefault) => {
      var listener = (event) => {
        if (event.target.vId) {
          if (preventDefault) {
            event.preventDefault();
          }
          this.callback(CallbackType.DOM_EVENT, this.renderNum + ':' + event.target.vId + ':' + event.type);
        }
      };
      this.root.addEventListener(name, listener);
      this.rootListeners.push({ 'listener': listener, 'type': name });
    };

    this.listenRoot('submit', true);

    this.historyHandler = (/** @type {Event} */ event) => {
      if (event.state === null) callback(CallbackType.HISTORY, this.initialPath);
      else callback(CallbackType.HISTORY, event.state);
    }

    window.addEventListener('popstate', this.historyHandler);
  }
  
  destroy() {
    // Remove root listeners
    this.rootListeners.forEach((o) => this.root.removeEventListener(o.type, o.listener));
    // Remove popstate handler
    window.removeEventListener('popstate', this.historyHandler);
  }
  
  /** @param {number} n */
  setRenderNum(n) {
    this.renderNum = n;
  }

  /** @param {HTMLElement} rootNode */
  registerRoot(rootNode) {
    let self = this;
    function aux(prefix, node) {
      var children = node.childNodes;
      for (var i = 0; i < children.length; i++) {
        var child = children[i];
        var id = prefix + '_' + (i + 1);
        child.vId = id;
        self.els[id] = child;
        aux(id, child);
      }
    }
    self.root = rootNode;
    self.els["1"] = rootNode;
    aux("1", rootNode);
  }

  cleanRoot() {
    while (this.root.children.length > 0)
      this.root.removeChild(this.root.children[0]);
  }

   /**
    * @param {string} type
    * @param {boolean} preventDefault
    */
  listenEvent(type, preventDefault) {
    this.listenRoot(type, preventDefault);
  }

  /**
   * @param {Array} data
   */
  modifyDom(data) {
    // Reverse data to use pop() instead of shift()
    // pop() faster than shift()
    let atad = data.reverse();
    let r = atad.pop.bind(atad);
    while (data.length > 0) {
      switch (r()) {
        case 0: this.create(r(), r(), r(), r()); break;
        case 1: this.createText(r(), r(), r()); break;
        case 2: this.remove(r(), r()); break;
        case 3: this.setAttr(r(), r(), r(), r(), r()); break;
        case 4: this.removeAttr(r(), r(), r(), r()); break;
        case 5: this.setStyle(r(), r(), r()); break;
        case 6: this.removeStyle(r(), r()); break;
      }
    }
  }
  
   /**
    * @param {string} id
    * @param {string} childId
    * @param {string} tag
    */
  create(id, childId, xmlNs, tag) {
    var parent = this.els[id],
      child = this.els[childId],
      newElement;
    if (!parent) return;
    if (xmlNs === 0) {
      newElement = document.createElement(tag);
    } else {
      newElement = document.createElementNS(xmlNs, tag);
    }
    newElement.vId = childId;
    if (child && child.parentNode === parent) {
      parent.replaceChild(newElement, child);
    } else {
      parent.appendChild(newElement);
    }
    this.els[childId] = newElement;
  }

   /**
    * @param {string} id
    * @param {string} childId
    * @param {string} text
    */
  createText(id, childId, text) {
    var parent = this.els[id],
      child = this.els[childId],
      newElement;
    if (!parent) return;
    newElement = document.createTextNode(text);
    newElement.vId = childId;
    if (child && child.parentNode === parent) {
      parent.replaceChild(newElement, child);
    } else {
      parent.appendChild(newElement);
    }
    this.els[childId] = newElement;
  }

   /**
    * @param {string} id
    * @param {string} childId
    */
  remove(id, childId) {
    var parent = this.els[id],
      child = this.els[childId];
    if (!parent) return;
    if (child) {
      parent.removeChild(child);
    }
  }

   /**
    * @param {string} descriptor
    * @param {string} id
    * @param {string} propertyName
    */
  extractProperty(descriptor, id, propertyName) {
    let element = this.els[id];
    let value = element[propertyName];
    var result, type;
    switch (typeof value) {
      case 'undefined':
        type = PropertyType.ERROR;
        result = `${propertyName} is undefined`;
        break;
      case 'function':
        type = PropertyType.ERROR;
        result = `${propertyName} is a function`;
        break;
      case 'object':
        type = PropertyType.OBJECT;
        result = JSON.stringify(value);
        break;
      case 'string':
        type = PropertyType.STRING;
        result = value;
        break;
      case 'number':
        type = PropertyType.NUMBER;
        result = value;
        break;
      case 'boolean':
        type = PropertyType.BOOLEAN;
        result = value;
        break;
    }
    this.callback(
      CallbackType.EXTRACT_PROPERTY_RESPONSE,
      `${descriptor}:${type}:${result}`
    );
  }

   /**
    * @param {string} id
    * @param {string} name
    * @param {string} value
    * @param {boolean} isProperty
    */
  setAttr(id, xmlNs, name, value, isProperty) {
    var element = this.els[id];
    if (isProperty) element[name] = value;
    else if (xmlNs === 0) {
      element.setAttribute(name, value);
    } else {
      element.setAttributeNS(xmlNs, name, value);
    }
  }

   /**
    * @param {string} id
    * @param {string} name
    * @param {boolean} isProperty
    */
  removeAttr(id, xmlNs, name, isProperty) {
    var element = this.els[id];
    if (isProperty) element[name] = undefined;
    else if (xmlNs === 0) {
      element.removeAttribute(name);
    } else {
      element.removeAttributeNS(xmlNs, name);
    }
  }

   /**
    * @param {string} id
    * @param {string} name
    * @param {string} value
    */
  setStyle(id, name, value) {
    var element = this.els[id];
    element.style[name] = value;
  }

   /**
    * @param {string} id
    * @param {string} name
    */
  removeStyle(id, name) {
    var element = this.els[id];
    element.style[name] = null;
  }

   /**
    * @param {string} id
    */
  focus(id) {
    var element = this.els[id];
    element.focus();
  }

   /**
    * @param {string} path
    */
  changePageUrl(path) {
    if (path !== window.location.pathname)
      window.history.pushState(path, '', path);
  }

   /**
    * @param {string} id
    * @param {string} descriptor
    */
  uploadForm(id, descriptor) {
    let self = this;
    var form = self.els[id];
    var formData = new FormData(form);
    var request = new XMLHttpRequest();
    var deviceId = getCookie('device');
    var uri = self.config['r'] +
      'bridge' +
      '/' + deviceId +
      '/' + self.config['sid'] +
      '/form-data' +
      '/' + descriptor;
    request.open("POST", uri, true);
    request.upload.onprogress = function(event) {
      var arg = [descriptor, event.loaded, event.total].join(':');
      self.callback(CallbackType.FORM_DATA_PROGRESS, arg);
    };
    request.send(formData);
  }

  reloadCss() {
    var links = document.getElementsByTagName("link");
    for (var i = 0; i < links.length; i++) {
      var link = links[i];
      if (link.getAttribute("rel") === "stylesheet")
        link.href = link.href + "?refresh=" + new Date().getMilliseconds();
    }
  }
}
