/**
 * Created by flystyle on 14.02.17.
 */

const url = "http://localhost:8181/";
const N = 30;
const storagePath = "/Users/flystyle/ScalaProjects/korolev/korolev-phantomjs-tests/dist/img/";
var cnt = 0;

let data = []

function onResourceReceived(response) {
    console.log((Date.now() - startTime) + ':' + response.stage + ':' + response.url);
}

function onResourceRequested(requestData, networkRequest) {
    console.log((Date.now() - startTime) + ':Request:' + requestData.url);
}

function buildGraphics(status) {
    let html = '';
}

var startTime = Date.now();
for (var i = 0; i < N; i++) {
    var page = require('webpage').create();
    page.onResourceReceived = onResourceReceived;
    page.onResourceRequested = onResourceRequested;
    const val = Date.now() - startTime;
    page.open(url + "?i=" + i, () => {
        ++cnt;
        console.log('Complete ' + cnt + ': ' + status + ':' + url);
        console.log('Execute time = ', val, "ms ");
        startTime = Date.now();
        page.clipRect = { top: 0, left: 0, width: 600, height: 700 };

        page.render(storagePath + "img" + "_" + cnt + ".png");
        if (cnt >= N) {
            phantom.exit();
        }
    });

}