package com.kovanlabs.ai_medicine_assistant.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.ai_medicine_assistant.model.MedicineMaster;
import com.kovanlabs.ai_medicine_assistant.repository.MedicineMasterRepository;
import com.kovanlabs.ai_medicine_assistant.service.MedicineCatalogIngestionService;
import com.kovanlabs.ai_medicine_assistant.service.OCRNormalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Database Seeder:
 * Automatically seeds all 5,000 rich medicine records into MySQL DB on application startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final MedicineMasterRepository masterRepository;
    private final MedicineCatalogIngestionService catalogIngestionService;
    private final OCRNormalizationService normalizationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int TARGET_RECORD_COUNT = 5000;

    @Override
    public void run(String... args) {
        long existing = masterRepository.count();
        log.info("[DatabaseSeeder] Checking medicine_master DB table record count: {}", existing);

        if (existing < TARGET_RECORD_COUNT) {
            log.info("[DatabaseSeeder] DB has {} records (target: {}). Seeding all 5,000 medicine records into database...", existing, TARGET_RECORD_COUNT);
            int jsonSaved = seedFromClasspathJson();
            long total = masterRepository.count();
            log.info("[DatabaseSeeder] Successfully seeded {} records. Total DB medicine_master count = {}", jsonSaved, total);
        } else {
            log.info("[DatabaseSeeder] medicine_master already populated with {} records.", existing);
        }
    }

    /**
     * Seeds all 5,000 Indian Medicine Catalogue entries from classpath JSON resource.
     */
    private int seedFromClasspathJson() {
        int count = 0;
        try {
            ClassPathResource resource = new ClassPathResource("data/medicine_catalogue.json");
            if (!resource.exists()) {
                log.error("[DatabaseSeeder] Dataset resource 'data/medicine_catalogue.json' NOT found!");
                return 0;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                List<Map<String, String>> rawEntries = objectMapper.readValue(
                        inputStream,
                        new TypeReference<List<Map<String, String>>>() {}
                );

                log.info("[DatabaseSeeder] Parsing {} medicine entries from 'data/medicine_catalogue.json'...", rawEntries.size());

                List<MedicineMaster> batch = new ArrayList<>();
                for (Map<String, String> entry : rawEntries) {
                    String brandName = entry.getOrDefault("brandName", "").trim();
                    String genericName = entry.getOrDefault("genericName", "").trim();
                    String strength = entry.getOrDefault("strength", "").trim();
                    String dosageForm = entry.getOrDefault("dosageForm", "Tablet").trim();
                    String manufacturer = entry.getOrDefault("manufacturer", "Known Manufacturer").trim();

                    if (brandName.isEmpty()) continue;

                    String normBrand = normalizationService.normalizeCompact(brandName);
                    String normGeneric = normalizationService.normalize(genericName);

                    MedicineMaster master = MedicineMaster.builder()
                            .brandName(brandName)
                            .genericName(genericName)
                            .strength(strength)
                            .dosageForm(dosageForm)
                            .manufacturer(manufacturer)
                            .normalizedBrandName(normBrand)
                            .normalizedGenericName(normGeneric)
                            .build();

                    batch.add(master);

                    if (batch.size() >= 500) {
                        masterRepository.saveAll(batch);
                        count += batch.size();
                        batch.clear();
                    }
                }

                if (!batch.isEmpty()) {
                    masterRepository.saveAll(batch);
                    count += batch.size();
                    batch.clear();
                }
            }
        } catch (Exception e) {
            log.error("[DatabaseSeeder] Error seeding dataset from JSON: {}", e.getMessage(), e);
        }
        return count;
    }
}
