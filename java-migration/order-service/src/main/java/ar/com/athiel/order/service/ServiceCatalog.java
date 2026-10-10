package ar.com.athiel.order.service;

import ar.com.athiel.order.OrderProperties;
import ar.com.athiel.order.domain.Settings;
import ar.com.athiel.order.domain.SettingsRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Today only WASH exists (the single Settings row). FOAM and VACUUM can be
 * added here as new items bound to their own device ids.
 */
@Component
public class ServiceCatalog {

    public static final String WASH = "WASH";

    private final SettingsRepository settings;
    private final OrderProperties props;

    public ServiceCatalog(SettingsRepository settings, OrderProperties props) {
        this.settings = settings;
        this.props = props;
    }

    public CatalogItem require(String code) {
        if (!WASH.equals(code)) {
            throw new NotFoundException("Servicio '" + code + "' inexistente");
        }
        Settings s = settings.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new NotFoundException("Configuración inexistente"));
        return new CatalogItem(WASH, "Lavado", s.getWashPrice(), s.durationSeconds(), props.washDeviceId());
    }

    public List<String> deviceIds() {
        return List.of(props.washDeviceId());
    }
}
