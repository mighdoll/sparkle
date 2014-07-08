define(["jquery","sg/data"], function($, dataApi) {
    'use strict';

    describe("DataApi Columns Test Suite", function () {
        it("should get columns", function (done) {
            var promise = dataApi.getDataSetColumns("epochs");
            promise.then(function (data) {
                expect(data).toBeDefined();
                expect($.isArray(data)).toBeTruthy();
                expect(data.length).toEqual(3);
                expect(data[0]).toEqual("count");
                expect(data[1]).toEqual("p90");
                expect(data[2]).toEqual("p99");
                done();
            },
            function (err) {
                expect(err.status).toEqual(200);
                done();
            });
        });

        it("should get 404 for an unknown dataset", function (done) {
            var promise = dataApi.getDataSetColumns("does/not/exist");
            promise.then(function (data) {
                expect(data).toBeUndefined();
                done();
            },
            function (err) {
                expect(err.status).toEqual(404);
                done();
            });
        });

        it("should get 404 for no dataset specified", function (done) {
            var promise = dataApi.getDataSetColumns("");
            promise.then(function (data) {
                expect(data).toBeUndefined();
                expect(true).toBeFalsy();
                done();
            },
            function (err) {
                expect(err.status).toEqual(404);
                done();
            });
        });
    });

    describe("DataApi DataSet Children Test Suite", function () {
        it("should get epochs.csv as child", function (done) {
            var promise = dataApi.getDataSetChildren("subdir");
            promise.then(function (data) {
                expect(data).toBeDefined();
                expect($.isArray(data)).toBeTruthy();
                expect(data.length).toEqual(1);
                expect(data[0]).toEqual("epochs");
                done();
            },
            function (err) {
                expect(err.status).toEqual(200);
                done();
            });
        });

        it("should get 404 for an unknown dataset", function (done) {
            var promise = dataApi.getDataSetChildren("does/not/exist");
            promise.then(function (data) {
                expect(data).toBeUndefined();
                done();
            },
            function (err) {
                expect(err.status).toEqual(404);
                done();
            });
        });

        it("should get 404 for no dataset specified", function (done) {
            var promise = dataApi.getDataSetChildren("");
            promise.then(function (data) {
                expect(data).toBeUndefined();
                expect(true).toBeFalsy();
                done();
            },
            function (err) {
                expect(err.status).toEqual(404);
                done();
            });
        });
    });
});
