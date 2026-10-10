package ar.com.athiel.order.service;

import ar.com.athiel.order.domain.Settings;
import ar.com.athiel.order.domain.SettingsRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Equivalent of the settings part of init_scripts/seed.py (3500 ARS, 15 minutes). */
@Component
public class SeedData implements ApplicationRunner {

    private final SettingsRepository settings;

    public SeedData(SettingsRepository settings) {
        this.settings = settings;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (settings.count() == 0) {
            settings.save(new Settings(new BigDecimal("3500"), 15, 0));
        }
    }
}
