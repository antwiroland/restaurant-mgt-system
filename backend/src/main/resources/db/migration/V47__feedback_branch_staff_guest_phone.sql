-- Feedback per branch, attributed to the rider and the staff member who served the order, and a guest phone so a
-- guest (no account) can receive the manager's reply.
ALTER TABLE order_feedback ADD COLUMN branch_id UUID REFERENCES branches(id) ON DELETE SET NULL;
ALTER TABLE order_feedback ADD COLUMN rider_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE order_feedback ADD COLUMN staff_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE order_feedback ADD COLUMN guest_phone VARCHAR(20);

UPDATE order_feedback f
SET branch_id = o.branch_id,
    rider_user_id = o.rider_user_id,
    staff_user_id = o.created_by_user_id
FROM orders o
WHERE o.id = f.order_id;

CREATE INDEX idx_order_feedback_branch_created ON order_feedback(branch_id, created_at);
