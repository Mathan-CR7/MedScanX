package com.kovanlabs.ai_medicine_assistant.service.scraper;

import com.microsoft.playwright.Page;
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
public class OneMgScraperService {

    private final ScraperHelper scraperHelper;

    public BigDecimal scrapePrice(Page page, String medicineName) {
        String encoded = URLEncoder.encode(medicineName, StandardCharsets.UTF_8).replace("+", "%20");
        return scraperHelper.scrapeCardOrUrl(page, "1mg",
                "https://www.1mg.com/search/all?name=" + encoded,
                medicineName,
                List.of(
                        "[class*='style__price-tag']",
                        "[class*='price-tag']",
                        "[class*='priceTag']",
                        "[class*='discounted-price']"
                )
        );
    }
}
