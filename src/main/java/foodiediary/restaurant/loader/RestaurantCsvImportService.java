package foodiediary.restaurant.loader;

import java.io.BufferedReader;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestaurantCsvImportService {

    private final RestaurantRefinedImporter refinedImporter;
    private final RestaurantLoaderProperties properties;

    @Transactional
    public int importFromCsv(BufferedReader reader, String sourceDescription) throws IOException {
        int batchSize = Math.max(1, properties.getBatchSize());
        return refinedImporter.importToRestaurant(reader, batchSize, sourceDescription);
    }
}
