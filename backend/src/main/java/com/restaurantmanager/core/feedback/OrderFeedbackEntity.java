package com.restaurantmanager.core.feedback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_feedback")
public class OrderFeedbackEntity {
    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "customer_user_id")
    private UUID customerUserId;

    @Column(name = "guest_name", length = 120)
    private String guestName;

    @Column(nullable = false)
    private int rating;

    @Column(name = "food_rating")
    private Integer foodRating;

    @Column(name = "service_rating")
    private Integer serviceRating;

    @Column(name = "delivery_rating")
    private Integer deliveryRating;

    @Column(length = 2000)
    private String comment;

    @Column(length = 2000)
    private String response;

    @Column(name = "responded_by")
    private UUID respondedBy;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(UUID customerUserId) { this.customerUserId = customerUserId; }
    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public Integer getFoodRating() { return foodRating; }
    public void setFoodRating(Integer foodRating) { this.foodRating = foodRating; }
    public Integer getServiceRating() { return serviceRating; }
    public void setServiceRating(Integer serviceRating) { this.serviceRating = serviceRating; }
    public Integer getDeliveryRating() { return deliveryRating; }
    public void setDeliveryRating(Integer deliveryRating) { this.deliveryRating = deliveryRating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public UUID getRespondedBy() { return respondedBy; }
    public void setRespondedBy(UUID respondedBy) { this.respondedBy = respondedBy; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
    public Instant getCreatedAt() { return createdAt; }

    /** The order's branch, so each branch sees and answers its own feedback. */
    @Column(name = "branch_id")
    private UUID branchId;

    /** The rider who delivered the order (delivery rating). */
    @Column(name = "rider_user_id")
    private UUID riderUserId;

    /** The staff member who took the order (service rating); null for orders guests placed themselves. */
    @Column(name = "staff_user_id")
    private UUID staffUserId;

    /** A guest (no account) may leave a phone number to receive the manager's reply. */
    @Column(name = "guest_phone", length = 20)
    private String guestPhone;

    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public UUID getRiderUserId() { return riderUserId; }
    public void setRiderUserId(UUID riderUserId) { this.riderUserId = riderUserId; }
    public UUID getStaffUserId() { return staffUserId; }
    public void setStaffUserId(UUID staffUserId) { this.staffUserId = staffUserId; }
    public String getGuestPhone() { return guestPhone; }
    public void setGuestPhone(String guestPhone) { this.guestPhone = guestPhone; }
}
