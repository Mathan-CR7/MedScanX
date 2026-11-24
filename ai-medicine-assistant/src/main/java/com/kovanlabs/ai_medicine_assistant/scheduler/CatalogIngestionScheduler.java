package com.kovanlabs.ai_medicine_assistant.scheduler;

import com.kovanlabs.ai_medicine_assistant.service.MedicineCatalogIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class CatalogIngestionScheduler {

    private final MedicineCatalogIngestionService catalogIngestionService;

    // Scheduled to run daily at 02:00 AM
    @Scheduled(cron = "${app.catalog.ingestion.cron:0 0 2 * * *}")
    public void runScheduledCatalogIngestion() {
        log.info("Scheduled task triggered: starting daily medicine catalog ingestion...");
        try {
            catalogIngestionService.ingestCatalog();
            log.info("Scheduled daily catalog ingestion completed successfully.");
        } catch (Exception e) {
            log.error("Scheduled catalog ingestion failed loudly", e);
        }
    }
}
