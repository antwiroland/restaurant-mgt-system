package com.restaurantmanager.core.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Single-row table (id = 1) holding restaurant-wide billing rules. */
@Entity
@Table(name = "billing_settings")
public class BillingSettingsEntity {
    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id = SINGLETON_ID;

    @Column(name = "service_charge_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal serviceChargePercent = BigDecimal.ZERO;

    @Column(name = "service_charge_dine_in_only", nullable = false)
    private boolean serviceChargeDineInOnly = true;

    @Column(name = "prices_include_tax", nullable = false)
    private boolean pricesIncludeTax;

    @Column(name = "tax_delivery_fee", nullable = false)
    private boolean taxDeliveryFee = true;

    @Column(name = "tips_enabled", nullable = false)
    private boolean tipsEnabled = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    @PreUpdate
    public void touch() {
        id = SINGLETON_ID;
        updatedAt = Instant.now();
    }

    public Integer getId() { return id; }
    public BigDecimal getServiceChargePercent() { return serviceChargePercent; }
    public void setServiceChargePercent(BigDecimal serviceChargePercent) { this.serviceChargePercent = serviceChargePercent; }
    public boolean isServiceChargeDineInOnly() { return serviceChargeDineInOnly; }
    public void setServiceChargeDineInOnly(boolean serviceChargeDineInOnly) { this.serviceChargeDineInOnly = serviceChargeDineInOnly; }
    public boolean isPricesIncludeTax() { return pricesIncludeTax; }
    public void setPricesIncludeTax(boolean pricesIncludeTax) { this.pricesIncludeTax = pricesIncludeTax; }
    public boolean isTaxDeliveryFee() { return taxDeliveryFee; }
    public void setTaxDeliveryFee(boolean taxDeliveryFee) { this.taxDeliveryFee = taxDeliveryFee; }
    public boolean isTipsEnabled() { return tipsEnabled; }
    public void setTipsEnabled(boolean tipsEnabled) { this.tipsEnabled = tipsEnabled; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}
