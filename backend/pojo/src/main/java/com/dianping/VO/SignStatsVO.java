package com.dianping.VO;

public record SignStatsVO(String date, String month, boolean todaySigned,
                          int monthlyDays, int continuousDays,
                          int previousMonthDays, int monthDifference) {
}
