package com.dianping.VO;

/**
 *
 * @param date 今天日期
 * @param alreadySigned 本次请求前是否签过到
 */
public record SignResultVO(String date, boolean alreadySigned) {
}
