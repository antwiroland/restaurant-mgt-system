package com.restaurantmanager.core.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs work once the current transaction has committed (or straight away when there is none). Realtime events go
 * through this so a screen that refreshes on the event (KDS, POS, floor plan) reads the committed row, not the
 * state before it.
 */
public final class AfterCommit {
    private AfterCommit() {
    }

    public static void run(Runnable work) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // The data is already committed: a failure here (e.g. the message broker is down) must not turn
                    // the request into an error, or the guest or cashier retries and the order is placed twice.
                    try {
                        work.run();
                    } catch (RuntimeException ex) {
                        org.slf4j.LoggerFactory.getLogger(AfterCommit.class)
                                .warn("After-commit work failed (the transaction is committed): {}", ex.toString());
                    }
                }
            });
        } else {
            work.run();
        }
    }
}
