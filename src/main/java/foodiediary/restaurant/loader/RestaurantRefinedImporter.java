package foodiediary.restaurant.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestaurantRefinedImporter {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "id", "business_name", "coord_x", "coord_y", "full_address");

    private static final String RESTAURANT_INSERT = """
            INSERT INTO restaurant (id, business_name, coord_x, coord_y, full_address)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public int importToRestaurant(BufferedReader reader, int batchSize, String sourceDescription) throws IOException {
        String headerLine = reader.readLine();
        if (headerLine == null) {
            log.warn("CSV 헤더가 없습니다: {}", sourceDescription);
            return 0;
        }

        Map<String, Integer> columnIndex = parseHeader(headerLine);
        if (!validateHeaders(columnIndex, sourceDescription)) {
            return 0;
        }

        int inserted = 0;
        int skipped = 0;
        List<Object[]> batch = new ArrayList<>(batchSize);
        String line;

        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = RestaurantCsvLineParser.parseLine(line);
            try {
                String idRaw = required(fields, columnIndex, "id");
                String businessName = required(fields, columnIndex, "business_name");
                Double coordX = requiredDouble(fields, columnIndex, "coord_x");
                Double coordY = requiredDouble(fields, columnIndex, "coord_y");
                String address = required(fields, columnIndex, "full_address");

                batch.add(new Object[]{parseRestaurantId(idRaw), businessName, coordX, coordY, address});
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
            log.warn("CSV 파싱 실패로 건너뛴 행: {}건 ({})", skipped, sourceDescription);
        }
        log.info("CSV → restaurant {}건 적재 ({})", inserted, sourceDescription);
        return inserted;
    }

    static int parseRestaurantId(String idRaw) {
        String trimmed = idRaw.trim();
        if (trimmed.matches("\\d+")) {
            return Integer.parseInt(trimmed);
        }
        return hashMgtNo(trimmed);
    }

    static int hashMgtNo(String mgtNo) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(mgtNo.replace("-", "").getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(digest);
            long value = Long.parseUnsignedLong(hex.substring(0, 8), 16);
            return (int) (value & 0x7FFFFFFFL);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    private boolean validateHeaders(Map<String, Integer> columnIndex, String sourceDescription) {
        List<String> missing = new ArrayList<>();
        for (String header : REQUIRED_HEADERS) {
            if (!columnIndex.containsKey(header)) {
                missing.add(header);
            }
        }
        if (!missing.isEmpty()) {
            log.error("CSV 필수 컬럼이 없습니다 ({}): {}", sourceDescription, missing);
            return false;
        }
        return true;
    }

    private int flushBatch(List<Object[]> batch) {
        jdbcTemplate.batchUpdate(RESTAURANT_INSERT, batch);
        return batch.size();
    }

    private String required(String[] fields, Map<String, Integer> columnIndex, String header) {
        String value = optional(fields, columnIndex, header);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("필수 값 없음: " + header);
        }
        return value;
    }

    private Double requiredDouble(String[] fields, Map<String, Integer> columnIndex, String header) {
        String raw = required(fields, columnIndex, header);
        return Double.parseDouble(raw);
    }

    private String optional(String[] fields, Map<String, Integer> columnIndex, String header) {
        int index = columnIndex.get(header);
        if (index < 0 || index >= fields.length) {
            return null;
        }
        String raw = fields[index].trim();
        return raw.isEmpty() ? null : raw;
    }

    private Map<String, Integer> parseHeader(String headerLine) {
        Map<String, Integer> columnIndex = new HashMap<>();
        String[] headers = RestaurantCsvLineParser.parseLine(RestaurantCsvLineParser.stripBom(headerLine));
        for (int i = 0; i < headers.length; i++) {
            columnIndex.put(RestaurantCsvLineParser.normalizeHeader(headers[i]), i);
        }
        return columnIndex;
    }
}
