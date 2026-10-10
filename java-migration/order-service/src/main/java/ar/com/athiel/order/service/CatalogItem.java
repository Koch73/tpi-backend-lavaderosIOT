package ar.com.athiel.order.service;

import java.math.BigDecimal;

/** A sellable service: price and duration are decided here, never by the browser. */
public record CatalogItem(String code, String title, BigDecimal price, int durationSeconds, String deviceId) {
}
