package com.kovanlabs.ai_medicine_assistant.service;
import com.kovanlabs.ai_medicine_assistant.model.Medicine;
import com.kovanlabs.ai_medicine_assistant.model.MedicinePrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.springframework.cache.annotation.Cacheable;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedicinePriceService {

    private final TrackedMedicineService trackedMedicineService;
    private final ScraperService scraperService;

    private static final List<String> WEBSITES = Arrays.asList("1mg", "Apollo", "NetMeds", "PharmEasy");

    @Cacheable(value = "medicinePrices", key = "#medicineName != null ? #medicineName.trim().replaceAll('\\s+', ' ').toLowerCase() : ''", unless = "#result == null || #result.isEmpty()")
    public List<MedicinePrice> getPricesForMedicine(String medicineName) {
        if (medicineName == null || medicineName.isBlank()) return Collections.emptyList();
        String cleanName = medicineName.trim().replaceAll("\\s+", " ");
        log.info("Checking prices for: '{}'", cleanName);
        return scrapePricesForMedicine(cleanName);
    }

    public List<MedicinePrice> scrapePricesForMedicine(String medicineName) {
        log.info("Triggering concurrent Playwright scraping for: '{}'", medicineName);

        List<CompletableFuture<MedicinePrice>> futures = WEBSITES.stream()
                .map(website -> scraperService.scrapeWebsite(medicineName, website))
                .toList();

        // Wait for all futures independently without cancelling on single failure
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .exceptionally(ex -> null)
                .join();

        return futures.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        log.error("Error gathering scraped price result", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public void scrapeAllMedicines() {
        List<Medicine> trackedMedicines = trackedMedicineService.getTrackedMedicines();
        log.info("Scheduler started. Scraping {} tracked medicines...", trackedMedicines.size());

        trackedMedicines.forEach(medicine -> {
            try {
                scrapePricesForMedicine(medicine.getName());
            } catch (Exception e) {
                log.error("Failed to scrape prices for medicine: {}", medicine.getName(), e);
            }
        });
        log.info("Scheduler scraping complete.");
    }
}