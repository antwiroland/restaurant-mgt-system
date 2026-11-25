package com.restaurantmanager.core.dining;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DiningCourseRepository extends JpaRepository<DiningCourseEntity, UUID> {

    /** Courses offered at a branch: its own and the restaurant-wide ones (no branch). Null branch: restaurant-wide only. */
    @Query("""
            select c from DiningCourseEntity c
            where (c.branchId is null or c.branchId = :branchId)
            order by c.displayOrder asc, c.name asc
            """)
    List<DiningCourseEntity> findForBranch(@Param("branchId") UUID branchId);

    @Query("select c from DiningCourseEntity c order by c.displayOrder asc, c.name asc")
    List<DiningCourseEntity> findAllOrdered();
}
