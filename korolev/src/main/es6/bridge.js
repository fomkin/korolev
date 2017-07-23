const LinkPrefix = '@link:';
const ArrayPrefix = '@arr:';
const ObjPrefix = '@obj:';
const UnitResult = "@unit";
const NullResult = "@null";
const HookSuccess = "@hook_success";
const HookFailure = "@hook_failure";
const LinkNotFound = "Link no found";
const ProtocolDebugEnabledKey = "$bridge.protocolDebugEnabled";

var protocolDebugEnabled = window.localStorage.getItem(ProtocolDebugEnabledKey) === 'true';

export class Bridge {

  /** @param {function(*)} postMessageFunction */
  constructor(postMessageFunction) {

    this.postMessageFunction = postMessageFunction;
    this.links = { 'global': window };
    this.initializationCallbacks = [];
    this.initialized = false;

    window._bridgeLink = 'global';
  }

  //---------------------------------------------------------------------------
  //
  //  Private methods
  //
  //---------------------------------------------------------------------------

  /** @private */
  _postMessage(data) {
    var res = data[2], value;
    if (protocolDebugEnabled)
      console.log('<-', data);
    this.postMessageFunction(data);
  }


  /** @private */
  _getLink(id) {
    return this.links[id];
  }

  /** @private */
  _unpackArgs(args) {
    var l = args.length,
        i = 0,
        arg = null,
        id = null,
        tpe = null;
    for (i = 0; i < l; i += 1) {
      arg = args[i];
      tpe = typeof arg;
      if (tpe === 'string' && arg.indexOf(LinkPrefix) === 0) {
        id = arg.substring(LinkPrefix.length);
        args[i] = this._getLink(id);
      }
      else if (tpe === 'object' && arg instanceof Array) {
        this._unpackArgs(arg)
      }
    }
    return args;
  }

  /** @private */
  _packResult(arg) {
    if (arg === undefined) {
      return UnitResult;
    }
    if (arg === null) {
      return NullResult;
    }
    if (typeof arg === 'object') {
      var id = arg._bridgeLink;
      if (arg instanceof Array) {
        return ArrayPrefix + id;
      }
      return ObjPrefix + id;
    }
    return arg;
  }

  /** @private */
  _createHook(reqId, success, cb) {
    let self = this;
    return function hook(res) {
      cb([reqId, success, self._packResult(res)]);
    };
  }

  /** @private */
  _receiveCall(reqId, args, cb) {
    let self = this;
    var obj = args[0],
        res = null,
        err = null,
        name = null,
        callArgs = null,
        hasHooks = false,
        arg = null,
        i = 0;
    if (!obj) {
      cb([reqId, false, LinkNotFound]);
      return;
    }
    name = args[1];
    callArgs = args.slice(2);
    for (i = 0; i < callArgs.length; i++) {
      arg = callArgs[i];
      if (arg === HookSuccess) {
        callArgs[i] = self._createHook(reqId, true, cb);
        hasHooks = true;
      } else if (arg === HookFailure) {
        callArgs[i] = self._createHook(reqId, false, cb);
        hasHooks = true;
      }
    }
    try {
      if (hasHooks) {
        obj[name].apply(obj, callArgs);
      } else {
        res = self._packResult(obj[name].apply(obj, callArgs));
        if (res === undefined) {
          res = UnitResult;
        }
        cb([reqId, true, res]);
      }
    } catch (exception) {
      err = obj._bridgeLink + '.' + name + '(' + callArgs + ') call failure: ' + exception;
      console.error(err);
      cb([reqId, false, err]);
    }
  }

  /** @private */
  _receiveGet(reqId, args, cb) {
    var obj = args[0],
        res = null,
        err = null;
    if (obj) {
      res = obj[args[1]];
      if (res === undefined) {
        err = obj + "." + args[1] + " is undefined";
        cb([reqId, false, err]);
      } else {
        cb([reqId, true, this._packResult(res)]);
      }
    } else {
      cb([reqId, false, LinkNotFound]);
    }
  }

  /** @private */
  _receiveGetAndSaveAs(reqId, args) {
    let subject = args[0];
    if (!subject) {
      this._postMessage([reqId, false, LinkNotFound]);
      return;
    }
    let property = args[1];
    let newId = args[2];
    let object = subject[property];
    if (!object) {
      this._postMessage([reqId, false, `Property ${property} not found`]);
    }
    else if (typeof object !== "object") {
      this._postMessage([reqId, false, `${property} is ${typeof object}`]);
    }
    else {
      object._bridgeLink = newId;
      this.links[newId] = object;
      this._postMessage([reqId, true, this._packResult(object)]);
    }
  }

  /** @private */
  _notifyInitialized() {
    for (var i = 0; i < this.initializationCallbacks.length; i++)
      this.initializationCallbacks[i](self);
    this.initializationCallbacks = null;
  }

  //---------------------------------------------------------------------------
  //
  //  Public methods
  //
  //---------------------------------------------------------------------------

  /** @param {*} data */
  receive(data) {
    let self = this;

    if (protocolDebugEnabled) console.log('->', data);

    // requests batch processing)
    if (data[0] === "batch") {
      var requests = data.slice(1);
      requests.forEach(function (request) {
        self.receive(request);
      });
      return;
    }

    var reqId = data[0],
        method = data[1],
        rawArgs = data.slice(2),
        args = self._unpackArgs(rawArgs.concat());

    switch (method) {

      // Misc
      case 'init':
        self.initialized = true;
        self._notifyInitialized();
        self._postMessage([reqId, true, UnitResult]);
        break;

      case 'registerCallback':
        (function BridgeRegisterCallback() {
          var callbackId = args[0];
          function callback(arg) {
            self._postMessage([-1, callbackId, self._packResult(arg)]);
          }
          self.links[callbackId] = callback;
          callback._bridgeLink = callbackId;
          self._postMessage([reqId, true, ObjPrefix + callbackId]);
        })();
        break;

      // Object methods
      case 'getAndSaveAs':
        this._receiveGetAndSaveAs(reqId, args);
        break;

      case 'get':
        self._receiveGet(reqId, args, (data) => self._postMessage(data));
        break;

      case 'call':
        self._receiveCall(reqId, args, (data) => self._postMessage(data));
        break;
    }
  }

  /** param {function(?Bridge, ?Error)} cb */
  onInitialize(cb) {
    if (this.initialized) {
      cb(self);
      return;
    }
    this.initializationCallbacks.push(cb);
  }
}

/** @param {boolean} value */
export function setProtocolDebugEnabled(value) {
  window.localStorage.setItem(ProtocolDebugEnabledKey, value.toString());
  protocolDebugEnabled = value;
}
