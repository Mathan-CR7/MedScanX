package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.*;
import com.kovanlabs.ai_medicine_assistant.model.MedicineMaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedicineVerificationService {

    private final OCRNormalizationService normalizationService;
    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();

    @Value("${medicine.verification.verified-threshold:0.75}")
    private double verifiedThreshold;

    @Value("${medicine.verification.review-threshold:0.50}")
    private double reviewThreshold;

    @Value("${medicine.verification.brand-weight:0.40}")
    private double brandWeight;

    @Value("${medicine.verification.composition-weight:0.30}")
    private double compositionWeight;

    @Value("${medicine.verification.manufacturer-weight:0.15}")
    private double manufacturerWeight;

    @Value("${medicine.verification.strength-weight:0.15}")
    private double strengthWeight;

    public MedicineIdentificationResponse verifyAndEvaluate(List<MedicineMatchingService.MatchResult> matches, List<OCRTextRegion> allRegions) {
        if (matches == null || matches.isEmpty()) {
            log.info("No database matches found above threshold. Returning UNVERIFIED.");
            return MedicineIdentificationResponse.builder()
                    .status(MedicineIdentificationStatus.NOT_FOUND)
                    .brandName("UNVERIFIED")
                    .confidence(0.0)
                    .message("No reliable medicine database record was found matching the image.")
                    .alternatives(Collections.emptyList())
                    .build();
        }

        // Build entire concatenated raw OCR text for cross-validation field matching
        String fullRawText = allRegions.stream()
                .map(OCRTextRegion::getText)
                .collect(Collectors.joining(" "));
        String normalizedFullText = normalizationService.normalize(fullRawText);

        MedicineMatchingService.MatchResult bestMatch = matches.get(0);
        MedicineMaster master = bestMatch.masterRecord();
        BrandCandidate candidate = bestMatch.matchedCandidate();

        // 1. Brand Similarity
        double brandSim = bestMatch.similarityScore();

        // 2. Composition Match
        double compositionScore = evaluateFieldMatch(master.getNormalizedGenericName(), normalizedFullText);

        // 3. Manufacturer Match
        double manufacturerScore = evaluateFieldMatch(master.getNormalizedManufacturer(), normalizedFullText);

        // 4. Strength Match
        double strengthScore = evaluateStrengthMatch(master.getStrength(), normalizedFullText);

        // Final Composite Weighted Confidence
        double totalConfidence = (brandWeight * brandSim)
                + (compositionWeight * compositionScore)
                + (manufacturerWeight * manufacturerScore)
                + (strengthWeight * strengthScore);

        VerificationEvidence evidence = VerificationEvidence.builder()
                .brandSimilarity(brandSim)
                .ocrConfidence(candidate.getOcrConfidence())
                .compositionMatch(compositionScore)
                .manufacturerMatch(manufacturerScore)
                .strengthMatch(strengthScore)
                .build();

        List<String> alternativeBrands = matches.stream()
                .map(m -> m.masterRecord().getBrandName())
                .distinct()
                .collect(Collectors.toList());

        log.info("Multi-factor cross-validation complete for '{}': BrandSim={}, Comp={}, Mfg={}, Strength={} => TotalConf={}",
                master.getBrandName(), brandSim, compositionScore, manufacturerScore, strengthScore, totalConfidence);

        if (brandSim >= 0.80 || totalConfidence >= verifiedThreshold) {
            return MedicineIdentificationResponse.builder()
                    .status(MedicineIdentificationStatus.VERIFIED)
                    .brandName(master.getBrandName())
                    .confidence(Math.max(brandSim, totalConfidence))
                    .ocrCandidate(candidate.getOriginalText())
                    .matchedMedicineId(master.getId())
                    .evidence(evidence)
                    .alternatives(alternativeBrands)
                    .message("Medicine brand successfully verified.")
                    .build();
        } else if (totalConfidence >= reviewThreshold) {
            return MedicineIdentificationResponse.builder()
                    .status(MedicineIdentificationStatus.NEEDS_CONFIRMATION)
                    .brandName(master.getBrandName())
                    .confidence(totalConfidence)
                    .ocrCandidate(candidate.getOriginalText())
                    .matchedMedicineId(master.getId())
                    .evidence(evidence)
                    .alternatives(alternativeBrands)
                    .message("Multiple plausible medicine candidates detected. Please confirm the correct medicine.")
                    .build();
        } else {
            return MedicineIdentificationResponse.builder()
                    .status(MedicineIdentificationStatus.NOT_FOUND)
                    .brandName("UNVERIFIED")
                    .confidence(totalConfidence)
                    .ocrCandidate(candidate.getOriginalText())
                    .evidence(evidence)
                    .alternatives(alternativeBrands)
                    .message("Medicine details could not be verified with sufficient confidence.")
                    .build();
        }
    }

    private double evaluateFieldMatch(String masterFieldNormalized, String fullOCRTextNormalized) {
        if (masterFieldNormalized == null || masterFieldNormalized.trim().isEmpty()) {
            return 0.5; // neutral score if db field missing
        }
        String[] tokens = masterFieldNormalized.split("\\s+");
        int matchedTokens = 0;
        for (String token : tokens) {
            if (token.length() >= 3 && fullOCRTextNormalized.contains(token)) {
                matchedTokens++;
            }
        }
        return tokens.length == 0 ? 0.0 : (double) matchedTokens / tokens.length;
    }

    private double evaluateStrengthMatch(String masterStrength, String fullOCRTextNormalized) {
        if (masterStrength == null || masterStrength.trim().isEmpty()) {
            return 0.5;
        }
        String normStrength = normalizationService.normalize(masterStrength);
        return fullOCRTextNormalized.contains(normStrength) ? 1.0 : 0.0;
    }
}
