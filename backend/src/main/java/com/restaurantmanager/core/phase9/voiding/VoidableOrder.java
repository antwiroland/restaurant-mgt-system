package com.restaurantmanager.core.phase9.voiding;

import java.util.UUID;

public class VoidableOrder {
    private final UUID id;
    private final OrderChannel channel;
    private VoidableOrderStatus status;
    private TableLinkStatus tableStatus;

    public VoidableOrder(UUID id, OrderChannel channel, VoidableOrderStatus status, TableLinkStatus tableStatus) {
        this.id = id;
        this.channel = channel;
        this.status = status;
        this.tableStatus = tableStatus;
    }

    public UUID getId() {
        return id;
    }

    public OrderChannel getChannel() {
        return channel;
    }

    public VoidableOrderStatus getStatus() {
        return status;
    }

    public void setStatus(VoidableOrderStatus status) {
        this.status = status;
    }

    public TableLinkStatus getTableStatus() {
        return tableStatus;
    }

    public void setTableStatus(TableLinkStatus tableStatus) {
        this.tableStatus = tableStatus;
    }
}
