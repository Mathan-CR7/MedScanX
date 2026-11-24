package com.kovanlabs.ai_medicine_assistant.service.scraper;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ScraperHelper {

    private static final BigDecimal MIN_VALID_PRICE = new BigDecimal("1.0");
    private static final BigDecimal MAX_VALID_PRICE = new BigDecimal("99999.0");

    /**
     * Normalizes text for clean token comparison:
     * Converts hyphens and punctuation to spaces, lowercases, collapses whitespace.
     */
    public String normalizeText(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[-_/,:+|\"]", " ")
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Extracts the primary brand token (the main alphabetic word, e.g. "Dolo" from "Dolo 650").
     */
    public String extractBrandToken(String medicineName) {
        if (medicineName == null || medicineName.isBlank()) return "";
        String normalized = normalizeText(medicineName);
        String[] tokens = normalized.split("\\s+");
        for (String token : tokens) {
            if (token.matches("^[a-z]{2,}$")) {
                return token;
            }
        }
        return tokens.length > 0 ? tokens[0] : "";
    }

    /**
     * Extracts the strength token if present (e.g. "650" from "Dolo 650" or "Dolo 650mg").
     */
    public String extractStrengthToken(String medicineName) {
        if (medicineName == null || medicineName.isBlank()) return "";
        Matcher matcher = Pattern.compile("(\\d{2,4})").matcher(medicineName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * Scores how strongly a candidate product title matches the searched medicine name.
     * Enforces strict word-boundary matching for brand tokens (e.g. "dolo" matches "Dolo-650" but NOT "Dolopar").
     * Returns a score >= 60 for valid matches, 0 for invalid/mismatched brands.
     */
    public int matchBrandAndScore(String cardText, String searchedMedicineName) {
        if (cardText == null || cardText.isBlank() || searchedMedicineName == null || searchedMedicineName.isBlank()) {
            return 0;
        }

        String normCard = normalizeText(cardText);
        String normSearch = normalizeText(searchedMedicineName);
        String brandToken = extractBrandToken(searchedMedicineName);
        String strengthToken = extractStrengthToken(searchedMedicineName);

        if (brandToken.isBlank()) {
            return normCard.contains(normSearch) ? 80 : 0;
        }

        // Strict word boundary check for brand token
        Pattern brandPattern = Pattern.compile("\\b" + Pattern.quote(brandToken) + "\\b");
        if (!brandPattern.matcher(normCard).find()) {
            // Rejection signal: Brand token does not exist as a distinct word
            return 0;
        }

        int score = 60; // Base score for exact brand token match

        // Check full normalized search string inclusion
        if (normCard.contains(normSearch)) {
            score += 25;
        }

        // Check strength token match if present
        if (!strengthToken.isBlank()) {
            String cardStrength = extractStrengthToken(normCard);
            if (!cardStrength.isBlank() && !cardStrength.equals(strengthToken)) {
                // Reject candidate card if it has an explicit conflicting strength (e.g. 1000 vs 650)
                return 0;
            }
            Pattern strengthPattern = Pattern.compile("\\b" + Pattern.quote(strengthToken) + "(?:mg)?\\b");
            if (strengthPattern.matcher(normCard).find()) {
                score += 15;
            }
        }

        return score;
    }

    /**
     * Generic Card or URL scraper with robust selector waiting, scrolling, and candidate matching.
     */
    public BigDecimal scrapeCardOrUrl(Page page, String website, String url, String medicineName, List<String> selectors) {
        try {
            log.info("[{}] Navigating to search URL: {}", website, url);
            try {
                page.navigate(url, new Page.NavigateOptions().setTimeout(25000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            } catch (Exception e) {
                log.debug("[{}] Navigation wait completed or timed out: {}", website, e.getMessage());
            }

            // Wait for dynamic card selectors or product grid elements
            for (String selector : selectors) {
                try {
                    page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(6000));
                    break;
                } catch (Exception ignored) {}
            }

            // Controlled short scroll to trigger lazy loading
            try {
                page.evaluate("window.scrollBy(0, 400)");
            } catch (Exception ignored) {}

            log.info("[{}] Page loaded. Title: '{}'", website, page.title());

            BigDecimal cardPrice = extractPriceFromMatchingCard(page, website, medicineName);
            if (cardPrice != null) {
                return cardPrice;
            }

            if (isSearchPage(page.url())) {
                log.info("[{}] No matching product card on search results page for '{}'. Skipping fallback selectors.", website, medicineName);
                return null;
            }

            if (!pageMatchesMedicine(page, medicineName)) {
                log.info("[{}] Product page detail does not match brand token for '{}'. Skipping fallback selectors.", website, medicineName);
                return null;
            }

            BigDecimal selectorPrice = checkSelectorsNow(page, website, selectors);
            if (selectorPrice != null) {
                return selectorPrice;
            }

            log.info("[{}] No matching product candidate found for '{}'", website, medicineName);
            return null;

        } catch (Exception e) {
            log.warn("[{}] URL scrape exception: {}", website, e.getMessage());
            return null;
        }
    }

    /**
     * Iterates candidate cards on page, scores candidate title matching, and extracts price from best card.
     */
    public BigDecimal extractPriceFromMatchingCard(Page page, String website, String medicineName) {
        try {
            Locator cards = page.locator(
                    "div[class*='ProductCard'], div[class*='productCard'], div[class*='product-card'], div[class*='product_card'], " +
                    "div[class*='product-box'], div[class*='productBox'], div[class*='ProductBox'], " +
                    "div[class*='style__product'], div[class*='style__card'], div[class*='SearchProductCard'], " +
                    "div[class*='product-item'], div[class*='productItem'], div[class*='ProductItem'], " +
                    "a[href*='/otc/'], a[href*='/medicine/'], a[href*='/drugs/'], a[href*='/products/'], a[href*='/product/']"
            );
            int count = cards.count();
            log.info("[{}] Found {} product card candidate element(s) on page", website, count);

            int bestScore = 0;
            BigDecimal bestPrice = null;
            String matchedTitle = null;

            for (int i = 0; i < Math.min(count, 35); i++) {
                try {
                    Locator card = cards.nth(i);
                    String cardText = card.innerText();
                    if (cardText == null || cardText.isBlank()) continue;

                    // Extract the first few lines to determine the candidate title
                    String[] lines = cardText.split("\n");
                    String candidateTitle = "";
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            // Skip common badges/meta-text that might appear before title
                            if (trimmed.equalsIgnoreCase("prescription required") || 
                                trimmed.equalsIgnoreCase("rx") || 
                                trimmed.toLowerCase().contains("off") ||
                                trimmed.toLowerCase().contains("save")) {
                                continue;
                            }
                            candidateTitle = trimmed;
                            break;
                        }
                    }
                    if (candidateTitle.isEmpty() && lines.length > 0) {
                        candidateTitle = lines[0].trim();
                    }

                    int score = matchBrandAndScore(candidateTitle, medicineName);
                    if (score > bestScore && score >= 60) {
                        BigDecimal p = extractSellingPriceFromCard(card, website);
                        if (p != null) {
                            bestScore = score;
                            bestPrice = p;
                            matchedTitle = candidateTitle;
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (bestPrice != null) {
                log.info("[{}] Matched requested medicine '{}' with candidate title '{}' (score {}) -> Price: ₹{}",
                        website, medicineName, matchedTitle, bestScore, bestPrice);
                return bestPrice;
            }

        } catch (Exception e) {
            log.debug("[{}] Card extraction error: {}", website, e.getMessage());
        }
        return null;
    }

    /**
     * Extracts selling price from product card locator.
     */
    public BigDecimal extractSellingPriceFromCard(Locator card, String website) {
        List<String> prioritySelectors = switch (website.toLowerCase()) {
            case "pharmeasy" -> List.of(
                    "[class*='ourPrice']",
                    "[class*='discountedPrice']",
                    "[class*='offerPrice']"
            );
            case "netmeds" -> List.of(
                    ".final-price",
                    ".our-price",
                    "[class*='final-price']",
                    ".price-box .price"
            );
            case "1mg" -> List.of(
                    "[class*='style__price-tag']",
                    "[class*='price-tag']",
                    "[class*='priceTag']",
                    "[class*='discounted-price']"
            );
            case "apollo" -> List.of(
                    "[class*='ProductCard_price']",
                    "[class*='productCard_price']",
                    "div[class*='Price']",
                    "span[class*='price']",
                    "p[class*='price']"
            );
            default -> List.of(
                    "[class*='ourPrice']",
                    "[class*='our-price']",
                    "[class*='final-price']",
                    "[class*='price-tag']"
            );
        };

        for (String selector : prioritySelectors) {
            try {
                Locator priceEl = card.locator(selector);
                int cnt = priceEl.count();
                if (cnt > 0) {
                    for (int j = 0; j < cnt; j++) {
                        String text = priceEl.nth(j).innerText();
                        BigDecimal val = parsePriceFromExactText(text);
                        if (val != null) {
                            return val;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Fallback: parse entire card text for selling price
        return parsePriceFromExactText(card.innerText());
    }

    public BigDecimal checkSelectorsNow(Page page, String website, List<String> selectors) {
        return selectors.stream()
                .map(selector -> {
                    try {
                        Locator locator = page.locator(selector);
                        int count = locator.count();
                        if (count > 0) {
                            for (int i = 0; i < Math.min(count, 10); i++) {
                                String text = locator.nth(i).innerText();
                                if (text != null && !text.isBlank()) {
                                    BigDecimal parsed = parsePriceFromExactText(text);
                                    if (parsed != null) {
                                        log.info("[{}] Selector '{}' matched price ₹{} from raw text: '{}'",
                                                website, selector, parsed, text.trim().replace("\n", " "));
                                        return parsed;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("[{}] Selector '{}' error: {}", website, selector, e.getMessage());
                    }
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Parses valid selling price from string. Handles formats like ₹32, ₹30.8, ₹1,234.50 while ignoring MRP labels if preceded by actual price.
     */
    public BigDecimal parsePriceFromExactText(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            // Find all currency matches in text
            Pattern pattern = Pattern.compile("(?:₹|Rs\\.?\\s*)\\s*([0-9]{1,4}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);

            List<BigDecimal> prices = new ArrayList<>();
            while (matcher.find()) {
                String group = matcher.group(1).replace(",", "");
                if (group != null && !group.isBlank()) {
                    try {
                        BigDecimal val = new BigDecimal(group);
                        if (val.compareTo(MIN_VALID_PRICE) >= 0 && val.compareTo(MAX_VALID_PRICE) <= 0) {
                            prices.add(val);
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (!prices.isEmpty()) {
                // Return the first valid currency match (standard selling price position)
                return prices.get(0);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public boolean isSearchPage(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("/search") ||
               lower.contains("search-medicines") ||
               lower.contains("products/?q=") ||
               lower.contains("products?q=") ||
               lower.contains("catalogsearch");
    }

    public boolean pageMatchesMedicine(Page page, String searchedMedicineName) {
        try {
            String brandToken = extractBrandToken(searchedMedicineName);
            if (brandToken.isBlank()) {
                return false;
            }
            String title = normalizeText(page.title());
            Pattern brandPattern = Pattern.compile("\\b" + Pattern.quote(brandToken) + "\\b");
            if (brandPattern.matcher(title).find()) {
                return true;
            }
            // Also check h1 element
            Locator h1s = page.locator("h1");
            int count = h1s.count();
            for (int i = 0; i < count; i++) {
                String h1Text = normalizeText(h1s.nth(i).innerText());
                if (brandPattern.matcher(h1Text).find()) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Error verifying page match for '{}': {}", searchedMedicineName, e.getMessage());
        }
        return false;
    }
}
