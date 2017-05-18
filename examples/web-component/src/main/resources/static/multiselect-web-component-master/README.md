# Multiselect Web Component

A multiselect widget created using [Web Components](https://www.w3.org/TR/components-intro/).

![multiselect web component](http://tabalinas.github.io/multiselect-web-component/images/demo.png)


## Demo

[Multiselect Demo](http://tabalinas.github.io/multiselect-web-component)


## Usage

Import `multiselect.html` and use `<x-multiselect>` element. Define items with `<li>` elements. To make an item selected add `selected` attribute.

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <link rel="import" href="multiselect.html">
</head>
<body>
    <x-multiselect placeholder="Select Value">
        <li value="1" selected>Item 1</li>
        <li value="2">Item 2</li>
        <li value="3" selected>Item 3</li>
        <li value="4">Item 4</li>
    </x-multiselect>
</body>
</html>
```


## API

### Attribute `placeholder`

Add attribure `placeholder` to set multiselect placeholder (text to be displayed when no items are selected).

```html
    <x-multiselect placeholder="Multiselect Placeholder">
    ...
    </x-multiselect>
```

### Method `selectedItems()`

Returns selected item values as an Array.

```js
// returns an array of values, e.g. [1, 3]
var selectedItems = multiselect.selectedItems();
```

### Event `change`

Is fired each time selected items are changes.

```js
multiselect.addEventListener('change', function() {
    // print selected items to console
    console.log('Selected items:', this.selectedItems());
});
```

## Browser Support

Browser support is limited by support of [Web Components](http://caniuse.com/#search=components).

Add [webcomponentjs](https://github.com/webcomponents/webcomponentsjs) polyfill to be able to use the component in all modern browsers.