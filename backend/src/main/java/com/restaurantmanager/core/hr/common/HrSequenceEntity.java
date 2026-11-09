package com.restaurantmanager.core.hr.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "hr_sequences")
public class HrSequenceEntity {
    @Id
    @Column(length = 40)
    private String name;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    protected HrSequenceEntity() {
    }

    public HrSequenceEntity(String name, long nextValue) {
        this.name = name;
        this.nextValue = nextValue;
    }

    public String getName() {
        return name;
    }

    public long getNextValue() {
        return nextValue;
    }

    public void setNextValue(long nextValue) {
        this.nextValue = nextValue;
    }
}
