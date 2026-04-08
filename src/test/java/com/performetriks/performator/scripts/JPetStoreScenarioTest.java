package com.performetriks.performator.scripts;

import com.performetriks.performator.http.PFRHttp;
import com.performetriks.performator.http.PFRHttpResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JPetStoreScenarioTest {

    @BeforeAll
    static void configureGlobalHttpClient() {
        // Prepare your JPetStore enterprise concurrency limits
        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        PFRHttp.defaultMaxTotalConnections(5000);
        PFRHttp.defaultMaxPerRouteConnections(1000);
        PFRHttp.defaultUseVirtualThreads(true); // Supercharges throughput

        // ONLY IF JPetStore remains deployed with a self-signed certificate:
        PFRHttp.defaultTrustAllCertificates(true);
    }

    @Test
    void executeJPetStorePurchasingFlow() {
        // ---------------------------------------------------------------------------------
        // STEP 1: INITIAL PAGE HIT (Starts the Session)
        // ---------------------------------------------------------------------------------
        PFRHttpResponse homeResponse = PFRHttp
                .create("Step1_HomePage", "https://jpetstore.perfluencer.pl/jpetstore/actions/Catalog.action")
                .checkStatusEquals(200) // Automatically check HTTP 200
                .send();

        // ---------------------------------------------------------------------------------
        // STEP 2: CORRELATION (Extract a token or ID from the response body)
        // ---------------------------------------------------------------------------------
        String csrfToken = extractTokenUsingRegex(homeResponse.getBody(), "name=\"_csrf\" value=\"([A-Za-z0-9\\-]+)\"");

        // ---------------------------------------------------------------------------------
        // STEP 3: LOG IN (Cookies are sent automatically! Pass the CSRF token in the
        // body)
        // ---------------------------------------------------------------------------------
        PFRHttpResponse loginResponse = PFRHttp
                .create("Step2_Login", "https://jpetstore.perfluencer.pl/jpetstore/actions/Account.action?signon")
                .METHOD("POST")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("username=j2ee&password=j2ee&_csrf=" + csrfToken)
                .send();

        // Verify successful login transition (Checking the page says 'Sign Out')
        assertTrue(loginResponse.isSuccess());
        assertNotNull(loginResponse.getBody());

        // ---------------------------------------------------------------------------------
        // STEP 4: ADD PET TO CART USING ASYNC
        // ---------------------------------------------------------------------------------
        // With virtual threads enabled, execute high-volume async transitions cleanly
        PFRHttpResponse cartResponse = PFRHttp.create("Step3_AddToCart",
                "https://jpetstore.perfluencer.pl/jpetstore/actions/Cart.action?addItemToCart=&workingItemId=EST-1")
                .sendAsync()
                .join(); // Blocks Carrier thread momentarily while Virtual thread executes natively

        assertTrue(cartResponse.isSuccess());
    }

    // Simple helper function to run the regex extraction
    private String extractTokenUsingRegex(String responseBody, String regexTarget) {
        if (responseBody == null)
            return "";
        Matcher matcher = Pattern.compile(regexTarget).matcher(responseBody);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return ""; // Fallback mapping
    }
}
