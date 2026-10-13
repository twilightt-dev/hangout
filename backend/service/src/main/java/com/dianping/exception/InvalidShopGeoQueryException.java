package com.dianping.exception;

/** Thrown when distance sorting is requested without a valid user location. */
public class InvalidShopGeoQueryException extends RuntimeException {

    public InvalidShopGeoQueryException() {
        super("距离排序需要有效的经纬度");
    }
}
