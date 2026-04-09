package com.performetriks.performator.scripts;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared configuration and load-balancing logic for JPetStore benchmarks.
 */
public class JPetStoreConfig {

    // TARGET_IPS restricted to verified healthy nodes for 3,000 TPS stability
    private static final String[] TARGET_IPS = {"192.168.1.126", "192.168.1.130"};
    private static final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Returns the next target IP in a round-robin rotation.
     */
    public static String getNextTargetIP() {
        return TARGET_IPS[counter.getAndIncrement() % TARGET_IPS.length];
    }

    /**
     * Common Host header for Ingress compatibility.
     */
    public static final String HOST_HEADER = "jpetstore.perfluencer.pl";
    
    /**
     * The NodePort configured for the Ingress controller.
     */
    public static final int INGRESS_PORT = 443;
}
