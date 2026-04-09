package com.performetriks.performator.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TlsConfigTest {

    @BeforeEach
    void setUp() {
        // Reset defaults before each test
        PFRHttp.defaultTrustAllCertificates(false);
    }

    @AfterEach
    void tearDown() {
        // Reset defaults after each test
        PFRHttp.defaultTrustAllCertificates(false);
    }

    @Test
    void testDefaultValueSecure() {
        assertFalse(PFRHttp.defaultTrustAllCertificates(), "Default TLS setting should be SECURE (false for trustAll)");
    }

    @Test
    void testCustomValueInsecureForTesting() {
        PFRHttp.defaultTrustAllCertificates(true);

        assertTrue(PFRHttp.defaultTrustAllCertificates(), "Custom TLS setting should allow trustAll");
    }
}
