package com.kovanlabs.ai_medicine_assistant.scheduler;

import com.kovanlabs.ai_medicine_assistant.service.MedicinePriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class ScraperScheduler {

    private final MedicinePriceService medicinePriceService;

    // Scheduled to run every 6 hours
    @Scheduled(cron = "${app.scraper.cron:0 0 */6 * * *}")
    public void scrapeScheduledMedicines() {
        log.info("Scheduled task triggered: scraping all registered medicines...");
        try {
            medicinePriceService.scrapeAllMedicines();
            log.info("Scheduled scraping task completed successfully.");
        } catch (Exception e) {
            log.error("Scheduled scraping task failed", e);
        }
    }
}
