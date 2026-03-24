package com.restaurantmanager.core.phase8.loyalty;

import com.restaurantmanager.core.common.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

public class LoyaltyService {
    private static final BigDecimal POINT_VALUE = new BigDecimal("0.01");
    private final int pointsPerCedi;

    public LoyaltyService(int pointsPerCedi) {
        this.pointsPerCedi = pointsPerCedi;
    }

    public int accrue(LoyaltyAccount account, BigDecimal paymentAmount, String orderId) {
        int awarded = paymentAmount.multiply(BigDecimal.valueOf(pointsPerCedi))
                .setScale(0, RoundingMode.DOWN)
                .intValue();
        account.setPoints(account.getPoints() + awarded);
        account.addTransaction(new LoyaltyTransaction(awarded, LoyaltyTransactionType.EARN, orderId, Instant.now()));
        return awarded;
    }

    public RedeemResult redeem(LoyaltyAccount account, int pointsToRedeem, BigDecimal orderTotal, String orderId) {
        if (pointsToRedeem > account.getPoints()) {
            throw new ApiException(400, "Insufficient loyalty points");
        }
        BigDecimal maxDiscount = POINT_VALUE.multiply(BigDecimal.valueOf(pointsToRedeem));
        BigDecimal appliedDiscount = maxDiscount.min(orderTotal);
        int appliedPoints = appliedDiscount.divide(POINT_VALUE, RoundingMode.DOWN).intValue();

        account.setPoints(account.getPoints() - appliedPoints);
        account.addTransaction(new LoyaltyTransaction(appliedPoints, LoyaltyTransactionType.REDEEM, orderId, Instant.now()));

        return new RedeemResult(orderTotal.subtract(appliedDiscount), account.getPoints(), appliedPoints);
    }

    public List<LoyaltyTransaction> history(LoyaltyAccount account) {
        return account.getTransactions();
    }

    public int currentBalance(LoyaltyAccount account) {
        return account.getPoints();
    }

    public record RedeemResult(BigDecimal newTotal, int remainingPoints, int redeemedPoints) {
    }
}
