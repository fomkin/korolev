import { getCookie } from './utils.js';

export class Korolev {

  /** @param {Object} config */
  constructor(config) {
    /** @type {Object} */
    this.config = config;
    /** @type {HTMLElement} */
    this.root = document.body;
    /** @type {Object} */
    this.els = {};
    /** @type {?function(string)} */
    this.formDataProgressHandler = null;
    /** @type {number} */
    this.renderNum = 0;
    /** @type {Array} */
    this.rootListeners = [];
    /** @type {?function(string, boolean)} */
    this.listenFun = null;
    /** @type {?function(Event)} */
    this.historyHandler = null;
    /** @type {string} */
    this.initialPath = window.location.pathname;
  }

  /** @param {number} n */
  SetRenderNum(n) {
    this.renderNum = n;
  }

  /** @param {HTMLElement} rootNode */
  RegisterRoot(rootNode) {
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
    this.root = rootNode;
    this.els["1"] = rootNode;
    aux("1", rootNode);
  }

  CleanRoot() {
    while (this.root.children.length > 0)
      this.root.removeChild(this.root.children[0]);
  }

  /** @param {function(string)} f */
  RegisterFormDataProgressHandler(f) {
    this.formDataProgressHandler = f;
  }

  /** @param {function(string)} eventHandler */
  RegisterGlobalEventHandler(eventHandler) {
    let self = this;
    self.listenFun = function(name, preventDefault) {
      var listener = function(event) {
        if (event.target.vId) {
          if (preventDefault) {
            event.preventDefault();
          }
          eventHandler(self.renderNum + ':' + event.target.vId + ':' + event.type);
        }
      };
      self.root.addEventListener(name, listener);
      self.rootListeners.push({ 'listener': listener, 'type': name });
    };
    self.listenFun('submit', true);
  }

  UnregisterGlobalEventHandler() {
    let self = this;
    self.rootListeners.forEach(function(item) {
      self.root.removeEventListener(item.type, item.listener);
    });
    self.listenFun = null;
    self.rootListeners.length = 0;
  }

   /**
    * @param {string} type
    * @param {boolean} preventDefault
    */
  ListenEvent(type, preventDefault) {
    this.listenFun(type, preventDefault);
  }

   /**
    * @param {string} id
    * @param {string} childId
    * @param {string} tag
    */
  Create(id, childId, xmlNs, tag) {
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
  CreateText(id, childId, text) {
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
  Remove(id, childId) {
    var parent = this.els[id],
      child = this.els[childId];
    if (!parent) return;
    if (child) {
      parent.removeChild(child);
    }
  }

   /**
    * @param {string} id
    * @param {string} propertyName
    */
  ExtractProperty(id, propertyName) {
    var element = this.els[id];
    return element[propertyName];
  }

   /**
    * @param {string} id
    * @param {string} name
    * @param {string} value
    * @param {boolean} isProperty
    */
  SetAttr(id, xmlNs, name, value, isProperty) {
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
  RemoveAttr(id, xmlNs, name, isProperty) {
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
    */
  Focus(id) {
    var element = this.els[id];
    element.focus();
  }

   /**
    * @param {function(string)} handler
    */
  RegisterHistoryHandler(handler) {
    let self = this;
    self.historyHandler = function(/** @type {Event} */ event) {
      if (event.state === null) handler(self.initialPath);
      else handler(event.state);
    }
    window.addEventListener('popstate', self.historyHandler);
  }

  UnregisterHistoryHandler() {
    if (this.historyHandler !== null) {
      window.removeEventListener('popstate', this.historyHandler);
      this.historyHandler = null;
    }
  }

   /**
    * @param {string} path
    */
  ChangePageUrl(path) {
    if (path !== window.location.pathname)
      window.history.pushState(path, '', path);
  }

   /**
    * @param {string} id
    * @param {string} descriptor
    */
  UploadForm(id, descriptor) {
    let self = this;
    var form = self.els[id];
    var formData = new FormData(form);
    var request = new XMLHttpRequest();
    var deviceId = getCookie('device');
    var uri = self.config['serverRootPath'] +
      'bridge' +
      '/' + deviceId +
      '/' + self.config['sessionId'] +
      '/form-data' +
      '/' + descriptor;
    request.open("POST", uri, true);
    request.upload.onprogress = function(event) {
      var arg = [descriptor, event.loaded, event.total].join(':');
      self.formDataProgressHandler(arg);
    };
    request.send(formData);
  }

  ReloadCss() {
    var links = document.getElementsByTagName("link");
    for (var i = 0; i < links.length; i++) {
      var link = links[i];
      if (link.getAttribute("rel") === "stylesheet")
        link.href = link.href + "?refresh=" + new Date().getMilliseconds();
    }
  }
}
