package com.performetriks.performator.scripts;

import com.performetriks.performator.base.PFRUsecase;
import com.performetriks.performator.http.PFRHttp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulates a guest user browsing the JPetStore catalog without logging in.
 */
public class JPetStoreBrowsingUsecase extends PFRUsecase {

    private static final Logger logger = LoggerFactory.getLogger(JPetStoreBrowsingUsecase.class);

    @Override
    public void initializeUser() {
        // No specific data needed for guest browsing
    }

    @Override
    public void execute() throws Throwable {
        
        // Ensure clean session
        com.performetriks.performator.http.PFRHttp.resetThreadState();
        com.performetriks.performator.http.PFRHttp.defaultTrustAllCertificates(true);

        // STEP 1: Land on Home Page
        String targetIP = JPetStoreConfig.getNextTargetIP();
        String baseUrl = "https://" + targetIP + ":" + JPetStoreConfig.INGRESS_PORT;

        PFRHttp.create("Browse_Home", baseUrl + "/jpetstore/actions/Catalog.action")
                .header("Host", JPetStoreConfig.HOST_HEADER)
                .checkStatusEquals(200)
                .send();

        // STEP 2: View Category (FISH)
        PFRHttp.create("Browse_Category_Fish", 
                baseUrl + "/jpetstore/actions/Catalog.action?viewCategory=&categoryId=FISH")
                .header("Host", JPetStoreConfig.HOST_HEADER)
                .checkStatusEquals(200)
                .send();

        // STEP 3: View Product (Angelfish)
        PFRHttp.create("Browse_Product_Angelfish", 
                baseUrl + "/jpetstore/actions/Catalog.action?viewProduct=&productId=FI-SW-01")
                .header("Host", JPetStoreConfig.HOST_HEADER)
                .checkStatusEquals(200)
                .send();

        // STEP 4: View Item Details
        PFRHttp.create("Browse_Item_Details", 
                baseUrl + "/jpetstore/actions/Catalog.action?viewItem=&itemId=EST-1")
                .header("Host", JPetStoreConfig.HOST_HEADER)
                .checkStatusEquals(200)
                .send();
                
        // STEP 5: Add to Cart (as guest)
        PFRHttp.create("Browse_AddToCart",
                baseUrl + "/jpetstore/actions/Cart.action?addItemToCart=&workingItemId=EST-1")
                .header("Host", JPetStoreConfig.HOST_HEADER)
                .send();
    }

    @Override
    public void terminate() {
        // Cleanup if needed
    }
}
