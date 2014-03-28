describe("DataApi Test Suite", function() {
    beforeEach(function() {
        console.log("in beforeEach");
    });
    
    afterEach(function() {
        console.log("in afterEach");
    });

    it("should get columns", function(done) {
        var jqXHR = Sparkle.Data.getDataSetColumns("src/test/resources/epochs.csv");
        jqXHR.done( function(data, textStatus) {
            expect(data).toBeDefined();
            expect($.isArray(data)).toBeTruthy();
            expect(data.length).toEqual(3);
            expect(data[0]).toEqual("count");
            expect(data[1]).toEqual("p90");
            expect(data[2]).toEqual("p99");
            done();
        });
        jqXHR.fail( function(jqXHR, textStatus, errorThrown) {
            expect(textStatus).toEqual("success");
            done();
        });
    });

    it("should get 404 for an unknown dataset", function(done) {
        var jqXHR = Sparkle.Data.getDataSetColumns("does/not/exist");
        jqXHR.done( function(data, textStatus) {
            expect(textStatus).toEqual("error");
            done();
        });
        jqXHR.fail( function(jqXHR, textStatus, errorThrown) {
            expect(textStatus).toEqual("error");
            expect(errorThrown).toEqual("Not Found");
            expect(jqXHR.status).toEqual(404);
            done();
        });
    });

    it("should get 404 for no dataset specified", function(done) {
        var jqXHR = Sparkle.Data.getDataSetColumns("");
        jqXHR.done( function(data, textStatus) {
            expect(true).toBeFalsy();
            done();
        });
        jqXHR.fail( function(jqXHR, textStatus, errorThrown) {
            expect(textStatus).toEqual("error");
            expect(errorThrown).toEqual("Not Found");
            expect(jqXHR.status).toEqual(404);
            done();
        });
    });
});
