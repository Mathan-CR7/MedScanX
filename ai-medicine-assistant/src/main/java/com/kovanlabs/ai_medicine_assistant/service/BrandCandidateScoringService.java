package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.BrandCandidate;
import com.kovanlabs.ai_medicine_assistant.dto.OCRTextRegion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class BrandCandidateScoringService {

    private static final Pattern BRAND_CODE_SUFFIX = Pattern.compile("(?i).*\\b([A-Z0-9]{1,4}-[A-Z0-9]{1,4}|DM|D|PLUS|FORTE|SR|ER|XL|CR|LA|DS|650|500|40|20|10|5)\\b.*");

    public List<BrandCandidate> rankCandidates(List<BrandCandidate> candidates, List<OCRTextRegion> allRegions) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }

        double maxArea = allRegions.stream().mapToDouble(OCRTextRegion::getArea).max().orElse(1.0);
        double maxTextHeight = allRegions.stream().mapToDouble(OCRTextRegion::getHeight).max().orElse(1.0);

        for (BrandCandidate candidate : candidates) {
            OCRTextRegion region = candidate.getRegion();

            // 1. Visual Score (60% Area, 40% Font Height)
            double relativeArea = region.getArea() / Math.max(1.0, maxArea);
            double relativeHeight = region.getHeight() / Math.max(1.0, maxTextHeight);

            double visualScore = (0.60 * relativeArea) + (0.40 * relativeHeight);
            candidate.setVisualScore(visualScore);

            // 2. Semantic Score
            String text = candidate.getOriginalText();
            String norm = candidate.getNormalizedText();
            double semanticScore = 0.5;

            // Alphabetic ratio vs numbers
            long alphaCount = norm.chars().filter(Character::isLetter).count();
            double alphaRatio = norm.isEmpty() ? 0.0 : (double) alphaCount / norm.length();
            semanticScore += (alphaRatio * 0.2);

            // Brand feature boosts
            // CamelCase / TitleCase brand name boost (e.g. "ElectSita", "Calpol", "Pantocid")
            if (text.matches(".*[A-Z][a-z]+[A-Z].*") || text.matches("^[A-Z][a-zA-Z0-9\\-]{2,20}$")) {
                semanticScore += 0.35;
            }

            // Hyphenated or brand suffix code boost (e.g. "ElectSita-DM", "Pan-D", "Dolo-650")
            if (text.contains("-") || BRAND_CODE_SUFFIX.matcher(text).find()) {
                semanticScore += 0.35;
            }

            // Ideal brand length preference (3 to 18 chars)
            if (norm.length() >= 3 && norm.length() <= 18) {
                semanticScore += 0.15;
            }

            candidate.setSemanticScore(semanticScore);

            // Composite candidate score
            double composite = (0.35 * candidate.getOcrConfidence()) + (0.35 * visualScore) + (0.30 * semanticScore);
            candidate.setCandidateScore(composite);
        }

        // Sort candidates in descending order of composite candidateScore
        candidates.sort(Comparator.comparingDouble(BrandCandidate::getCandidateScore).reversed());
        return candidates;
    }
}
