package com.restaurantmanager.core.hr.recruitment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Public, read-only job listings for the website's careers page (no sign-in). */
@RestController
@RequestMapping("/careers")
public class PublicCareersController {
    private final PublicCareersService careers;

    public PublicCareersController(PublicCareersService careers) {
        this.careers = careers;
    }

    @GetMapping
    public List<PublicCareersService.PublicVacancy> list() {
        return careers.list();
    }

    @GetMapping("/{id}")
    public PublicCareersService.PublicVacancy get(@PathVariable UUID id) {
        return careers.get(id);
    }
}
