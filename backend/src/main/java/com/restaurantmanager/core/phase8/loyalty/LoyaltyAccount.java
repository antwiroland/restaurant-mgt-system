package com.restaurantmanager.core.phase8.loyalty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LoyaltyAccount {
    private int points;
    private final List<LoyaltyTransaction> transactions = new ArrayList<>();

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public void addTransaction(LoyaltyTransaction transaction) {
        transactions.add(transaction);
    }

    public List<LoyaltyTransaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }
}
