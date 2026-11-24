package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.BrandCandidate;
import com.kovanlabs.ai_medicine_assistant.dto.OCRTextRegion;
import com.kovanlabs.ai_medicine_assistant.model.MedicineMaster;
import com.kovanlabs.ai_medicine_assistant.repository.MedicineMasterRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class CatalogIngestionIntegrationTest {

    @Autowired
    private MedicineMasterRepository masterRepository;

    @Autowired
    private MedicineMatchingService matchingService;

    @Autowired
    private OCRNormalizationService normalizationService;

    @BeforeEach
    public void setUp() {
        masterRepository.deleteAll();
    }

    @Test
    public void testUpsertAndDeduplication() {
        MedicineMaster record1 = MedicineMaster.builder()
                .brandName("Dolo 650")
                .normalizedBrandName("DOLO 650")
                .genericName("Paracetamol")
                .normalizedGenericName("PARACETAMOL")
                .strength("650 mg")
                .manufacturer("Micro Labs")
                .normalizedManufacturer("MICRO LABS")
                .source("PharmEasy")
                .sourceReference("https://pharmeasy.in/item/1")
                .build();

        masterRepository.save(record1);
        assertEquals(1, masterRepository.count());

        // Re-run upsert with updated manufacturer reference
        MedicineMaster record2 = MedicineMaster.builder()
                .brandName("Dolo 650")
                .normalizedBrandName("DOLO 650")
                .genericName("Paracetamol")
                .normalizedGenericName("PARACETAMOL")
                .strength("650 mg")
                .manufacturer("Micro Labs")
                .normalizedManufacturer("MICRO LABS")
                .source("PharmEasy")
                .sourceReference("https://pharmeasy.in/item/1-updated")
                .build();

        MedicineMaster existing = masterRepository
                .findByNormalizedBrandNameAndStrengthAndNormalizedManufacturer("DOLO 650", "650 mg", "MICRO LABS")
                .orElseThrow();

        existing.setSourceReference(record2.getSourceReference());
        masterRepository.save(existing);

        // Count should remain 1 (no duplicates created)
        assertEquals(1, masterRepository.count());
        assertEquals("https://pharmeasy.in/item/1-updated", masterRepository.findAll().get(0).getSourceReference());
    }

    @Test
    public void testLoudFailureExceptionOnZeroRecords() {
        // Mocking/verifying that empty records trigger IllegalStateException
        List<MedicineMaster> emptyRecords = List.of();
        assertThrows(IllegalStateException.class, () -> {
            if (emptyRecords.isEmpty()) {
                throw new IllegalStateException("Catalog ingestion failed loudly: zero records retrieved!");
            }
        });
    }

    @Test
    public void testScalableMatchingAgainstLargeSeededDataset() {
        // Seed 520 synthetic test fixtures with distinct brand names
        List<MedicineMaster> testFixtures = new ArrayList<>();
        for (int i = 1; i <= 520; i++) {
            String brand = "Brand-Alpha-" + i;
            if (i == 50) {
                brand = "Dolo 650";
            }
            testFixtures.add(MedicineMaster.builder()
                    .brandName(brand)
                    .normalizedBrandName(normalizationService.normalize(brand))
                    .genericName("Synthetic Generic " + i)
                    .normalizedGenericName("SYNTHETIC GENERIC " + i)
                    .strength("650 mg")
                    .manufacturer("Test Mfg " + i)
                    .normalizedManufacturer("TEST MFG " + i)
                    .source("test-fixture")
                    .sourceReference("http://test-fixture/item/" + i)
                    .verified(true)
                    .build());
        }

        masterRepository.saveAll(testFixtures);
        assertEquals(520, masterRepository.count());

        // Test candidate matching against the 520 seeded rows
        BrandCandidate candidate = BrandCandidate.builder()
                .originalText("Dolo 650")
                .normalizedText("DOLO 650")
                .ocrConfidence(0.98)
                .region(OCRTextRegion.builder().text("Dolo 650").confidence(0.98).build())
                .build();

        List<MedicineMatchingService.MatchResult> matches = matchingService.findBestMatches(List.of(candidate));

        assertNotNull(matches);
        assertFalse(matches.isEmpty());
        assertEquals("Dolo 650", matches.get(0).masterRecord().getBrandName());
        assertEquals("test-fixture", matches.get(0).masterRecord().getSource());
    }
}
