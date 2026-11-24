package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.*;
import com.kovanlabs.ai_medicine_assistant.model.MedicinePrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Enterprise Medicine Label Search Pipeline (Spring AI-First):
 *
 * Step 1 — PaddleOCR: Extract all raw text regions from the image.
 * Step 2 — Spring AI Reasoning (PRIMARY): Send ALL OCR regions to Spring AI ChatClient.
 *           Spring AI holistically reasons over the text fragments and identifies the brand name.
 * Step 3 — Live Playwright price scrape for the verified brand.
 *
 * Endpoints 2, 3, 4 (side effects / alternatives / food support) are SEPARATE — call them
 * individually using the medicine name returned from this pipeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MedicineSearchService {

    private final OcrService ocrService;
    private final MedicineLabelAiReasoningService aiReasoningService;
    private final BrandCandidateGenerator candidateGenerator;
    private final BrandCandidateScoringService candidateScoringService;
    private final MedicineMatchingService matchingService;
    private final MedicineVerificationService verificationService;
    private final MedicinePriceService medicinePriceService;
    private final MedicineSideEffectService sideEffectService;
    private final MedicineAlternativeService alternativeService;
    private final FoodSupportService foodSupportService;

    public MedicineSearchResponse searchMedicineByImage(byte[] imageBytes, String originalFilename) {
        log.info("[Pipeline] ══════════════════════════════════════════════════");
        log.info("[Pipeline] Starting Spring AI-First medicine label search for: {}", originalFilename);

        if (imageBytes == null || imageBytes.length == 0) {
            log.warn("[Pipeline] No image data provided. Returning UNVERIFIED.");
            return new MedicineSearchResponse("UNVERIFIED", Collections.emptyList(), null, null, null);
        }

        // ── STEP 1: PaddleOCR — Extract all raw text regions from the label ──────
        log.info("[Pipeline] Step 1: Running PaddleOCR on '{}'...", originalFilename);
        List<OCRTextRegion> regions = ocrService.extractTextRegions(imageBytes, originalFilename);

        if (regions == null || regions.isEmpty()) {
            log.warn("[Pipeline] Step 1: PaddleOCR found no readable text in '{}'. Returning UNVERIFIED.", originalFilename);
            return new MedicineSearchResponse("UNVERIFIED", Collections.emptyList(), null, null, null);
        }

        log.info("[Pipeline] Step 1: PaddleOCR extracted {} text region(s) from '{}'.", regions.size(), originalFilename);

        // ── STEP 2: Spring AI Reasoning — PRIMARY brand identification (NO fallback) ──
        log.info("[Pipeline] Step 2: Sending all OCR regions to Spring AI for brand name reasoning...");
        String verifiedBrandName = aiReasoningService.identifyBrandName(regions);

        if (verifiedBrandName == null || verifiedBrandName.isBlank()) {
            log.warn("[Pipeline] Step 2: Spring AI could not identify the brand from '{}'. Returning UNVERIFIED (no fallback).", originalFilename);
            return new MedicineSearchResponse("UNVERIFIED", Collections.emptyList(), null, null, null);
        }

        log.info("[Pipeline] Step 2: Spring AI identified brand name: '{}' from '{}'.", verifiedBrandName, originalFilename);

        // ── STEP 3: Live Playwright Price Scrape ───────────────────────────────────
        log.info("[Pipeline] Step 3: Running live Playwright price scrape for '{}'...", verifiedBrandName);
        List<MedicinePrice> prices = medicinePriceService.scrapePricesForMedicine(verifiedBrandName);
        List<PriceResult> priceResults = prices.stream()
                .map(p -> new PriceResult(p.getWebsite(), p.getPrice(), p.getAvailability()))
                .toList();

        log.info("[Pipeline] ✅ Step 3 complete for '{}'. Brand: '{}', Prices: {} stores scraped.",
                originalFilename, verifiedBrandName, priceResults.size());
        log.info("[Pipeline] Use GET /api/medicines/{}/side-effects for side effects.", verifiedBrandName);
        log.info("[Pipeline] Use GET /api/medicines/{}/alternatives for lower-cost alternatives.", verifiedBrandName);
        log.info("[Pipeline] Use GET /api/medicines/{}/food-support for food equivalents.", verifiedBrandName);
        log.info("[Pipeline] ══════════════════════════════════════════════════");

        // Return only medicineName + prices.
        // Endpoints 2, 3, 4 are separate — call them with the returned medicineName.
        return new MedicineSearchResponse(verifiedBrandName, priceResults);
    }

    /**
     * Identifies medicine details from an image using Spring AI as the primary reasoner.
     * Also uses Spring AI → no fallback to old DB fuzzy matching pipeline.
     */
    public MedicineIdentificationResponse identifyMedicineDetails(byte[] imageBytes, String originalFilename) {
        if (imageBytes == null || imageBytes.length == 0) {
            return MedicineIdentificationResponse.builder()
                    .status(MedicineIdentificationStatus.NOT_FOUND)
                    .brandName("UNVERIFIED")
                    .confidence(0.0)
                    .message("No image data provided.")
                    .alternatives(Collections.emptyList())
                    .build();
        }

        List<OCRTextRegion> regions = ocrService.extractTextRegions(imageBytes, originalFilename);

        if (regions == null || regions.isEmpty()) {
            return MedicineIdentificationResponse.builder()
                    .status(MedicineIdentificationStatus.NOT_FOUND)
                    .brandName("UNVERIFIED")
                    .confidence(0.0)
                    .message("No readable text detected in medicine image.")
                    .alternatives(Collections.emptyList())
                    .build();
        }

        // Spring AI as primary brand reasoning — no fallback
        String aiBrandName = aiReasoningService.identifyBrandName(regions);

        if (aiBrandName == null || aiBrandName.isBlank()) {
            // Still run old pipeline to surface OCR candidates for NEEDS_CONFIRMATION
            List<BrandCandidate> candidates = candidateGenerator.generateCandidates(regions);
            List<BrandCandidate> rankedCandidates = candidateScoringService.rankCandidates(candidates, regions);
            List<MedicineMatchingService.MatchResult> matches = matchingService.findBestMatches(rankedCandidates);
            return verificationService.verifyAndEvaluate(matches, regions);
        }

        return MedicineIdentificationResponse.builder()
                .status(MedicineIdentificationStatus.VERIFIED)
                .brandName(aiBrandName)
                .confidence(0.95)
                .ocrCandidate(regions.stream().map(OCRTextRegion::getText).findFirst().orElse(""))
                .message("Medicine brand identified by Spring AI reasoning over OCR text.")
                .alternatives(Collections.emptyList())
                .build();
    }

    public MedicineSearchResponse confirmUserBrandSelection(String userConfirmedBrandName) {
        if (userConfirmedBrandName == null || userConfirmedBrandName.trim().isEmpty()) {
            return new MedicineSearchResponse("UNVERIFIED", Collections.emptyList(), null, null, null);
        }
        String cleanBrand = candidateGenerator.cleanBrandName(userConfirmedBrandName);
        log.info("[Pipeline] User confirmed medicine selection: '{}'. Running full downstream pipeline...", cleanBrand);

        List<MedicinePrice> prices = medicinePriceService.scrapePricesForMedicine(cleanBrand);
        List<PriceResult> priceResults = prices.stream()
                .map(p -> new PriceResult(p.getWebsite(), p.getPrice(), p.getAvailability()))
                .toList();

        MedicineSideEffectResponse sideEffects = null;
        try {
            sideEffects = sideEffectService.getSideEffectsAndSafetyInfo(
                    MedicineSideEffectRequest.builder().brandName(cleanBrand).build()
            );
        } catch (Exception e) {
            log.warn("[Pipeline] Notice attaching side effects to confirmation response: {}", e.getMessage());
        }

        MedicineAlternativeResponse alternatives = null;
        try {
            alternatives = alternativeService.findLowerCostAlternatives(cleanBrand);
        } catch (Exception e) {
            log.warn("[Pipeline] Notice attaching alternatives to confirmation response: {}", e.getMessage());
        }

        FoodSupportResponse foodSupport = null;
        try {
            foodSupport = foodSupportService.getFoodSupport(cleanBrand);
        } catch (Exception e) {
            log.warn("[Pipeline] Notice attaching food support to confirmation response: {}", e.getMessage());
        }

        return new MedicineSearchResponse(cleanBrand, priceResults, sideEffects, alternatives, foodSupport);
    }
}
