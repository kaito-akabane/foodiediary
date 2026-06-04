package foodiediary.restaurant.loader;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.restaurant")
public class RestaurantLoaderProperties {

    private int batchSize = 1000;
}
