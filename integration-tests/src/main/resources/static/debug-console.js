window.document.addEventListener("DOMContentLoaded", function() {
  Bridge.setProtocolDebugEnabled(true);
  var display = document.createElement("pre");
  display.innerHTML = "<div><strong>Client log</strong></div>"
  document.body.appendChild(display);
  console.log = function() {
    var line = document.createElement("div");
    for (var i = 0; i < arguments.length; i++)
      line.textContent = arguments[i];
    display.appendChild(line);
  }
  console.error = function() {
    var line = document.createElement("div");
    line.setAttribute("style", "color: red");
    for (var i = 0; i < arguments.length; i++)
      line.textContent = arguments[i];
    display.appendChild(line);
  }
  document.body.addEventListener('click', function(e) {
    console.log('! click on ' + e.target);
  });
  window.onerror = function(e) {
    console.error(e);
  };
});
