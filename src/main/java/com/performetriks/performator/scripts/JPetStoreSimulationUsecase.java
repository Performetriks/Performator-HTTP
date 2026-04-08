package com.performetriks.performator.scripts;

import com.performetriks.performator.base.PFRUsecase;
import com.performetriks.performator.data.PFRDataSource;
import com.performetriks.performator.data.PFRData;
import com.performetriks.performator.http.PFRHttp;
import com.performetriks.performator.http.PFRHttpResponse;
import com.xresch.xrutils.data.XRRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JPetStoreSimulationUsecase extends PFRUsecase {

    private static final Logger logger = LoggerFactory.getLogger(JPetStoreSimulationUsecase.class);

    // Static data source shared by all virtual users
    private static final PFRDataSource dataSource = PFRData.newSourceCSV(
            "com/performetriks/performator/scripts/data", 
            "jpetstore_users.csv", 
            ","
    ).build();

    private XRRecord userRecord;

    @Override
    public void initializeUser() {
        // Pull the next unique record for this thread
        userRecord = dataSource.next();
        if (userRecord == null) {
            logger.error("No more user records available in CSV!");
        }
    }

    @Override
    public void execute() throws Throwable {
        if (userRecord == null) return;

        // Ensure each user (thread) starts with a clean session
        // This prevents cookie leakage inherited from the parent thread
        com.performetriks.performator.http.PFRHttp.resetThreadState();
        com.performetriks.performator.http.PFRHttp.defaultTrustAllCertificates(true);

        String username = userRecord.get("username").getAsString();
        String password = userRecord.get("password").getAsString();

        // STEP 1: INITIAL PAGE HIT (Catalog)
        String targetIP = JPetStoreConfig.getNextTargetIP();
        String baseUrl = "https://" + targetIP + ":" + JPetStoreConfig.INGRESS_PORT;

        PFRHttp.create("Step1_HomePage", baseUrl + "/jpetstore/actions/Catalog.action")
                .header("Host", JPetStoreConfig.HOST_HEADER)
                .checkStatusEquals(200)
                .send();

        // STEP 2: NAVIGATION TO LOGIN FORM (To get Stripes tokens)
        PFRHttpResponse formResponse = PFRHttp
                .create("Step2_SignonForm", baseUrl + "/jpetstore/actions/Account.action?signonForm=")
                .header("Host", JPetStoreConfig.HOST_HEADER)
                .checkStatusEquals(200)
                .send();

        // STEP 3: CORRELATION
        String body = formResponse.getBody();
        String csrfToken = extractToken(body, "name=\"_csrf\" value=\"([^\"]+)\"");
        String sourcePage = extractToken(body, "name=\"_sourcePage\" value=\"([^\"]+)\"");
        String fp = extractToken(body, "name=\"__fp\" value=\"([^\"]+)\"");

        // STEP 4: LOG IN
        PFRHttpResponse loginResponse = PFRHttp
                .create("Step3_Login", baseUrl + "/jpetstore/actions/Account.action")
                .header("Host", "jpetstore.perfluencer.pl")
                .METHOD("POST")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .param("username", username)
                .param("password", password)
                .param("signon", "Login")
                .param("_csrf", csrfToken)
                .param("_sourcePage", sourcePage)
                .param("__fp", fp)
                .send();

        // Verify successful login transition
        if (!loginResponse.isSuccess()) {
            logger.warn("Login failed for user: " + username + 
                       " | Status: " + loginResponse.getStatus() + 
                       " | Error: " + loginResponse.errorMessage());
            return; // Skip subsequent steps if login failed
        }

        if (!loginResponse.getBody().contains("Sign Out")) {
            logger.warn("Login seemed successful (status 200) but 'Sign Out' was not found for user: " + username);
        }

        // STEP 4: ADD PET TO CART
        PFRHttp.create("Step3_AddToCart",
                baseUrl + "/jpetstore/actions/Cart.action?addItemToCart=&workingItemId=EST-1")
                .header("Host", "jpetstore.perfluencer.pl")
                .sendAsync()
                .join();
    }

    @Override
    public void terminate() {
        // Cleanup if needed
    }

    private String extractToken(String body, String regex) {
        if (body == null) return "";
        Matcher matcher = Pattern.compile(regex).matcher(body);
        return matcher.find() ? matcher.group(1) : "";
    }
}
