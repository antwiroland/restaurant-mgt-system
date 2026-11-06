package com.restaurantmanager.core.loyalty;

import com.restaurantmanager.core.common.ApiException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@EnableConfigurationProperties(LoyaltyProps.class)
public class LoyaltyAccountService {
    private final LoyaltyBalanceRepository loyaltyBalanceRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final LoyaltyProps props;
    private final LoyaltyService loyaltyService;

    public LoyaltyAccountService(LoyaltyBalanceRepository loyaltyBalanceRepository,
                                 LoyaltyTransactionRepository loyaltyTransactionRepository,
                                 LoyaltyProps props) {
        this.loyaltyBalanceRepository = loyaltyBalanceRepository;
        this.loyaltyTransactionRepository = loyaltyTransactionRepository;
        this.props = props;
        this.loyaltyService = new LoyaltyService(props.getPointsPerCedi(), props.getPointValue());
    }

    public LoyaltyProps props() {
        return props;
    }

    @Transactional
    public int accrue(UUID customerId, BigDecimal paymentAmount, String orderId) {
        LoyaltyBalanceEntity balance = lockOrCreateBalance(customerId);
        LoyaltyAccount account = toAccount(balance);
        int awarded = loyaltyService.accrue(account, paymentAmount, orderId);
        balance.setPoints(account.getPoints());
        loyaltyBalanceRepository.save(balance);
        saveTransaction(account.getTransactions().get(0), customerId);
        return awarded;
    }

    @Transactional
    public LoyaltyService.RedeemResult redeem(UUID customerId, int pointsToRedeem, BigDecimal orderTotal, String orderId) {
        LoyaltyBalanceEntity balance = lockOrCreateBalance(customerId);
        LoyaltyAccount account = toAccount(balance);
        LoyaltyService.RedeemResult result = loyaltyService.redeem(account, pointsToRedeem, orderTotal, orderId);
        balance.setPoints(account.getPoints());
        loyaltyBalanceRepository.save(balance);
        saveTransaction(account.getTransactions().get(0), customerId);
        return result;
    }

    /**
     * Redeems points against an order. The discount is capped at {@code loyalty.max-redeem-percent} of {@code payableAmount}.
     */
    @Transactional
    public OrderRedemption redeemForOrder(UUID customerId, int pointsRequested, BigDecimal payableAmount, String orderId) {
        if (pointsRequested <= 0 || payableAmount.signum() <= 0) {
            return OrderRedemption.none();
        }
        BigDecimal cap = payableAmount.multiply(BigDecimal.valueOf(props.getMaxRedeemPercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        if (cap.signum() <= 0) {
            return OrderRedemption.none();
        }
        LoyaltyService.RedeemResult result = redeem(customerId, pointsRequested, cap, orderId);
        BigDecimal discount = cap.subtract(result.newTotal()).setScale(2, RoundingMode.HALF_UP);
        return new OrderRedemption(result.redeemedPoints(), discount);
    }

    /** Returns points redeemed on an order that was cancelled or voided before payment. */
    @Transactional
    public void restoreRedeemed(UUID customerId, int points, String orderId) {
        if (points > 0) {
            adjust(customerId, points, orderId);
        }
    }

    /** Awards points for a fully paid order. */
    @Transactional
    public int accrueForOrder(UUID customerId, BigDecimal paidAmount, String orderId) {
        if (!props.isAutoAccrue() || paidAmount == null || paidAmount.signum() <= 0) {
            return 0;
        }
        return accrue(customerId, paidAmount, orderId);
    }

    /** Claws back previously awarded points (e.g. after a refund); never takes the balance below zero. */
    @Transactional
    public int reverseAccrual(UUID customerId, int points, String orderId) {
        if (points <= 0) {
            return 0;
        }
        LoyaltyBalanceEntity balance = lockOrCreateBalance(customerId);
        int deducted = Math.min(points, balance.getPoints());
        if (deducted > 0) {
            adjust(customerId, -deducted, orderId);
        }
        return deducted;
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

    private void adjust(UUID customerId, int delta, String orderId) {
        LoyaltyBalanceEntity balance = lockOrCreateBalance(customerId);
        int next = balance.getPoints() + delta;
        if (next < 0) {
            throw new ApiException(400, "Insufficient loyalty points");
        }
        balance.setPoints(next);
        loyaltyBalanceRepository.save(balance);
        saveTransaction(new LoyaltyTransaction(delta, LoyaltyTransactionType.ADJUST, orderId, Instant.now()), customerId);
    }

    private LoyaltyBalanceEntity lockOrCreateBalance(UUID customerId) {
        return loyaltyBalanceRepository.lockByCustomerId(customerId)
                .orElseGet(() -> {
                    LoyaltyBalanceEntity e = new LoyaltyBalanceEntity();
                    e.setCustomerId(customerId);
                    e.setPoints(0);
                    return loyaltyBalanceRepository.saveAndFlush(e);
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

    public record OrderRedemption(int pointsRedeemed, BigDecimal discount) {
        public static OrderRedemption none() {
            return new OrderRedemption(0, BigDecimal.ZERO);
        }
    }
}
