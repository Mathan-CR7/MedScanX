package com.kovanlabs.ai_medicine_assistant.service.impl;

import com.kovanlabs.ai_medicine_assistant.model.MedicinePrice;
import com.kovanlabs.ai_medicine_assistant.service.ScraperService;
import com.kovanlabs.ai_medicine_assistant.service.scraper.ApolloScraperService;
import com.kovanlabs.ai_medicine_assistant.service.scraper.NetMedsScraperService;
import com.kovanlabs.ai_medicine_assistant.service.scraper.OneMgScraperService;
import com.kovanlabs.ai_medicine_assistant.service.scraper.PharmEasyScraperService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

@Slf4j
public class PlaywrightScraperService implements ScraperService {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36";

    private final Executor executor;
    private final OneMgScraperService oneMgScraperService;
    private final ApolloScraperService apolloScraperService;
    private final NetMedsScraperService netMedsScraperService;
    private final PharmEasyScraperService pharmEasyScraperService;

    // Limit concurrent Playwright browser process creation to 2 at a time to prevent OS handle & pipe exhaustion
    private final Semaphore concurrencyLimiter = new Semaphore(2);

    public PlaywrightScraperService(Executor executor,
                                    OneMgScraperService oneMgScraperService,
                                    ApolloScraperService apolloScraperService,
                                    NetMedsScraperService netMedsScraperService,
                                    PharmEasyScraperService pharmEasyScraperService) {
        this.executor = executor;
        this.oneMgScraperService = oneMgScraperService;
        this.apolloScraperService = apolloScraperService;
        this.netMedsScraperService = netMedsScraperService;
        this.pharmEasyScraperService = pharmEasyScraperService;
    }

    @Override
    public CompletableFuture<MedicinePrice> scrapeWebsite(String medicineName, String website) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                concurrencyLimiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return MedicinePrice.builder()
                        .medicineName(medicineName)
                        .website(website)
                        .price(null)
                        .availability("SCRAPE_FAILED")
                        .lastUpdated(LocalDateTime.now())
                        .build();
            }

            try {
                log.info("[{}] Starting price scrape task for medicine '{}'", website, medicineName);

                int maxAttempts = 2;
                BigDecimal price = null;
                boolean scrapeTechnicalError = false;

                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    try (Playwright playwright = Playwright.create()) {
                        Browser browser = playwright.chromium().launch(
                            new BrowserType.LaunchOptions().setHeadless(true)
                        );
                        try (BrowserContext context = browser.newContext(
                            new Browser.NewContextOptions()
                                .setUserAgent(USER_AGENT)
                                .setViewportSize(1366, 768)
                                .setLocale("en-IN")
                                .setTimezoneId("Asia/Kolkata")
                        )) {
                            context.addInitScript(
                                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
                            );

                            // Abort heavy static assets (images, fonts, media) to speed up navigation by 5x-10x
                            context.route("**/*", route -> {
                                try {
                                    String type = route.request().resourceType();
                                    if ("image".equals(type) || "font".equals(type) || "media".equals(type)) {
                                        route.abort();
                                    } else {
                                        route.resume();
                                    }
                                } catch (Exception e) {
                                    try { route.resume(); } catch (Exception ignored) {}
                                }
                            });

                            Page page = context.newPage();
                            page.setDefaultTimeout(30000);

                            price = switch (website.toLowerCase()) {
                                case "1mg" -> oneMgScraperService.scrapePrice(page, medicineName);
                                case "apollo" -> apolloScraperService.scrapePrice(page, medicineName);
                                case "netmeds" -> netMedsScraperService.scrapePrice(page, medicineName);
                                case "pharmeasy" -> pharmEasyScraperService.scrapePrice(page, medicineName);
                                default -> throw new IllegalArgumentException("Unknown website: " + website);
                            };

                            page.close();
                        }
                        browser.close();

                        // If scrape executed without throwing exception, break retry loop
                        scrapeTechnicalError = false;
                        break;

                    } catch (Throwable e) {
                        scrapeTechnicalError = true;
                        log.warn("[{}] Attempt {} failed for '{}': {}", website, attempt, medicineName, e.getMessage());
                        if (attempt < maxAttempts) {
                            log.info("[{}] Retrying scrape with fresh context...", website);
                        }
                    }
                }

                if (scrapeTechnicalError) {
                    log.warn("[{}] Scraping technically failed for '{}' after {} attempts.", website, medicineName, maxAttempts);
                    return MedicinePrice.builder()
                            .medicineName(medicineName)
                            .website(website)
                            .price(null)
                            .availability("SCRAPE_FAILED")
                            .lastUpdated(LocalDateTime.now())
                            .build();
                }

                if (price != null) {
                    log.info("[{}] Successfully scraped actual selling price for '{}': ₹{}", website, medicineName, price);
                    return MedicinePrice.builder()
                            .medicineName(medicineName)
                            .website(website)
                            .price(price)
                            .availability("AVAILABLE")
                            .lastUpdated(LocalDateTime.now())
                            .build();
                } else {
                    log.info("[{}] Website loaded but no matching product price found for '{}'", website, medicineName);
                    return MedicinePrice.builder()
                            .medicineName(medicineName)
                            .website(website)
                            .price(null)
                            .availability("UNAVAILABLE")
                            .lastUpdated(LocalDateTime.now())
                            .build();
                }
            } finally {
                concurrencyLimiter.release();
            }

        }, executor);
    }
}
