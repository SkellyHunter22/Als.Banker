package com.alexander.alsbanker.api;

/**
 * A player's holding in a single stock, for rendering a portfolio screen.
 *
 * avgCost is the player's average purchase price per share; currentPrice is the live market price.
 */
public record StockHolding(String symbol, String name, double shares, double avgCost, double currentPrice) {
}
