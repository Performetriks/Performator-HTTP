package com.performetriks.performator.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectionPoolConfigTest {

    @BeforeEach
    void setUp() {
        // Reset defaults before each test
        PFRHttp.defaultMaxTotalConnections(1000);
        PFRHttp.defaultMaxPerRouteConnections(200);
    }

    @AfterEach
    void tearDown() {
        // Reset defaults after each test
        PFRHttp.defaultMaxTotalConnections(1000);
        PFRHttp.defaultMaxPerRouteConnections(200);
    }

    @Test
    void testDefaultValues() {
        assertEquals(1000, PFRHttp.defaultMaxTotalConnections(), "Default max total connections should be 1000");
        assertEquals(200, PFRHttp.defaultMaxPerRouteConnections(), "Default max per route connections should be 200");
    }

    @Test
    void testCustomValues() {
        PFRHttp.defaultMaxTotalConnections(5000);
        PFRHttp.defaultMaxPerRouteConnections(1000);

        assertEquals(5000, PFRHttp.defaultMaxTotalConnections(), "Custom max total connections should be 5000");
        assertEquals(1000, PFRHttp.defaultMaxPerRouteConnections(), "Custom max per route connections should be 1000");
    }

    @Test
    void testConnectionManagerIsConfigured() {
        PFRHttp.defaultMaxTotalConnections(2500);
        PFRHttp.defaultMaxPerRouteConnections(500);

        var manager = PFRHttp.getConnectionManager();
        
        // Polling the configured properties
        assertEquals(2500, manager.getMaxTotal());
        assertEquals(500, manager.getDefaultMaxPerRoute());
    }
}
