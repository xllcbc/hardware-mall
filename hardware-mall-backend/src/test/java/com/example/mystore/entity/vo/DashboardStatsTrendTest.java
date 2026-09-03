package com.example.mystore.entity.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardStatsTrendTest {

    @Test
    void yesterdayZero_todayPositive_returns100() {
        assertThat(DashboardStatsVO.percentTrend(5, 0)).isEqualTo(100);
    }

    @Test
    void yesterdayZero_todayZero_returns0() {
        assertThat(DashboardStatsVO.percentTrend(0, 0)).isEqualTo(0);
    }

    @Test
    void positiveGrowth() {
        assertThat(DashboardStatsVO.percentTrend(5, 4)).isEqualTo(25);
    }

    @Test
    void negativeGrowth() {
        assertThat(DashboardStatsVO.percentTrend(4, 8)).isEqualTo(-50);
    }

    @Test
    void rounding_roundsHalfAwayFromZero() {
        // (1-3)*100/3 = -66.67 → -67
        assertThat(DashboardStatsVO.percentTrend(1, 3)).isEqualTo(-67);
        // (2-3)*100/3 = -33.33 → -33
        assertThat(DashboardStatsVO.percentTrend(2, 3)).isEqualTo(-33);
        // (4-3)*100/3 = 33.33 → 33
        assertThat(DashboardStatsVO.percentTrend(4, 3)).isEqualTo(33);
    }

    @Test
    void equalValues_returns0() {
        assertThat(DashboardStatsVO.percentTrend(7, 7)).isEqualTo(0);
    }
}
