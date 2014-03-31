define(["jquery","sg/data"], function($, DataApi) {
    'use strict';

    describe("DataApi Test Suite", function () {
        it("should get columns", function (done) {
            var promise = DataApi.getDataSetColumns("src/test/resources/epochs.csv");
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
            var promise = DataApi.getDataSetColumns("does/not/exist");
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
            var promise = DataApi.getDataSetColumns("");
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
