/**
 * Created by flystyle on 14.02.17.
 */

const webpage = require('webpage');
const fs = require('fs');
const tocsv = require('to-csv');

const storagePath = "dist/img/";
const url = 'http://localhost:8181/';
const requests = 500;

let counter = 0;
let data = []
var startTime = Date.now();

function onResourceReceived(response) {
    console.log((Date.now() - startTime) + ' ::: ' + response.stage + ' ::: ' + response.url);
}

function onResourceRequested(requestData, networkRequest) {
    console.log((Date.now() - startTime) + ':Request:' + requestData.url);
}

function buildGraphics(status) {
    let html = '';
}

for (var i = 0; i < requests; i++) {
    var page = require('webpage').create();
    page.onResourceReceived = onResourceReceived;
    page.onResourceRequested = onResourceRequested;
    page.open(url + "?i=" + i, () => {
        page.evaluate(() => {
            let posts = document.getElementsByClassName("checkbox");
            for (var j = 1; j < posts.length; j++) {
                console.log(posts[j].className);
                posts[j].className = "checkbox checkbox__checked";
            }
        });
        const val = Date.now() - startTime;
        setTimeout(() => {}, 1000);

        data.push({begin : startTime, end: val });
        ++counter;
        console.log('Complete ' + counter + ': ' + status + ':' + url);
        console.log('Execute time = ', val, "ms ");
        // page.clipRect = { top: 0, left: 0, width: 600, height: 700 };
        // page.render(storagePath + "img" + "_" + counter + ".png");

        startTime = Date.now();
        if (counter >= requests) {
            console.log(tocsv(data));
            phantom.exit();
        }
    });

}

