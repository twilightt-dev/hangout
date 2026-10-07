package com.dianping.service;

/** Validation shared by HTTP input and GEO index maintenance. */
public final class ShopGeoValidator {

    private ShopGeoValidator() {
    }

    public static boolean isValidCoordinate(Double longitude, Double latitude) {
        return longitude != null && latitude != null
                && Double.isFinite(longitude) && Double.isFinite(latitude)
                && longitude >= -180D && longitude <= 180D
                && latitude >= -90D && latitude <= 90D;
    }
}
