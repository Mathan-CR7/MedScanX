package com.kovanlabs.ai_medicine_assistant.service.scraper;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApolloScraperService {

    private final ScraperHelper scraperHelper;

    public BigDecimal scrapePrice(Page page, String medicineName) {
        try {
            String encoded = URLEncoder.encode(medicineName, StandardCharsets.UTF_8).replace("+", "%20");
            String url = "https://www.apollopharmacy.in/search-medicines/" + encoded;
            log.info("[Apollo] Navigating to: {}", url);
            try {
                page.navigate(url, new Page.NavigateOptions().setTimeout(25000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            } catch (Exception e) {
                log.debug("[Apollo] Navigation load finished early or timed out: {}", e.getMessage());
            }

            // Wait for dynamic Apollo search result elements to populate
            List<String> apolloSelectors = List.of(
                    "div[class*='ProductCard']",
                    "div[class*='productCard']",
                    "div[class*='MedicineCard']",
                    "div[class*='SearchProductCard']",
                    "a[href*='/otc/']",
                    "a[href*='/medicine/']",
                    "div[class*='product_card']"
            );

            for (String selector : apolloSelectors) {
                try {
                    page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(8000));
                    break;
                } catch (Exception ignored) {}
            }

            try {
                page.evaluate("window.scrollBy(0, 400)");
            } catch (Exception ignored) {}

            log.info("[Apollo] Page title: '{}'", page.title());

            // 1. Try card matching extraction
            BigDecimal cardPrice = scraperHelper.extractPriceFromMatchingCard(page, "apollo", medicineName);
            if (cardPrice != null) {
                return cardPrice;
            }

            if (scraperHelper.isSearchPage(page.url())) {
                log.info("[Apollo] No matching product card on search results page for '{}'. Skipping direct selector fallback.", medicineName);
                return null;
            }

            if (!scraperHelper.pageMatchesMedicine(page, medicineName)) {
                log.info("[Apollo] Product page detail does not match brand token for '{}'. Skipping direct selector fallback.", medicineName);
                return null;
            }

            // 2. Direct selector fallback on Apollo page
            Locator priceLocators = page.locator("span[class*='price'], p[class*='price'], div[class*='ProductCard_price']");
            int count = priceLocators.count();
            log.info("[Apollo] Direct price locator count: {}", count);
            for (int i = 0; i < Math.min(count, 10); i++) {
                try {
                    String text = priceLocators.nth(i).innerText();
                    BigDecimal price = scraperHelper.parsePriceFromExactText(text);
                    if (price != null) {
                        log.info("[Apollo] Extracted selling price ₹{} from locator index {}", price, i);
                        return price;
                    }
                } catch (Exception ignored) {}
            }

            log.warn("[Apollo] No valid product card or selling price found matching '{}'", medicineName);
            return null;

        } catch (Exception e) {
            log.warn("[Apollo] Scrape error: {}", e.getMessage());
            return null;
        }
    }
}
