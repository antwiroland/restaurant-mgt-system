package com.restaurantmanager.core.loyalty;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class LoyaltyAccountService {
    private final LoyaltyBalanceRepository loyaltyBalanceRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final LoyaltyService loyaltyService = new LoyaltyService(1);

    public LoyaltyAccountService(LoyaltyBalanceRepository loyaltyBalanceRepository,
                                 LoyaltyTransactionRepository loyaltyTransactionRepository) {
        this.loyaltyBalanceRepository = loyaltyBalanceRepository;
        this.loyaltyTransactionRepository = loyaltyTransactionRepository;
    }

    @Transactional
    public int accrue(UUID customerId, BigDecimal paymentAmount, String orderId) {
        LoyaltyBalanceEntity balance = findOrCreateBalance(customerId);
        LoyaltyAccount account = toAccount(balance);
        int awarded = loyaltyService.accrue(account, paymentAmount, orderId);
        balance.setPoints(account.getPoints());
        loyaltyBalanceRepository.save(balance);
        saveTransaction(account.getTransactions().get(0), customerId);
        return awarded;
    }

    @Transactional
    public LoyaltyService.RedeemResult redeem(UUID customerId, int pointsToRedeem, BigDecimal orderTotal, String orderId) {
        LoyaltyBalanceEntity balance = findOrCreateBalance(customerId);
        LoyaltyAccount account = toAccount(balance);
        LoyaltyService.RedeemResult result = loyaltyService.redeem(account, pointsToRedeem, orderTotal, orderId);
        balance.setPoints(account.getPoints());
        loyaltyBalanceRepository.save(balance);
        saveTransaction(account.getTransactions().get(0), customerId);
        return result;
    }

    @Transactional(readOnly = true)
    public int balance(UUID customerId) {
        return loyaltyBalanceRepository.findByCustomerId(customerId)
                .map(LoyaltyBalanceEntity::getPoints)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public List<LoyaltyTransaction> history(UUID customerId) {
        return loyaltyTransactionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(e -> new LoyaltyTransaction(e.getPoints(), e.getType(), e.getOrderId(), e.getCreatedAt()))
                .toList();
    }

    private LoyaltyBalanceEntity findOrCreateBalance(UUID customerId) {
        return loyaltyBalanceRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    LoyaltyBalanceEntity e = new LoyaltyBalanceEntity();
                    e.setCustomerId(customerId);
                    e.setPoints(0);
                    return e;
                });
    }

    private LoyaltyAccount toAccount(LoyaltyBalanceEntity balance) {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setPoints(balance.getPoints());
        return account;
    }

    private void saveTransaction(LoyaltyTransaction tx, UUID customerId) {
        LoyaltyTransactionEntity entity = new LoyaltyTransactionEntity();
        entity.setCustomerId(customerId);
        entity.setPoints(tx.points());
        entity.setType(tx.type());
        entity.setOrderId(tx.orderId());
        entity.setCreatedAt(tx.createdAt());
        loyaltyTransactionRepository.save(entity);
    }
}
