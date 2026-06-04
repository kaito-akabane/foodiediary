package foodiediary.restaurant.loader;

import foodiediary.restaurant.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(RestaurantLoaderProperties.class)
public class RestaurantCsvLoader {

    private static final List<String> ID_HEADERS = List.of("번호", "관리번호", "id");
    private static final List<String> NAME_HEADERS = List.of("사업장명", "업소명", "business_name");
    private static final List<String> COORD_X_HEADERS = List.of("좌표정보x", "좌표정보(x)", "coord_x");
    private static final List<String> COORD_Y_HEADERS = List.of("좌표정보y", "좌표정보(y)", "coord_y");
    private static final List<String> ADDRESS_HEADERS = List.of(
            "소재지전체주소", "도로명전체주소", "지번주소", "full_address");

    private final RestaurantRepository restaurantRepository;
    private final RestaurantLoaderProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final ResourceLoader resourceLoader;

    @EventListener(ApplicationReadyEvent.class)
    public void loadIfEmpty() {
        if (restaurantRepository.count() > 0) {
            log.info("restaurant 테이블에 데이터가 있어 CSV 적재를 건너뜁니다.");
            return;
        }

        CsvSource source = resolveCsvSource();
        if (source == null) {
            log.warn("음식점 CSV 파일이 없습니다: {} — /restaurant/nearby 는 빈 결과를 반환합니다.",
                    properties.getCsvPath());
            return;
        }

        try (BufferedReader reader = source.openReader()) {
            int inserted = loadCsv(reader, source.description());
            log.info("음식점 CSV 적재 완료: {}건 ({})", inserted, source.description());
        } catch (IOException e) {
            jdbcTemplate.update("DELETE FROM restaurant");
            log.error("음식점 CSV 적재 실패: {} — 부분 적재 데이터를 삭제했습니다.", source.description(), e);
        }
    }

    private CsvSource resolveCsvSource() {
        String configuredPath = properties.getCsvPath();
        if (!StringUtils.hasText(configuredPath)) {
            return null;
        }

        if (configuredPath.startsWith("classpath:")) {
            Resource resource = resourceLoader.getResource(configuredPath);
            if (!resource.exists()) {
                return null;
            }
            return new CsvSource(configuredPath, () -> {
                byte[] bytes = resource.getInputStream().readAllBytes();
                Charset charset = detectCharset(bytes);
                return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), charset));
            });
        }

        Path filePath = Path.of(configuredPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(filePath)) {
            return null;
        }
        return new CsvSource(filePath.toString(), () -> {
            byte[] bytes = Files.readAllBytes(filePath);
            Charset charset = detectCharset(bytes);
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), charset));
        });
    }

    int loadCsv(BufferedReader reader, String sourceDescription) throws IOException {
        int batchSize = Math.max(1, properties.getBatchSize());
        int inserted = 0;
        int skipped = 0;

        String headerLine = reader.readLine();
        if (headerLine == null) {
            log.warn("CSV 헤더가 없습니다: {}", sourceDescription);
            return 0;
        }

        Map<String, Integer> columnIndex = parseHeader(headerLine);
        Integer idIdx = findColumn(columnIndex, ID_HEADERS);
        Integer nameIdx = findColumn(columnIndex, NAME_HEADERS);
        Integer xIdx = findColumn(columnIndex, COORD_X_HEADERS);
        Integer yIdx = findColumn(columnIndex, COORD_Y_HEADERS);
        Integer addressIdx = findColumn(columnIndex, ADDRESS_HEADERS);

        if (idIdx == null || nameIdx == null || xIdx == null || yIdx == null) {
            log.error("CSV 필수 컬럼을 찾을 수 없습니다. id={}, name={}, coord_x={}, coord_y={}",
                    idIdx, nameIdx, xIdx, yIdx);
            return 0;
        }

        List<Object[]> batch = new ArrayList<>(batchSize);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }

            String[] fields = parseCsvLine(line);
            try {
                int id = Integer.parseInt(strip(fields, idIdx));
                String businessName = strip(fields, nameIdx);
                double coordX = Double.parseDouble(strip(fields, xIdx));
                double coordY = Double.parseDouble(strip(fields, yIdx));
                String fullAddress = addressIdx != null ? strip(fields, addressIdx) : null;

                batch.add(new Object[]{id, businessName, coordX, coordY, fullAddress});
                if (batch.size() >= batchSize) {
                    inserted += flushBatch(batch);
                    batch.clear();
                }
            } catch (RuntimeException e) {
                skipped++;
            }
        }

        if (!batch.isEmpty()) {
            inserted += flushBatch(batch);
        }

        if (skipped > 0) {
            log.warn("CSV 파싱 실패로 건너뛴 행: {}건", skipped);
        }
        return inserted;
    }

    private int flushBatch(List<Object[]> batch) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO restaurant (id, business_name, coord_x, coord_y, full_address) VALUES (?, ?, ?, ?, ?)",
                batch);
        return batch.size();
    }

    private Map<String, Integer> parseHeader(String headerLine) {
        Map<String, Integer> columnIndex = new HashMap<>();
        String[] headers = parseCsvLine(headerLine);
        for (int i = 0; i < headers.length; i++) {
            columnIndex.put(normalizeHeader(headers[i]), i);
        }
        return columnIndex;
    }

    private Integer findColumn(Map<String, Integer> columnIndex, List<String> candidates) {
        for (String candidate : candidates) {
            Integer index = columnIndex.get(normalizeHeader(candidate));
            if (index != null) {
                return index;
            }
        }
        return null;
    }

    private String normalizeHeader(String header) {
        return header.trim()
                .replace("\uFEFF", "")
                .toLowerCase(Locale.ROOT)
                .replace(" ", "");
    }

    private String strip(String[] fields, int index) {
        if (index < 0 || index >= fields.length) {
            return "";
        }
        return fields[index].trim();
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(String[]::new);
    }

    private Charset detectCharset(byte[] sample) {
        if (sample.length >= 3
                && sample[0] == (byte) 0xEF
                && sample[1] == (byte) 0xBB
                && sample[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }
        int limit = Math.min(sample.length, 4096);
        for (int i = 0; i < limit; i++) {
            if (sample[i] < 0) {
                return Charset.forName("EUC-KR");
            }
        }
        return StandardCharsets.UTF_8;
    }

    @FunctionalInterface
    private interface ReaderSupplier {
        BufferedReader open() throws IOException;
    }

    private record CsvSource(String description, ReaderSupplier readerSupplier) {
        BufferedReader openReader() throws IOException {
            return readerSupplier.open();
        }
    }
}
