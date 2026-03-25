package com.restaurantmanager.core.kds;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/kds")
public class KdsController {
    private final KdsService kdsService;

    public KdsController(KdsService kdsService) {
        this.kdsService = kdsService;
    }

    @GetMapping("/board")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public KdsBoardResponse board(@RequestParam(required = false) UUID branchId) {
        return kdsService.board(branchId);
    }
}
