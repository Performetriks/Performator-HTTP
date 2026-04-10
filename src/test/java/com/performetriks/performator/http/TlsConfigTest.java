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
        PFRHttp.defaultTrustAllCertificates(true);
    }

    @AfterEach
    void tearDown() {
        // Reset defaults after each test
        PFRHttp.defaultTrustAllCertificates(true);
    }

    @Test
    void testDefaultValueTrusted() {
        assertTrue(PFRHttp.defaultTrustAllCertificates(), "Default TLS setting should be TRUST ALL (true)");
    }

    @Test
    void testCustomValueStrict() {
        PFRHttp.defaultTrustAllCertificates(false);

        assertFalse(PFRHttp.defaultTrustAllCertificates(), "Custom TLS setting should allow disabling trustAll");
    }
}
