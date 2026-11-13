-- Modifier options chosen for an item in a group cart (size, spice, add-ons), carried into the placed order.
CREATE TABLE group_session_item_modifiers (
    group_session_item_id UUID NOT NULL REFERENCES group_session_items(id) ON DELETE CASCADE,
    modifier_option_id UUID NOT NULL REFERENCES menu_modifier_options(id) ON DELETE CASCADE,
    PRIMARY KEY (group_session_item_id, modifier_option_id)
);

-- Open sessions with no activity are cancelled by a scheduled job.
CREATE INDEX idx_group_order_sessions_status_updated ON group_order_sessions(status, updated_at);
