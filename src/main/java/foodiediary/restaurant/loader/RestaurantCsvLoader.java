package foodiediary.restaurant.loader;

import foodiediary.restaurant.RestaurantRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(RestaurantLoaderProperties.class)
public class RestaurantCsvLoader {

    /** 동봉 정제 CSV (classpath 고정) */
    public static final String CSV_CLASSPATH = "classpath:data/식품_일반음식점_서울종로구.csv";

    private final RestaurantRepository restaurantRepository;
    private final RestaurantCsvImportService importService;
    private final JdbcTemplate jdbcTemplate;
    private final ResourceLoader resourceLoader;

    @EventListener(ApplicationReadyEvent.class)
    public void loadIfEmpty() {
        if (restaurantRepository.count() > 0) {
            log.info("restaurant 테이블에 데이터가 있어 CSV 적재를 건너뜁니다.");
            return;
        }

        Resource resource = resourceLoader.getResource(CSV_CLASSPATH);
        if (!resource.exists()) {
            log.warn("음식점 CSV 파일이 없습니다: {} — /foodiediary/restaurant/nearby 는 빈 결과를 반환합니다.",
                    CSV_CLASSPATH);
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            int inserted = importService.importFromCsv(reader, CSV_CLASSPATH);
            log.info("음식점 CSV 적재 완료: {}건 ({})", inserted, CSV_CLASSPATH);
        } catch (Exception e) {
            jdbcTemplate.update("DELETE FROM restaurant");
            log.error("음식점 CSV 적재 실패: {} — 부분 적재 데이터를 삭제했습니다.", CSV_CLASSPATH, e);
        }
    }
}
