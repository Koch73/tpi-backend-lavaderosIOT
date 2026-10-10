package ar.com.athiel.device;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({DeviceProperties.class, ShellyProperties.class, TuyaProperties.class})
public class DeviceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeviceServiceApplication.class, args);
    }
}
