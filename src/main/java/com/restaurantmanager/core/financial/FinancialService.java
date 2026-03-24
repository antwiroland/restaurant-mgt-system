package com.restaurantmanager.core.financial;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.OverrideActionType;
import com.restaurantmanager.core.common.TokenType;
import com.restaurantmanager.core.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FinancialService {
    private final JwtService jwtService;

    public FinancialService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public Map<String, Object> performAction(String overrideToken, OverrideActionType expectedAction) {
        Claims claims;
        try {
            claims = jwtService.parse(overrideToken);
        } catch (ExpiredJwtException ex) {
            throw new ApiException(401, "Override token expired");
        }

        if (jwtService.tokenType(claims) != TokenType.OVERRIDE) {
            throw new ApiException(401, "Invalid override token");
        }
        OverrideActionType actualAction = jwtService.actionType(claims);
        if (actualAction != expectedAction) {
            throw new ApiException(403, "Override token not valid for this action");
        }
        return Map.of("status", "OK", "actionType", expectedAction.name());
    }
}
