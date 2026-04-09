package com.performetriks.performator.scripts;

import com.performetriks.performator.base.PFRCoordinator;
import com.performetriks.performator.base.PFRTest;
import com.performetriks.performator.executors.PFRExecStandard;
import com.performetriks.performator.http.PFRHttp;
import com.xresch.hsr.base.HSRConfig;
import com.xresch.hsr.reporting.HSRReporterCSV;
import com.xresch.hsr.reporting.HSRReporterHTML;
import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;
import java.time.Duration;

public class JPetStoreSimulationRunner extends PFRTest {

    @Test
    void executeTest() throws Exception {
        
        // 1. Configure HSR
        HSRConfig.setLogLevelRoot(Level.INFO);
        HSRConfig.addProperty("Project", "JPetStore Performance");
        
        String resultsDir = "./target/hsr-results";
        HSRConfig.addReporter(new HSRReporterCSV(resultsDir + "/stats.csv", ","));
        HSRConfig.addReporter(new HSRReporterHTML(resultsDir + "/report"));
        HSRConfig.setInterval(5); 

        // 2. Configure PFRHttp
        // 1. Configure Global HTTP Settings for high throughput
        PFRHttp.defaultMaxTotalConnections(10000);
        PFRHttp.defaultMaxPerRouteConnections(5000);
        PFRHttp.defaultUseVirtualThreads(true);
        PFRHttp.defaultTrustAllCertificates(true);
        PFRHttp.defaultConnectTimeout(60000);
        PFRHttp.defaultSocketTimeout(60000);

        // 3. Configure PFRTest settings
        this.maxDuration(Duration.ofMinutes(5));
        this.gracefulStop(Duration.ofSeconds(60));

        // 4. Wrap the load pattern (90/10 split for 3000 total TPS)
        PFRExecStandard executorLogin = new PFRExecStandard(
                JPetStoreSimulationUsecase.class, 
                500,      // Users
                180000,   // Total executions per hour (~300 TPS)
                0,        // Offset start
                50        // Ramp up in chunks
        );

        PFRExecStandard executorBrowse = new PFRExecStandard(
                JPetStoreBrowsingUsecase.class, 
                4000,     // Users (Virtual Threads)
                1800000,  // Total executions per hour (~2500 TPS)
                0,        // Offset start
                400       // Ramp up in chunks
        );

        this.add(executorLogin);
        this.add(executorBrowse);

        // 5. Start through Coordinator
        System.out.println("Starting JPetStore Simulation via PFRCoordinator...");
        PFRCoordinator.startTest(this);
        System.out.println("Simulation complete. Results in: " + resultsDir);
    }
}
