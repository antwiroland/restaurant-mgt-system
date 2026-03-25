package com.restaurantmanager.core.payment;

import com.restaurantmanager.core.config.PaymentProps;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PendingPaymentReconciliationJob {
    private final PaymentService paymentService;
    private final PaymentProps paymentProps;

    public PendingPaymentReconciliationJob(PaymentService paymentService, PaymentProps paymentProps) {
        this.paymentService = paymentService;
        this.paymentProps = paymentProps;
    }

    @Scheduled(fixedDelayString = "${payments.reconciliation.fixed-delay-ms:60000}")
    public void run() {
        PaymentProps.Reconciliation cfg = paymentProps.getReconciliation();
        if (!cfg.isEnabled()) {
            return;
        }
        paymentService.reconcilePendingPayments(cfg.getPendingOlderThanMinutes(), cfg.getBatchSize());
    }
}
