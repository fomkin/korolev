window.document.addEventListener("DOMContentLoaded", function() {
  Korolev.setProtocolDebugEnabled(true);
  var display = document.createElement("pre");
  display.setAttribute('style', 'height: 300px; overflow-y: scroll')
  display.innerHTML = '<div><strong id="debug-log-label">Client log</strong></div>';
  document.body.appendChild(display);
  console.log = function() {
    var line = document.createElement("div");
    for (var i = 0; i < arguments.length; i++) {
      if (arguments[i].indexOf("[0,0 ]") > -1) {
        document.getElementById('debug-log-label').textContent = 'Client log (connected)';
      }
      line.textContent += arguments[i];
    }
    display.appendChild(line);
    display.scrollTop = display.scrollHeight;
  };
  console.error = function() {
    var line = document.createElement("div");
    line.setAttribute("style", "color: red");
    for (var i = 0; i < arguments.length; i++)
      line.textContent = arguments[i];
    display.appendChild(line);
  };
  document.body.addEventListener('click', function(e) {
    console.log('! click on ' + e.target);
  });
  window.onerror = function(e) {
    console.error(e);
  };
});
