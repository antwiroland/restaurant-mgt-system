package com.restaurantmanager.core.dining;

import com.restaurantmanager.core.dining.DiningDtos.NextCourseRequest;
import com.restaurantmanager.core.dining.DiningDtos.NextCourseResponse;
import com.restaurantmanager.core.dining.DiningDtos.PublicDiningView;
import com.restaurantmanager.core.dining.DiningDtos.PublicSubmitRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Course dining from the table QR page. No sign-in: the table's secret QR token is the key, as for the other guest
 * table endpoints. The table, branch and meal always come from the token, never from the request, and a guest can
 * only order and ask for the next course: releasing a course to the kitchen is for staff.
 */
@RestController
@RequestMapping("/dining/public/tables/{tableToken}")
public class DiningPublicController {
    private final DiningService diningService;

    public DiningPublicController(DiningService diningService) {
        this.diningService = diningService;
    }

    /** Courses offered, this party's courses and their status, and whether "next course" can be asked for. */
    @GetMapping
    public PublicDiningView view(@PathVariable String tableToken) {
        return diningService.publicView(tableToken);
    }

    @PostMapping("/items")
    public PublicDiningView order(@PathVariable String tableToken, @Valid @RequestBody PublicSubmitRequest request) {
        return diningService.submitFromTable(tableToken, request);
    }

    @PostMapping("/request-next-course")
    public NextCourseResponse requestNextCourse(@PathVariable String tableToken,
                                                @Valid @RequestBody(required = false) NextCourseRequest request) {
        return diningService.requestNextCourse(tableToken, request == null ? null : request.sessionRef());
    }
}
