package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.BrandIdentificationResult;
import com.kovanlabs.ai_medicine_assistant.dto.OCRTextRegion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring AI Post-OCR Brand Name Reasoning Service.
 *
 * Receives ALL raw OCR text regions extracted by PaddleOCR from a medicine label image.
 * Sends the complete set of extracted text fragments to Spring AI ChatClient.
 * Spring AI reasons holistically over the fragments and returns the most likely correct Indian brand medicine name.
 *
 * This service is the PRIMARY brand identification mechanism — replacing the old BrandCandidateGenerator → DB fuzzy matching chain.
 * There is NO fallback to the old pipeline. If Spring AI cannot identify the brand with sufficient confidence, returns null → UNVERIFIED.
 *
 * Example reasoning:
 *   OCR extracts: ["CA LP OL", "500", "Paracetamol", "GSK"]
 *   Spring AI identifies: brandName="Calpol 500", confidence=0.96, reasoning="CALPOL + 500mg + Paracetamol by GSK = Calpol 500"
 */
@Service
@Slf4j
public class MedicineLabelAiReasoningService {

    /** Minimum AI confidence threshold. Results below this are treated as unidentifiable. */
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.40;

    private final ChatClient chatClient;

    @Autowired
    public MedicineLabelAiReasoningService(@Autowired(required = false) ChatClient chatClient) {
        this.chatClient = chatClient;
        if (chatClient == null) {
            log.warn("[MedicineLabelAiReasoningService] Spring AI ChatClient is NOT configured (no API key). Medicine label identification will return UNVERIFIED until Spring AI is configured.");
        } else {
            log.info("[MedicineLabelAiReasoningService] Spring AI ChatClient configured and ready for brand name reasoning.");
        }
    }

    /**
     * Sends all PaddleOCR-extracted text regions to Spring AI for brand name reasoning.
     *
     * @param ocrRegions List of raw OCR text regions from PaddleOCR
     * @return Identified brand name (e.g. "Calpol 500", "Dolo 650") or null if unidentifiable
     */
    public String identifyBrandName(List<OCRTextRegion> ocrRegions) {
        if (ocrRegions == null || ocrRegions.isEmpty()) {
            log.warn("[MedicineLabelAiReasoningService] No OCR text regions provided.");
            return null;
        }

        if (chatClient == null) {
            log.warn("[MedicineLabelAiReasoningService] ChatClient unavailable. Returning UNVERIFIED (no fallback).");
            return null;
        }

        // Collect all extracted text, preserving each region on its own line for AI context
        String allOcrText = ocrRegions.stream()
                .map(OCRTextRegion::getText)
                .filter(t -> t != null && !t.trim().isEmpty())
                .collect(Collectors.joining("\n"));

        if (allOcrText.isBlank()) {
            log.warn("[MedicineLabelAiReasoningService] All OCR regions are empty after filtering.");
            return null;
        }

        log.info("[MedicineLabelAiReasoningService] Sending {} OCR region(s) to Spring AI for brand name reasoning:\n{}",
                ocrRegions.size(), allOcrText);

        String systemPrompt = """
                You are an expert pharmaceutical brand identification AI specializing in Indian prescription and OTC medicines.
                
                Your ONLY task is to analyze raw text fragments extracted by OCR from a medicine label image,
                and determine the correct brand medicine name as it would appear on the medicine box.
                
                STRICT RULES:
                1. Return the EXACT brand name including strength number if it is part of the brand (e.g. "Calpol 500", "Dolo 650", "Pan 40", "Augmentin 625 Duo", "Telma 40").
                2. Do NOT return the generic/chemical composition name (e.g. NOT "Paracetamol", NOT "Amoxicillin + Clavulanic acid").
                3. OCR text may be garbled, partial, or rotated. Use pharmaceutical knowledge to reconstruct the most likely brand name.
                4. Use ALL available fragments as clues: brand fragments + generic name + strength + manufacturer together tell the story.
                5. If the brand name CANNOT be determined with any reasonable certainty, set brandName to null and confidence to 0.0.
                6. Set confidence between 0.0 (completely unsure) and 1.0 (absolutely certain).
                7. Confidence >= 0.40 means you are making a meaningful identification.
                8. Common Indian medicine brand families to consider: Calpol, Dolo, Crocin, Pacimol, Pan, Pantocid, Razo, Omez, Augmentin, Moxikind, Clavam, Taxim, Azee, Azithral, Telma, Amlokind, Glycomet, Dolo, Combiflam, Meftal, Benadryl, Ascoril, Allegra, Cetzine.
                """;

        String userPrompt = String.format("""
                Below is raw OCR text extracted from a medicine label image. Some text may be garbled, partial, rotated, or fragmented:
                
                --- RAW OCR TEXT FROM LABEL ---
                %s
                --- END RAW OCR TEXT ---
                
                Based on these fragments, identify the most likely Indian medicine brand name.
                
                Return ONLY valid JSON using snake_case keys in this exact structure:
                {
                  "brand_name": "Calpol 500",
                  "strength": "500mg",
                  "composition": "Paracetamol",
                  "confidence": 0.95,
                  "reasoning": "OCR fragments 'CALP', '500', 'Paracetamol', 'GSK' together strongly indicate Calpol 500 by GlaxoSmithKline."
                }
                """, allOcrText);

        try {
            BrandIdentificationResult result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .entity(BrandIdentificationResult.class);

            if (result == null) {
                log.warn("[MedicineLabelAiReasoningService] Spring AI returned null result.");
                return null;
            }

            String identifiedBrand = result.getBrandName();

            if (identifiedBrand == null || identifiedBrand.isBlank()
                    || identifiedBrand.equalsIgnoreCase("null")
                    || identifiedBrand.equalsIgnoreCase("unknown")
                    || identifiedBrand.equalsIgnoreCase("unidentified")) {
                log.warn("[MedicineLabelAiReasoningService] Spring AI could not identify a brand. Confidence: {}. Reasoning: {}",
                        result.getConfidence(), result.getReasoning());
                return null;
            }

            if (result.getConfidence() < MIN_CONFIDENCE_THRESHOLD) {
                log.warn("[MedicineLabelAiReasoningService] Spring AI identified '{}' but confidence {} is below threshold {}. Returning UNVERIFIED.",
                        identifiedBrand, result.getConfidence(), MIN_CONFIDENCE_THRESHOLD);
                return null;
            }

            log.info("[MedicineLabelAiReasoningService] ✅ Spring AI identified brand: '{}' | Strength: '{}' | Composition: '{}' | Confidence: {} | Reasoning: {}",
                    identifiedBrand, result.getStrength(), result.getComposition(),
                    result.getConfidence(), result.getReasoning());

            return identifiedBrand.trim();

        } catch (Exception e) {
            log.error("[MedicineLabelAiReasoningService] Spring AI reasoning call failed: {}", e.getMessage());
            return null;
        }
    }
}
