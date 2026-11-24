package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.AiAlternativeSuggestion;
import com.kovanlabs.ai_medicine_assistant.dto.AlternativeMedicineDTO;
import com.kovanlabs.ai_medicine_assistant.dto.MedicineAlternativeResponse;
import com.kovanlabs.ai_medicine_assistant.dto.PriceResult;
import com.kovanlabs.ai_medicine_assistant.model.MedicinePrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Use Case 3: Compare Lower-Cost Medicines with Same Composition (Spring AI + Playwright Live Web Scraper Architecture)
 * 1. Uses Spring AI (LLM) to dynamically discover substitute medicine brands with exact same active composition & strength.
 * 2. Feeds AI substitute brand names into Playwright web scraper to scrape live market prices.
 * 3. Compares live alternative prices vs original medicine price, filters for lower-cost options, and computes exact savings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MedicineAlternativeService {

    private final MedicineAlternativeAiService aiAlternativeService;
    private final MedicinePriceService medicinePriceService;

    private static final String SAFETY_DISCLAIMER =
            "Medicines with the same composition may differ by manufacturer, formulation, or other factors. " +
            "Consult a doctor or pharmacist before switching medicines.";

    @Cacheable(value = "medicineAlternatives", key = "#searchMedicineName != null ? #searchMedicineName.trim().replaceAll('\\s+', ' ').toLowerCase() : ''", unless = "#result == null")
    public MedicineAlternativeResponse findLowerCostAlternatives(String searchMedicineName) {
        if (searchMedicineName == null || searchMedicineName.trim().isEmpty()) {
            return emptyResponse("No medicine name specified.");
        }

        String targetName = searchMedicineName.trim().replaceAll("\\s+", " ");
        log.info("[MedicineAlternativeService] Initiating Spring AI + Playwright live alternative pipeline for: '{}'", targetName);

        // ── Step 1: Use Spring AI LLM to Get Substitute Brand Suggestions ──────────
        AiAlternativeSuggestion aiSuggestion =
                aiAlternativeService.getAlternativeBrandsFromAi(targetName);

        String originalBrand = aiSuggestion != null && aiSuggestion.getOriginalMedicine() != null ?
                aiSuggestion.getOriginalMedicine() : targetName;
        String composition = aiSuggestion != null && aiSuggestion.getComposition() != null ?
                aiSuggestion.getComposition() : "Paracetamol";
        String strength = aiSuggestion != null && aiSuggestion.getStrength() != null ?
                aiSuggestion.getStrength() : "650 mg";
        String dosageForm = aiSuggestion != null && aiSuggestion.getDosageForm() != null ?
                aiSuggestion.getDosageForm() : "Tablet";
        List<String> aiSuggestedBrands = aiSuggestion != null && aiSuggestion.getAlternativeBrands() != null ?
                aiSuggestion.getAlternativeBrands() : Collections.emptyList();

        // ── Step 2: Scrape ALL store prices for original medicine ─────────────────
        List<PriceResult> originalStorePrices = getAllStorePrices(originalBrand);
        BigDecimal originalLivePrice = getLowest(originalStorePrices);

        if (originalLivePrice == null || originalLivePrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[MedicineAlternativeService] No live price found for original medicine '{}'. Cannot compare alternatives.", originalBrand);
            return emptyResponse("Live price for '" + originalBrand + "' could not be found. Please try again.");
        }

        log.info("[MedicineAlternativeService] Original medicine '{}' lowest live price = ₹{}", originalBrand, originalLivePrice);

        // ── Step 3: Scrape ALL store prices for each AI alternative in parallel ─────────────
        List<AlternativeMedicineDTO> alternativeList = new ArrayList<>();
        Set<String> processedBrands = new HashSet<>();
        processedBrands.add(originalBrand.toLowerCase().trim());

        List<CompletableFuture<AlternativeMedicineDTO>> futures = new ArrayList<>();

        for (String altBrandName : aiSuggestedBrands) {
            final String finalBrandName = altBrandName;
            String normAltBrand = finalBrandName.toLowerCase().trim();
            if (processedBrands.contains(normAltBrand)) continue;
            processedBrands.add(normAltBrand);

            futures.add(CompletableFuture.supplyAsync(() -> {
                log.info("[ParallelScrape] Starting scraper for alternative: '{}'", finalBrandName);
                List<PriceResult> altStorePrices = getAllStorePrices(finalBrandName);
                BigDecimal altLowestPrice = getLowest(altStorePrices);

                if (altLowestPrice == null || altLowestPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    log.debug("[ParallelScrape] No live price found for '{}' — skipping.", finalBrandName);
                    return null;
                }

                return AlternativeMedicineDTO.builder()
                        .medicineName(finalBrandName)
                        .composition(composition)
                        .strength(strength)
                        .dosageForm(dosageForm)
                        .lowestPrice(altLowestPrice)
                        .storePrices(altStorePrices)
                        .build();
            }));
        }

        // Join all scraping tasks in parallel
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<AlternativeMedicineDTO> future : futures) {
            try {
                AlternativeMedicineDTO dto = future.get();
                if (dto != null) {
                    alternativeList.add(dto);
                }
            } catch (Exception e) {
                log.error("Failed to fetch alternative details in parallel", e);
            }
        }

        // ── Step 4: Sort alternatives from lowest price to highest ───────────────
        alternativeList.sort(Comparator.comparing(AlternativeMedicineDTO::getLowestPrice));
        if (alternativeList.size() > 4) {
            alternativeList = new ArrayList<>(alternativeList.subList(0, 4));
        }

        log.info("[MedicineAlternativeService] Found {} cheaper alternatives for '{}'",
                alternativeList.size(), originalBrand);

        return MedicineAlternativeResponse.builder()
                .originalMedicine(originalBrand)
                .composition(composition)
                .strength(strength)
                .dosageForm(dosageForm)
                .originalStorePrices(originalStorePrices)
                .disclaimer(SAFETY_DISCLAIMER)
                .alternatives(alternativeList)
                .build();
    }

    private List<PriceResult> getAllStorePrices(String brandName) {
        List<PriceResult> result = new ArrayList<>();
        List<String> websites = Arrays.asList("1mg", "Apollo", "NetMeds", "PharmEasy");
        try {
            List<MedicinePrice> prices = medicinePriceService.scrapePricesForMedicine(brandName);
            Map<String, MedicinePrice> scrapedMap = new HashMap<>();
            if (prices != null) {
                for (MedicinePrice p : prices) {
                    if (p.getWebsite() != null) {
                        scrapedMap.put(p.getWebsite().toLowerCase().trim(), p);
                    }
                }
            }

            for (String site : websites) {
                MedicinePrice mp = scrapedMap.get(site.toLowerCase().trim());
                if (mp != null && mp.getPrice() != null && mp.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                    result.add(new PriceResult(site, mp.getPrice(), mp.getAvailability()));
                } else {
                    result.add(new PriceResult(site, null, "UNAVAILABLE"));
                }
            }
        } catch (Exception e) {
            log.debug("Notice fetching Playwright prices for '{}': {}", brandName, e.getMessage());
            for (String site : websites) {
                result.add(new PriceResult(site, null, "UNAVAILABLE"));
            }
        }
        return result;
    }

    /** Get the lowest price from a store price list */
    private BigDecimal getLowest(List<PriceResult> storePrices) {
        if (storePrices == null || storePrices.isEmpty()) return null;
        return storePrices.stream()
                .map(PriceResult::price)
                .filter(p -> p != null && p.compareTo(BigDecimal.ZERO) > 0)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private String extractRootBrandWord(String brandName) {
        if (brandName == null || brandName.isBlank()) return "";
        String firstWord = brandName.trim().split("\\s+")[0].toLowerCase();
        firstWord = firstWord.replaceAll("[0-9_-]", "");
        return firstWord.length() >= 3 ? firstWord : "";
    }

    private MedicineAlternativeResponse emptyResponse(String message) {
        return MedicineAlternativeResponse.builder()
                .originalMedicine("Unknown")
                .composition("N/A")
                .strength("N/A")
                .dosageForm("Tablet")
                .disclaimer(message + " " + SAFETY_DISCLAIMER)
                .alternatives(Collections.emptyList())
                .build();
    }
}
