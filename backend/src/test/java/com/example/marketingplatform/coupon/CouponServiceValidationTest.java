package com.example.marketingplatform.coupon;

import com.example.marketingplatform.coupon.application.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CouponServiceValidationTest {
    private NamedParameterJdbcTemplate jdbc;
    private TransactionTemplate tx;
    private CouponService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        tx = mock(TransactionTemplate.class);
        service = new CouponService(jdbc, tx, Clock.systemUTC(), JsonMapper.builder().build());
    }

    @Test
    void rejectsPercentageAboveOneHundred() {
        var input = input("PERCENTAGE", "100.01", 1, "2026-12-01T00:00:00Z", "2026-12-02T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> service.create(UUID.randomUUID(), input));
        verifyNoInteractions(jdbc, tx);
    }

    @Test
    void rejectsNullDiscountTypeAsValidationError() {
        var input = new CouponService.CouponWrite("Coupon", null, null, BigDecimal.TEN,
                null, 1, "2026-12-01T00:00:00Z", "2026-12-02T00:00:00Z");
        var exception = assertThrows(IllegalArgumentException.class,
                () -> service.create(UUID.randomUUID(), input));
        org.junit.jupiter.api.Assertions.assertEquals(
                "discountType must be FIXED_AMOUNT or PERCENTAGE.", exception.getMessage());
        verifyNoInteractions(jdbc, tx);
    }

    @Test
    void rejectsNonPositiveDiscountValue() {
        var input = input("FIXED_AMOUNT", "0", 1, "2026-12-01T00:00:00Z", "2026-12-02T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> service.create(UUID.randomUUID(), input));
        verifyNoInteractions(jdbc, tx);
    }

    @Test
    void rejectsZeroQuantityAndNegativeMinimumOrderAmount() {
        var zeroQuantity = input("FIXED_AMOUNT", "10", 0, "2026-12-01T00:00:00Z", "2026-12-02T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> service.create(UUID.randomUUID(), zeroQuantity));
        var negativeMinimum = new CouponService.CouponWrite("Coupon", null, "FIXED_AMOUNT", BigDecimal.TEN,
                new BigDecimal("-1"), 1, "2026-12-01T00:00:00Z", "2026-12-02T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> service.create(UUID.randomUUID(), negativeMinimum));
        verifyNoInteractions(jdbc, tx);
    }

    @Test
    void rejectsInvalidValidityWindowAndMissingIdempotencyKey() {
        var invalidWindow = input("FIXED_AMOUNT", "10", 1, "2026-12-02T00:00:00Z", "2026-12-02T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> service.create(UUID.randomUUID(), invalidWindow));
        assertThrows(IllegalArgumentException.class, () -> service.claim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null));
        verifyNoInteractions(jdbc, tx);
    }

    private CouponService.CouponWrite input(String type, String value, int quantity, String start, String end) {
        return new CouponService.CouponWrite("Coupon", null, type, new BigDecimal(value), null, quantity, start, end);
    }
}
