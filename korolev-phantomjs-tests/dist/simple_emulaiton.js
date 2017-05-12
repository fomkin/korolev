'use strict';

/**
 * Created by flystyle on 14.02.17.
 */

var webpage = require('webpage');
var fs = require('fs');
var tocsv = require('to-csv');

var storagePath = "dist/img/";
var url = 'http://localhost:8181/';
var requests = 500;

var counter = 0;
var data = [];
var startTime = Date.now();

function onResourceReceived(response) {
    console.log(Date.now() - startTime + ' ::: ' + response.stage + ' ::: ' + response.url);
}

function onResourceRequested(requestData, networkRequest) {
    console.log(Date.now() - startTime + ':Request:' + requestData.url);
}

function buildGraphics(status) {
    var html = '';
}

for (var i = 0; i < requests; i++) {
    var page = require('webpage').create();
    page.onResourceReceived = onResourceReceived;
    page.onResourceRequested = onResourceRequested;
    page.open(url + "?i=" + i, function () {
        page.evaluate(function () {
            var posts = document.getElementsByClassName("checkbox");
            for (var j = 1; j < posts.length; j++) {
                console.log(posts[j].className);
                posts[j].className = "checkbox checkbox__checked";
            }
        });
        var val = Date.now() - startTime;
        setTimeout(function () {
            data.push({ begin: startTime, end: val });
        }, 5000);

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