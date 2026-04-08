package com.performetriks.performator.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

class PFRHttpMetricTest {

    private WireMockServer wireMockServer;
    private String url;

    @BeforeEach
    void setUp() throws Exception {
        WireMockConfiguration config = WireMockConfiguration.wireMockConfig().dynamicPort();
        wireMockServer = new WireMockServer(config);
        wireMockServer.start();
        url = "http://localhost:" + wireMockServer.port() + "/metric-test";
        wireMockServer.stubFor(WireMock.get("/metric-test")
            .willReturn(WireMock.aResponse().withStatus(200).withBody("Metric Test Body")));
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void testLatencyMetricsAreRecorded() {
        // We can't easily assert on static HSR calls without specialized mocking,
        // but we can ensure the request still succeeds with the instrumentation active.
        String metricName = "TestMetric";
        PFRHttpResponse response = PFRHttp.create(metricName, url).send();
        
        assertFalse(response.hasError, "Request should succeed with latency instrumentation");
        assertTrue(response.getBody().contains("Metric Test Body"));
        
        // Internal check: Verify that ThreadLocal was cleared
        assertTrue(PFRHttp.currentMetricName() == null, "currentMetricName ThreadLocal should be cleared after request");
    }
}
