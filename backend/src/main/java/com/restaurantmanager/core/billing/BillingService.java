package com.restaurantmanager.core.billing;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes service charge and taxes for orders and manages the billing configuration.
 * Without a billing_settings row (e.g. a fresh database without migrations) no service charge or tax applies.
 */
@Service
public class BillingService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BillingSettingsRepository settingsRepository;
    private final TaxRateRepository taxRateRepository;
    private final OrderTaxLineRepository taxLineRepository;

    public BillingService(BillingSettingsRepository settingsRepository,
                          TaxRateRepository taxRateRepository,
                          OrderTaxLineRepository taxLineRepository) {
        this.settingsRepository = settingsRepository;
        this.taxRateRepository = taxRateRepository;
        this.taxLineRepository = taxLineRepository;
    }

    // ------------------------------------------------------------ pricing

    /** Computes the components that depend on billing rules (no persistence). */
    @Transactional(readOnly = true)
    public Breakdown preview(OrderType type, BigDecimal discountedSubtotal, BigDecimal deliveryFee) {
        BillingSettingsEntity settings = settings();
        BigDecimal serviceCharge = BigDecimal.ZERO;
        boolean serviceApplies = settings.getServiceChargePercent().signum() > 0
                && (!settings.isServiceChargeDineInOnly() || type == OrderType.DINE_IN);
        if (serviceApplies) {
            serviceCharge = discountedSubtotal.multiply(settings.getServiceChargePercent())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }
        BigDecimal taxable = discountedSubtotal.add(serviceCharge)
                .add(settings.isTaxDeliveryFee() ? deliveryFee : BigDecimal.ZERO);
        List<TaxCalculator.Rate> rates = taxRateRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(r -> new TaxCalculator.Rate(r.getCode(), r.getName(), r.getRate(), r.isCompound()))
                .toList();
        TaxCalculator.Result tax = TaxCalculator.calculate(taxable, rates, settings.isPricesIncludeTax());
        return new Breakdown(serviceCharge.setScale(2, RoundingMode.HALF_UP), tax.taxTotal(), settings.isPricesIncludeTax(),
                tax.lines().stream().map(l -> new TaxLine(l.code(), l.name(), l.percent(), l.amount())).toList());
    }

    /** Recomputes service charge, taxes and total for a persisted order and stores its tax lines. */
    @Transactional
    public void apply(OrderEntity order) {
        order.recalculateTotal(); // ensures discounted subtotal is consistent before computing charges
        Breakdown breakdown = preview(order.getType(), order.discountedSubtotal(), order.getDeliveryFee());
        order.setServiceCharge(breakdown.serviceCharge());
        order.setTaxTotal(breakdown.taxTotal());
        order.setTaxInclusive(breakdown.taxInclusive());
        order.recalculateTotal();
        if (order.getId() != null) {
            taxLineRepository.deleteByOrderId(order.getId());
            List<OrderTaxLineEntity> rows = new ArrayList<>();
            int position = 0;
            for (TaxLine line : breakdown.taxLines()) {
                OrderTaxLineEntity row = new OrderTaxLineEntity();
                row.setOrderId(order.getId());
                row.setCode(line.code());
                row.setName(line.name());
                row.setRate(line.rate());
                row.setAmount(line.amount());
                row.setDisplayOrder(position++);
                rows.add(row);
            }
            taxLineRepository.saveAll(rows);
        }
    }

    /** Sets a tip (absolute amount or percentage of the discounted subtotal) and recomputes the total. */
    @Transactional
    public void setTip(OrderEntity order, BigDecimal amount, BigDecimal percent) {
        if (!settings().isTipsEnabled()) {
            throw new ApiException(400, "Tips are disabled");
        }
        BigDecimal tip;
        if (percent != null) {
            if (percent.signum() < 0 || percent.compareTo(new BigDecimal("50")) > 0) {
                throw new ApiException(400, "Tip percentage must be between 0 and 50");
            }
            tip = order.discountedSubtotal().multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        } else {
            tip = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        }
        if (tip.signum() < 0) {
            throw new ApiException(400, "Tip cannot be negative");
        }
        order.setTipAmount(tip);
        order.recalculateTotal();
    }

    @Transactional(readOnly = true)
    public List<TaxLine> taxLines(UUID orderId) {
        return taxLineRepository.findByOrderIdOrderByDisplayOrderAsc(orderId).stream().map(TaxLine::from).toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<TaxLine>> taxLines(Collection<UUID> orderIds) {
        Map<UUID, List<TaxLine>> result = new HashMap<>();
        if (orderIds.isEmpty()) {
            return result;
        }
        taxLineRepository.findByOrderIdIn(orderIds).stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .forEach(row -> result.computeIfAbsent(row.getOrderId(), k -> new ArrayList<>()).add(TaxLine.from(row)));
        return result;
    }

    // ------------------------------------------------------------ configuration

    @Transactional(readOnly = true)
    public BillingSettingsEntity settings() {
        return settingsRepository.findById(BillingSettingsEntity.SINGLETON_ID).orElseGet(BillingSettingsEntity::new);
    }

    @Transactional(readOnly = true)
    public ConfigView config() {
        BillingSettingsEntity s = settings();
        return new ConfigView(s.getServiceChargePercent(), s.isServiceChargeDineInOnly(), s.isPricesIncludeTax(),
                s.isTaxDeliveryFee(), s.isTipsEnabled(),
                taxRateRepository.findAllByOrderByDisplayOrderAsc().stream().map(TaxRateView::from).toList());
    }

    @Transactional
    public ConfigView updateSettings(SettingsRequest request, UUID actorId) {
        if (request.serviceChargePercent().signum() < 0 || request.serviceChargePercent().compareTo(new BigDecimal("30")) > 0) {
            throw new ApiException(400, "Service charge must be between 0 and 30 percent");
        }
        BillingSettingsEntity s = settings();
        s.setServiceChargePercent(request.serviceChargePercent());
        s.setServiceChargeDineInOnly(request.serviceChargeDineInOnly());
        s.setPricesIncludeTax(request.pricesIncludeTax());
        s.setTaxDeliveryFee(request.taxDeliveryFee());
        s.setTipsEnabled(request.tipsEnabled());
        s.setUpdatedBy(actorId);
        settingsRepository.save(s);
        return config();
    }

    @Transactional
    public TaxRateView saveTax(UUID id, TaxRateRequest request) {
        TaxRateEntity tax = id == null ? new TaxRateEntity()
                : taxRateRepository.findById(id).orElseThrow(() -> new ApiException(404, "Tax rate not found"));
        String code = request.code().trim().toUpperCase();
        if ((id == null || !code.equalsIgnoreCase(tax.getCode())) && taxRateRepository.existsByCodeIgnoreCase(code)) {
            throw new ApiException(409, "Tax code already exists");
        }
        if (request.rate().signum() < 0 || request.rate().compareTo(HUNDRED) > 0) {
            throw new ApiException(400, "Rate must be between 0 and 100");
        }
        tax.setCode(code);
        tax.setName(request.name().trim());
        tax.setRate(request.rate());
        tax.setCompound(request.compound());
        tax.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        if (request.active() != null) {
            tax.setActive(request.active());
        }
        return TaxRateView.from(taxRateRepository.save(tax));
    }

    @Transactional
    public void deleteTax(UUID id) {
        taxRateRepository.delete(taxRateRepository.findById(id).orElseThrow(() -> new ApiException(404, "Tax rate not found")));
    }

    // ------------------------------------------------------------ views

    public record TaxLine(String code, String name, BigDecimal rate, BigDecimal amount) {
        static TaxLine from(OrderTaxLineEntity e) {
            return new TaxLine(e.getCode(), e.getName(), e.getRate(), e.getAmount());
        }
    }

    public record Breakdown(BigDecimal serviceCharge, BigDecimal taxTotal, boolean taxInclusive, List<TaxLine> taxLines) {}

    public record TaxRateView(UUID id, String code, String name, BigDecimal rate, boolean compound, int displayOrder, boolean active) {
        static TaxRateView from(TaxRateEntity e) {
            return new TaxRateView(e.getId(), e.getCode(), e.getName(), e.getRate(), e.isCompound(), e.getDisplayOrder(), e.isActive());
        }
    }

    public record ConfigView(BigDecimal serviceChargePercent, boolean serviceChargeDineInOnly, boolean pricesIncludeTax,
                             boolean taxDeliveryFee, boolean tipsEnabled, List<TaxRateView> taxes) {}

    public record SettingsRequest(@jakarta.validation.constraints.NotNull BigDecimal serviceChargePercent,
                                  boolean serviceChargeDineInOnly, boolean pricesIncludeTax,
                                  boolean taxDeliveryFee, boolean tipsEnabled) {}

    public record TaxRateRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 20) String code,
                                 @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 60) String name,
                                 @jakarta.validation.constraints.NotNull BigDecimal rate,
                                 boolean compound, Integer displayOrder, Boolean active) {}
}
