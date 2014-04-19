'use strict';

requirejs.config({
    baseUrl: '.',
    paths: {
        'jasmine': 'jasmine-2.0.0/jasmine',
        'jasmine-html': 'jasmine-2.0.0/jasmine-html',
        'boot': 'jasmine-2.0.0/boot',
        jquery: 'lib/jquery-2.1.0',
        'jquery.simulate': 'test/jquery.simulate'
    },
    shim: {
        jasmine: {
            exports: 'jasmine'
        },
        'jasmine-html': {
            deps: ['jasmine'],
            exports: 'jasmine'
        },
        boot: {
            deps: [ 'jasmine', 'jasmine-html'],
            exports: 'jasmine'
        },
        jquery: {
          exports: 'jQuery'
        },
        'jquery.simulate': {
          deps: ['jquery']
        }
    }
});

require(['boot','jquery.simulate'], function() {
    require(["spec/dashboardSpec"], function() {
        // onload has already happened by time require executes. Run fcn boot.js setup.
        window.onload();
    });
});
