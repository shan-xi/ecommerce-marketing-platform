package com.example.marketingplatform.identity.domain;

import java.util.UUID;

public record UserPrincipal(UUID userId, String role) {
}
