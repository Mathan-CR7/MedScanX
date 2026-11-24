package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.model.MedicineMaster;
import com.kovanlabs.ai_medicine_assistant.repository.MedicineMasterRepository;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dynamic medicine catalog ingestion service.
 * Scrapes multiple pharmacy category pages across PharmEasy and 1mg
 * to populate medicine_master with real, live branded medicine records.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MedicineCatalogIngestionService {

    private final MedicineMasterRepository masterRepository;
    private final OCRNormalizationService normalizationService;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36";

    private static final Pattern STRENGTH_PATTERN =
            Pattern.compile("(\\d{1,4}(?:\\.\\d+)?\\s*(?:mg|mcg|gm|ml|g|iu|%|IU))", Pattern.CASE_INSENSITIVE);

    public record IngestionStats(int addedCount, int updatedCount, int skippedCount, int failedCount) {}

    /**
     * Multi-category dynamic catalog ingestion.
     * Scrapes several pharmacy search pages covering diabetes, cardiac, antibiotics,
     * vitamins, pain relief, and general medicine categories.
     */
    @Transactional
    public IngestionStats ingestCatalog() {
        log.info("[CatalogIngestion] Starting multi-category dynamic ingestion from PharmEasy & 1mg...");

        List<MedicineMaster> scrapedRecords = new ArrayList<>();

        // Multi-category search URLs covering major Indian medicine categories
        List<CatalogSource> sources = List.of(
            new CatalogSource("PharmEasy", "https://pharmeasy.in/search/all?name=tablet",              "Tablet"),
            new CatalogSource("PharmEasy", "https://pharmeasy.in/search/all?name=diabetes+tablet",     "Tablet"),
            new CatalogSource("PharmEasy", "https://pharmeasy.in/search/all?name=blood+pressure",      "Tablet"),
            new CatalogSource("PharmEasy", "https://pharmeasy.in/search/all?name=antibiotic+tablet",   "Tablet"),
            new CatalogSource("PharmEasy", "https://pharmeasy.in/search/all?name=paracetamol",         "Tablet"),
            new CatalogSource("PharmEasy", "https://pharmeasy.in/search/all?name=vitamin",             "Capsule"),
            new CatalogSource("PharmEasy", "https://pharmeasy.in/search/all?name=cholesterol",        "Tablet"),
            new CatalogSource("PharmEasy", "https://pharmeasy.in/search/all?name=thyroid",            "Tablet"),
            new CatalogSource("PharmEasy", "https://pharmeasy.in/search/all?name=antacid",            "Tablet"),
            new CatalogSource("1mg",       "https://www.1mg.com/search/all?name=diabetes+medicine",    "Tablet"),
            new CatalogSource("1mg",       "https://www.1mg.com/search/all?name=blood+pressure",       "Tablet"),
            new CatalogSource("1mg",       "https://www.1mg.com/search/all?name=paracetamol+tablet",   "Tablet")
        );

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );

            BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                    .setUserAgent(USER_AGENT)
                    .setViewportSize(1366, 768)
                    .setLocale("en-IN")
            );

            Page page = context.newPage();
            page.setDefaultTimeout(30000);

            for (CatalogSource source : sources) {
                try {
                    List<MedicineMaster> pageRecords = scrapeOneCategoryPage(page, source);
                    scrapedRecords.addAll(pageRecords);
                    log.info("[CatalogIngestion] Source '{}' url='{}' -> {} records scraped",
                            source.site(), source.url(), pageRecords.size());
                } catch (Exception e) {
                    log.warn("[CatalogIngestion] Failed to scrape source '{}' url='{}': {}",
                            source.site(), source.url(), e.getMessage());
                }
            }

            browser.close();
        } catch (Exception e) {
            log.error("[CatalogIngestion] Playwright browser failed to initialize: {}", e.getMessage());
            throw new IllegalStateException("Catalog ingestion failed: Playwright initialization error: " + e.getMessage(), e);
        }

        if (scrapedRecords.isEmpty()) {
            log.error("[CatalogIngestion] All catalog sources returned 0 records!");
            throw new IllegalStateException("Catalog ingestion failed loudly: zero records retrieved from all sources!");
        }

        return upsertRecords(scrapedRecords);
    }

    /**
     * Scrapes a single category search results page and extracts MedicineMaster records from product cards.
     */
    private List<MedicineMaster> scrapeOneCategoryPage(Page page, CatalogSource source) {
        List<MedicineMaster> records = new ArrayList<>();

        try {
            page.navigate(source.url(), new Page.NavigateOptions()
                    .setTimeout(25000)
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        } catch (Exception e) {
            log.debug("[CatalogIngestion][{}] Navigation timed out (continuing): {}", source.site(), e.getMessage());
        }

        // Wait for product cards to appear
        String[] cardSelectors = {
            "div[class*='ProductCard']",
            "div[class*='productCard']",
            "div[class*='product-card']",
            "div[class*='style__product']",
            "div[class*='SearchProductCard']"
        };
        for (String sel : cardSelectors) {
            try {
                page.waitForSelector(sel, new Page.WaitForSelectorOptions().setTimeout(8000));
                break;
            } catch (Exception ignored) {}
        }

        // Scroll to trigger lazy-loading
        try {
            page.evaluate("window.scrollBy(0, 1000)");
            Thread.sleep(600);
            page.evaluate("window.scrollBy(0, 1000)");
        } catch (Exception ignored) {}

        // Locate product cards using combined selector
        Locator cards = page.locator(String.join(", ", cardSelectors));
        int count = cards.count();
        log.debug("[CatalogIngestion][{}] Found {} cards on page: {}", source.site(), count, source.url());

        for (int i = 0; i < Math.min(count, 40); i++) {
            try {
                Locator card = cards.nth(i);
                String cardText = card.innerText();
                if (cardText == null || cardText.isBlank()) continue;

                String[] lines = cardText.split("\n");
                String brandName = cleanBrandName(lines[0].trim());
                if (brandName.length() < 3 || brandName.length() > 80) continue;

                // Extract generic name from subsequent lines
                String genericName = extractGenericName(lines);

                // Extract strength from brand name or generic
                String strength = extractStrength(brandName + " " + genericName);

                // Extract manufacturer from "by XYZ" pattern
                String manufacturer = extractManufacturer(cardText);

                // Extract product URL for sourceReference
                String productUrl = source.url() + "#item-" + i;
                try {
                    Locator link = card.locator("a[href]");
                    if (link.count() > 0) {
                        String href = link.first().getAttribute("href");
                        if (href != null && !href.isBlank()) {
                            productUrl = href.startsWith("http") ? href
                                    : (source.site().equals("PharmEasy") ? "https://pharmeasy.in" : "https://www.1mg.com") + href;
                        }
                    }
                } catch (Exception ignored) {}

                MedicineMaster master = MedicineMaster.builder()
                        .brandName(brandName)
                        .normalizedBrandName(normalizationService.normalize(brandName))
                        .genericName(genericName)
                        .normalizedGenericName(normalizationService.normalize(genericName))
                        .strength(strength)
                        .dosageForm(source.defaultForm())
                        .manufacturer(manufacturer)
                        .normalizedManufacturer(normalizationService.normalize(manufacturer))
                        .source(source.site())
                        .sourceReference(productUrl)
                        .verified(true)
                        .build();

                records.add(master);
            } catch (Exception ignored) {}
        }

        return records;
    }

    /**
     * Upserts scraped records into the database using brand+strength+manufacturer as unique key.
     */
    private IngestionStats upsertRecords(List<MedicineMaster> records) {
        int added = 0, updated = 0, skipped = 0, failed = 0;

        for (MedicineMaster record : records) {
            try {
                Optional<MedicineMaster> existing = masterRepository
                        .findByNormalizedBrandNameAndStrengthAndNormalizedManufacturer(
                                record.getNormalizedBrandName(),
                                record.getStrength(),
                                record.getNormalizedManufacturer());

                if (existing.isPresent()) {
                    MedicineMaster e = existing.get();
                    e.setBrandName(record.getBrandName());
                    e.setGenericName(record.getGenericName());
                    e.setNormalizedGenericName(record.getNormalizedGenericName());
                    e.setDosageForm(record.getDosageForm());
                    e.setManufacturer(record.getManufacturer());
                    e.setSource(record.getSource());
                    e.setSourceReference(record.getSourceReference());
                    masterRepository.save(e);
                    updated++;
                } else {
                    masterRepository.save(record);
                    added++;
                }
            } catch (Exception e) {
                log.debug("[CatalogIngestion] Failed to upsert '{}': {}", record.getBrandName(), e.getMessage());
                failed++;
            }
        }

        log.info("[CatalogIngestion] Upsert complete: Added={}, Updated={}, Skipped={}, Failed={}",
                added, updated, skipped, failed);
        return new IngestionStats(added, updated, skipped, failed);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String cleanBrandName(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[™®©]", "")
                  .replaceAll("[^a-zA-Z0-9\\s\\-]", " ")
                  .replaceAll("\\s+", " ")
                  .trim();
    }

    private String extractGenericName(String[] lines) {
        for (int i = 1; i < Math.min(lines.length, 5); i++) {
            String line = lines[i].trim();
            if (!line.isBlank() && line.length() > 3
                    && !line.toLowerCase().contains("by ")
                    && !line.toLowerCase().contains("strip")
                    && !line.toLowerCase().contains("tablet")
                    && !line.toLowerCase().contains("capsule")
                    && !line.matches(".*\\d{2,}.*")) {
                return line;
            }
        }
        return "Unspecified Formulation";
    }

    private String extractStrength(String text) {
        if (text == null) return "";
        Matcher m = STRENGTH_PATTERN.matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    private String extractManufacturer(String cardText) {
        if (cardText == null) return "Unknown Manufacturer";
        String[] lines = cardText.split("\n");
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT).trim();
            if (lower.startsWith("by ") && line.length() > 3) {
                return line.replaceFirst("(?i)by\\s+", "").trim();
            }
            if (lower.contains("pharmaceuticals") || lower.contains("pharma") || lower.contains("labs")
                    || lower.contains("healthcare") || lower.contains("biocon") || lower.contains("cipla")) {
                return line.trim();
            }
        }
        return "Unknown Manufacturer";
    }

    private record CatalogSource(String site, String url, String defaultForm) {}
}
