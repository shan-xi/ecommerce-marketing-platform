package com.example.marketingplatform.audience.application;

import java.util.List;
import java.util.UUID;

public record AudiencePreviewResult(long count, List<SampleMember> sample) {
    public record SampleMember(UUID memberId, String displayName) {
    }
}
