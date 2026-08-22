package com.dms.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SqlValueSupportTest {
    @Test
    void toLong_handlesNumberStringAndBlank() {
        assertThat(SqlValueSupport.toLong(12L)).isEqualTo(12L);
        assertThat(SqlValueSupport.toLong(" 34 ")).isEqualTo(34L);
        assertThat(SqlValueSupport.toLong("")).isNull();
        assertThat(SqlValueSupport.toLong("abc")).isNull();
        assertThat(SqlValueSupport.toLong(null)).isNull();
    }

    @Test
    void toBdZero_keepsDecimalAndFallsBackToZero() {
        assertThat(SqlValueSupport.toBdZero(new BigDecimal("12.30"))).isEqualByComparingTo("12.30");
        assertThat(SqlValueSupport.toBdZero(45.6)).isEqualByComparingTo("45.6");
        assertThat(SqlValueSupport.toBdZero(" 7.8 ")).isEqualByComparingTo("7.8");
        assertThat(SqlValueSupport.toBdZero("bad")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(SqlValueSupport.toBdZero(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void strOr_trimsAndUsesDefaultForBlank() {
        assertThat(SqlValueSupport.strOr(" x ", "d")).isEqualTo("x");
        assertThat(SqlValueSupport.strOr("  ", "d")).isEqualTo("d");
        assertThat(SqlValueSupport.strOr(null, "d")).isEqualTo("d");
    }
}