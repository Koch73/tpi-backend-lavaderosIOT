package ar.com.athiel.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Single-row commercial configuration, same columns as the Python {@code settings} table. */
@Entity
@Table(name = "settings")
public class Settings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wash_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal washPrice;

    @Column(name = "wash_minutes", nullable = false)
    private int washMinutes;

    @Column(name = "wash_seconds", nullable = false)
    private int washSeconds;

    protected Settings() {
    }

    public Settings(BigDecimal washPrice, int washMinutes, int washSeconds) {
        this.washPrice = washPrice;
        this.washMinutes = washMinutes;
        this.washSeconds = washSeconds;
    }

    public BigDecimal getWashPrice() { return washPrice; }
    public int getWashMinutes() { return washMinutes; }
    public int getWashSeconds() { return washSeconds; }

    public void update(BigDecimal price, int minutes, int seconds) {
        this.washPrice = price;
        this.washMinutes = minutes;
        this.washSeconds = seconds;
    }

    public int durationSeconds() {
        return washMinutes * 60 + washSeconds;
    }
}
