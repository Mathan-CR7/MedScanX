package com.kovanlabs.ai_medicine_assistant.config;

import com.kovanlabs.ai_medicine_assistant.service.BrandCandidateGenerator;
import com.kovanlabs.ai_medicine_assistant.service.BrandCandidateScoringService;
import com.kovanlabs.ai_medicine_assistant.service.OcrService;
import com.kovanlabs.ai_medicine_assistant.service.ScraperService;
import com.kovanlabs.ai_medicine_assistant.service.impl.ImagePreprocessingService;
import com.kovanlabs.ai_medicine_assistant.service.impl.PaddleOcrService;
import com.kovanlabs.ai_medicine_assistant.service.impl.PlaywrightScraperService;
import com.kovanlabs.ai_medicine_assistant.service.scraper.ApolloScraperService;
import com.kovanlabs.ai_medicine_assistant.service.scraper.NetMedsScraperService;
import com.kovanlabs.ai_medicine_assistant.service.scraper.OneMgScraperService;
import com.kovanlabs.ai_medicine_assistant.service.scraper.PharmEasyScraperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@Slf4j
public class AppConfig {

    @Bean(name = "scraperExecutor")
    public Executor scraperExecutor() {
        log.info("Creating Java 21 Virtual Thread Executor for concurrent scraping...");
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public OcrService ocrService(ImagePreprocessingService preprocessingService,
                                 BrandCandidateGenerator candidateGenerator,
                                 BrandCandidateScoringService candidateScoringService) {
        log.info("Configuring PaddleOCR OcrService bean with ImagePreprocessingService and candidate scoring...");
        return new PaddleOcrService(preprocessingService, candidateGenerator, candidateScoringService);
    }

    @Bean
    public ScraperService scraperService(Executor scraperExecutor,
                                         OneMgScraperService oneMgScraperService,
                                         ApolloScraperService apolloScraperService,
                                         NetMedsScraperService netMedsScraperService,
                                         PharmEasyScraperService pharmEasyScraperService) {
        log.info("Configuring Playwright ScraperService bean...");
        return new PlaywrightScraperService(scraperExecutor, oneMgScraperService, apolloScraperService, netMedsScraperService, pharmEasyScraperService);
    }
}
