package com.performetriks.performator.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualThreadsConfigTest {

    @BeforeEach
    void setUp() {
        // Reset defaults before each test
        PFRHttp.defaultUseVirtualThreads(false);
    }

    @AfterEach
    void tearDown() {
        // Reset defaults after each test
        PFRHttp.defaultUseVirtualThreads(false);
    }

    @Test
    void testDefaultValueIsFalse() {
        assertFalse(PFRHttp.defaultUseVirtualThreads(), "Default virtual threads setting should be false");
    }

    @Test
    void testEnableVirtualThreads() {
        PFRHttp.defaultUseVirtualThreads(true);

        assertTrue(PFRHttp.defaultUseVirtualThreads(), "Virtual threads setting should reflect the globally configured state");
    }
}
