package com.performetriks.performator.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class PFRHttpIntegrationTest {

    private com.github.tomakehurst.wiremock.WireMockServer wireMockServer;
    private String selfSignedUrl;

    @BeforeEach
    void setUp() throws Exception {
        resetStatics();
        PFRHttp.defaultTrustAllCertificates(false);
        PFRHttp.defaultUseVirtualThreads(false);
        
        com.github.tomakehurst.wiremock.core.WireMockConfiguration config = com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig()
            .dynamicHttpsPort();
        wireMockServer = new com.github.tomakehurst.wiremock.WireMockServer(config);
        wireMockServer.start();
        
        selfSignedUrl = "https://localhost:" + wireMockServer.httpsPort() + "/test";
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/test")
            .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(200).withBody("JPetStore Mock")));
    }

    @AfterEach
    void tearDown() throws Exception {
        if(wireMockServer != null) {
            wireMockServer.stop();
        }
        PFRHttp.defaultTrustAllCertificates(false);
        PFRHttp.defaultUseVirtualThreads(false);
        resetStatics();
    }

    private void resetStatics() throws Exception {
        java.lang.reflect.Field cmField = PFRHttp.class.getDeclaredField("connectionManager");
        cmField.setAccessible(true);
        cmField.set(null, null);

        java.lang.reflect.Field hcField = PFRHttpRequestBuilder.class.getDeclaredField("httpClientSingle");
        hcField.setAccessible(true);
        hcField.set(null, null);

        // Reset ThreadLocals
        PFRHttp.cookieStore.remove();
        PFRHttp.httpContextStore.remove();
    }

    @Test
    void testSelfSignedCertFailsByDefault() {
        PFRHttp.defaultTrustAllCertificates(false);
        
        PFRHttpResponse response = PFRHttp.create(selfSignedUrl).send();
        
        assertTrue(response.hasError, "Request to self-signed cert should have errors when trustAllCertificates is false");
    }

    @Test
    void testSelfSignedCertAcceptsWhenConfigured() {
        PFRHttp.defaultTrustAllCertificates(true);
        PFRHttp.defaultUseVirtualThreads(false);
        
        PFRHttpResponse response = PFRHttp.create(selfSignedUrl).send();
        
        assertFalse(response.hasError, "Request to self-signed cert should NOT have errors when trustAllCertificates is true");
        assertTrue(response.getBody().contains("JPetStore Mock"), "Response should load the JPetStore body content");
    }

    @Test
    void testAsyncCallToSelfSignedTarget() throws ExecutionException, InterruptedException {
        PFRHttp.defaultTrustAllCertificates(true);
        PFRHttp.defaultUseVirtualThreads(true); // Using Virtual threads for the async execution
        
        PFRHttpResponse response = PFRHttp.create(selfSignedUrl).sendAsync().get();
        
        assertFalse(response.hasError, "Async request to self-signed cert should succeed");
        assertTrue(response.getBody().contains("JPetStore Mock"), "Async response should load the JPetStore body content");
    }
}
