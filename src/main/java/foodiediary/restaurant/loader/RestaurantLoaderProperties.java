package foodiediary.restaurant.loader;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.restaurant")
public class RestaurantLoaderProperties {

    private String csvPath = "classpath:data/restaurant.csv";
    private int batchSize = 1000;
}
