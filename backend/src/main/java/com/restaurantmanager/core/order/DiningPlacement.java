package com.restaurantmanager.core.order;

import java.util.UUID;

/**
 * Where a course-dining submission belongs: its meal, its course (null: straight to the kitchen), the station that
 * prepares it, and whether it is held back from the kitchen until the course is released.
 *
 * @param releasedBy for dishes added to a course that was already released: who released that course
 */
public record DiningPlacement(UUID diningOrderId, UUID diningOrderCourseId, String kitchenStation, boolean held,
                              UUID releasedBy) {
}
