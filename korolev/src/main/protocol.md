## Procedure codes

From server to the client

```json
[
  3,              // procedure code of ExtractProperty
  "1_1",          // id
  "propertyName", // propertyName
  0               // descriptor 
]
```

 * 0 - SetRenderNum(n)
 * 1 - CleanRoot()
 * 2 - ListenEvent(type, preventDefault)
 * 3 - ExtractProperty(descriptor, id, propertyName)
 * 4 - ModifyDOM(commands)
 * 5 - Focus(id) {
 * 6 - ChangePageUrl(path)
 * 7 - UploadForm(id, descriptor)
 * 8 - ReloadCss()
 * 9 - Keep-alive message from server (noop)
 * 10 - EvalJs(descriptor, code)
 * 11 - ExtractEventData(descriptor, renderNum)
 * 12 - ListFiles(id, descriptor)
 * 13 - UploadFile(id, descriptor, fileName)
 * 14 - ResetForm(id)

### Modify dom commands

```json
[
  4,              // procedure code of ModifyDOM
  0,              // procedure code of Create
  "1_1",          // id
  "1_1_1",        // childId
  0,              // xmlNs
  "div",          // tag
  5,              // procedure code of SetStyle
  "width",        // name
  "100px"         // value
]
```

 * 0 - Create(id, childId, xmlNs, tag)
 * 1 - CreateText(id, childId, text)
 * 2 - Remove(id, childId)
 * 3 - SetAttr(id, xmlNs, name, value, isProperty)
 * 4 - RemoveAttr(id, xmlNs, name, isProperty)
 * 5 - SetStyle(id, name, value)
 * 6 - RemoveStyle(id, name)

## Callbacks

From the client to the server

```json
[
  0,             // DOM event
  "0:1_1:click"  // data
]
```

 * 0 - DOM Event. Data: `$renderNum:$elementId:$eventType`
 * 1 - FormData progress. Data: `$descriptor:$loaded:$total` 
 * 2 - ExtractProperty response. Data:`$descriptor:$type:$value`
 * 3 - History Event. Data: URL
 * 4 - EvalJs response. Data: `$descriptor:$dataJson` 
 * 5 - ExtractEventData response. Data: `$descriptor:$dataJson`
 * 6 - Heartbeat. Data: `$descriptor` 
