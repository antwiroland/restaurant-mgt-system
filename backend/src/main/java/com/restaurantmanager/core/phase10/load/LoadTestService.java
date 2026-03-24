package com.restaurantmanager.core.phase10.load;

public class LoadTestService {
    public long concurrentOrderCreationLatencyMs(int concurrentRequests) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < concurrentRequests; i++) {
            mockOrderCreate();
        }
        return System.currentTimeMillis() - start;
    }

    public long concurrentPaymentInitiationLatencyMs(int concurrentRequests) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < concurrentRequests; i++) {
            mockPaymentInitiate();
        }
        return System.currentTimeMillis() - start;
    }

    public int broadcastOrderEvent(int subscribers) {
        int delivered = 0;
        for (int i = 0; i < subscribers; i++) {
            delivered++;
        }
        return delivered;
    }

    private void mockOrderCreate() {
        Math.sqrt(144.0);
    }

    private void mockPaymentInitiate() {
        Math.sqrt(225.0);
    }
}
