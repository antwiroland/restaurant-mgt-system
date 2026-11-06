package com.restaurantmanager.core.loyalty;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "loyalty")
public class LoyaltyProps {
    /** Automatically award points when a customer's order is fully paid. */
    private boolean autoAccrue = true;
    /** Points earned per 1 GHS paid. */
    private int pointsPerCedi = 1;
    /** Monetary value of one point in GHS. */
    private BigDecimal pointValue = new BigDecimal("0.01");
    /** Maximum share of an order (after other discounts) that may be paid with points, 0-100. */
    private int maxRedeemPercent = 50;

    public boolean isAutoAccrue() { return autoAccrue; }
    public void setAutoAccrue(boolean autoAccrue) { this.autoAccrue = autoAccrue; }
    public int getPointsPerCedi() { return pointsPerCedi; }
    public void setPointsPerCedi(int pointsPerCedi) { this.pointsPerCedi = pointsPerCedi; }
    public BigDecimal getPointValue() { return pointValue; }
    public void setPointValue(BigDecimal pointValue) { this.pointValue = pointValue; }
    public int getMaxRedeemPercent() { return maxRedeemPercent; }
    public void setMaxRedeemPercent(int maxRedeemPercent) { this.maxRedeemPercent = maxRedeemPercent; }
}
