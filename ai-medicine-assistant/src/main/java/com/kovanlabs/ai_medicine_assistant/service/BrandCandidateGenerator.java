package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.BrandCandidate;
import com.kovanlabs.ai_medicine_assistant.dto.OCRTextRegion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrandCandidateGenerator {

    private final OCRNormalizationService normalizationService;

    // Comprehensive exclusion pattern filtering regulatory, pricing, storage, composition, active pharmaceutical ingredients, and packaging noise
    private static final Pattern EXCLUSION_PATTERN = Pattern.compile(
            "(?i).*(mrp|mfg|exp|batch|lic|b\\.no|rs\\.|price|taxes|store|storage|temperature|room|protected|protect|moisture|light|swallow|chew|crush|keep out|children|physician|doctor|dosage|dose|composition|excipients|schedule|warning|caution|marketed|manufactured|road|street|nagar|pin|plot|limited|ltd|pvt|inc|corp|labs|pharma|tablets|capsules|syrup|injection|drops|uncoated|coated|film|extended|sustained|release|ip|bp|usp|0mg|mg|ml|mcg|gm|kg|iu|eq\\.|equivalent|colors|colours|ferric|oxide|titanium|dioxide|oral|suspension|ointment|gel|cream|solution|dispersible|chewable|sublingual|suppository|inhaler|external use|dry place|overdose|injurious|liver|allergic|reaction|customer care|toll free|bilayered|film-coated|filmcoated|strip|each|contains|prescription|danger|dapagliflozin|sitagliptin|metformin|paracetamol|acetaminophen|ibuprofen|aceclofenac|diclofenac|pantoprazole|rabeprazole|omeprazole|esomeprazole|ranitidine|famotidine|amoxicillin|azithromycin|ciprofloxacin|levofloxacin|cefixime|ceftriaxone|atorvastatin|rosuvastatin|amlodipine|telmisartan|losartan|metoprolol|atenolol|glimepiride|gliclazide|vildagliptin|teneligliptin|empagliflozin|canagliflozin|clopidogrel|aspirin|montelukast|levocetirizine|cetirizine|fexofenadine|chlorpheniramine|dextromethorphan|ambroxol|bromhexine|terbutaline|salbutamol|budesonide|formoterol|fluticasone|prednisolone|dexamethasone|methylprednisolone|deflazacort|ondansetron|domperidone|metoclopramide|dicyclomine|hyoscine|tramadol|tapentadol|gabapentin|pregabalin|duloxetine|sertraline|escitalopram|fluoxetine|alprazolam|clonazepam|diazepam|lorazepam|zolpidem).*"
    );

    public String cleanBrandName(String rawText) {
        if (rawText == null) return "";
        String text = rawText.trim();

        // 1. Remove non-ASCII trademark/registered/copyright symbols
        text = text.replaceAll("[\\u00AE\\u00A9\\u2122\\u00AE®©™]", " ");

        // 2. Remove non-alphanumeric punctuation except spaces and hyphens
        text = text.replaceAll("[^a-zA-Z0-9\\s\\-]", " ");

        // 3. Filter out random OCR noise tokens
        String[] tokens = text.trim().split("\\s+");
        List<String> validTokens = new ArrayList<>();
        for (String token : tokens) {
            if (isGibberishToken(token)) {
                log.debug("Stripping random OCR noise artifact: '{}'", token);
                continue;
            }
            validTokens.add(token);
        }
        text = String.join(" ", validTokens);

        // 4. Convert dot separators in brand strength numbers
        text = text.replaceAll("(?<=[a-zA-Z])\\.(?=\\d)", " ");

        // 5. Format spacing between letters and dosage strength digits if concatenated
        text = text.replaceAll("(?<=[a-zA-Z]{2,})(?=\\d)", " ");

        // 6. Dynamically strip short leading manufacturer logo acronyms or Rx symbols
        text = text.replaceAll("^(?i)(rx|r|cul|[A-Z]{2,4})\\s+(?=[A-Za-z]{2,})", "");

        // 7. Dynamically strip leading manufacturer prefix if candidate has format "MfgBrand BrandName Strength"
        String[] parts = text.split("\\s+");
        if (parts.length >= 3 && parts[parts.length - 1].matches("\\d+")) {
            if (parts[0].matches("[A-Z]{2,}") && !parts[0].contains("-")) {
                text = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            }
        }

        // 8. Capitalize brand words cleanly
        text = capitalizeBrandText(text);

        return text.trim();
    }

    public boolean isGibberishToken(String token) {
        if (token == null || token.isBlank()) return true;
        String text = token.trim();
        String lower = text.toLowerCase();

        // 1. No vowels or all digits (except brand numbers like 650)
        boolean noVowels = !lower.matches(".*[aeiouy].*");
        if (noVowels && !lower.matches("\\d+")) return true;

        // 2. Contains 4 or more consecutive non-vowel/consonant letters (e.g. "hctfs" in "Acihctfsy")
        if (lower.matches(".*[bcdfghjklmnpqrstvwxz]{4,}.*")) return true;

        // 3. Mixed internal casing (e.g. "aCiHcTfSy")
        boolean hasMixedInternalCasing = text.matches(".*[a-z]+[A-Z]+[a-z]+.*") || text.matches(".*[A-Z]+[a-z]+[A-Z]+.*");
        if (hasMixedInternalCasing && !text.contains("-")) return true;

        return false;
    }

    public boolean isExcludedText(String text) {
        if (text == null || text.isBlank()) return true;
        String lower = text.toLowerCase().trim();

        if (EXCLUSION_PATTERN.matcher(lower).find()) {
            return true;
        }

        String[] words = lower.split("\\s+");
        for (String word : words) {
            if (EXCLUSION_PATTERN.matcher(word).find()) {
                return true;
            }
            if (word.startsWith("compos") || word.startsWith("excip") || word.startsWith("table") || word.startsWith("capsu")) {
                return true;
            }
            if (isGibberishToken(word)) {
                return true;
            }
        }

        return false;
    }

    private String capitalizeBrandText(String text) {
        if (text == null || text.isBlank()) return "";
        String[] words = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (word.contains("-")) {
                String[] subParts = word.split("-");
                StringBuilder subSb = new StringBuilder();
                for (int i = 0; i < subParts.length; i++) {
                    if (!subParts[i].isEmpty()) {
                        subSb.append(Character.toUpperCase(subParts[i].charAt(0)))
                             .append(subParts[i].substring(1));
                    }
                    if (i < subParts.length - 1) subSb.append("-");
                }
                sb.append(subSb.toString()).append(" ");
            } else if (Character.isLowerCase(word.charAt(0))) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1)).append(" ");
            } else {
                sb.append(word).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public List<BrandCandidate> generateCandidates(List<OCRTextRegion> regions) {
        List<BrandCandidate> candidates = new ArrayList<>();
        if (regions == null || regions.isEmpty()) {
            return candidates;
        }

        for (OCRTextRegion region : regions) {
            String rawText = region.getText();
            if (rawText == null || rawText.trim().isEmpty()) {
                continue;
            }

            String trimmed = cleanBrandName(rawText);
            if (trimmed.isEmpty() || isExcludedText(trimmed)) {
                log.debug("Excluding OCR region as generic/regulatory noise: '{}'", rawText);
                continue;
            }

            String[] wordTokens = trimmed.split("\\s+");
            if (wordTokens.length > 3) {
                log.debug("Excluding OCR region with > 3 words as instructional/storage text: '{}'", trimmed);
                continue;
            }

            String normalized = normalizationService.normalize(trimmed);
            if (normalized.length() < 2 || normalized.length() > 30) {
                continue;
            }

            BrandCandidate candidate = BrandCandidate.builder()
                    .originalText(trimmed)
                    .normalizedText(normalized)
                    .ocrConfidence(region.getConfidence())
                    .region(region)
                    .build();

            candidates.add(candidate);
        }

        // Evaluate adjacent horizontal/vertical line pairs
        for (int i = 0; i < regions.size() - 1; i++) {
            OCRTextRegion r1 = regions.get(i);
            OCRTextRegion r2 = regions.get(i + 1);

            String combinedText = cleanBrandName(r1.getText() + " " + r2.getText());
            if (combinedText.length() <= 30 && !isExcludedText(combinedText)) {
                String[] wordTokens = combinedText.split("\\s+");
                if (wordTokens.length > 3) {
                    continue;
                }

                String normalizedCombined = normalizationService.normalize(combinedText);
                if (normalizedCombined.length() >= 3 && normalizedCombined.length() <= 30) {
                    double avgConf = (r1.getConfidence() + r2.getConfidence()) / 2.0;

                    double minX = Math.min(r1.getX(), r2.getX());
                    double minY = Math.min(r1.getY(), r2.getY());
                    double maxX = Math.max(r1.getX() + r1.getWidth(), r2.getX() + r2.getWidth());
                    double maxY = Math.max(r1.getY() + r1.getHeight(), r2.getY() + r2.getHeight());

                    OCRTextRegion combinedRegion = OCRTextRegion.builder()
                            .text(combinedText)
                            .confidence(avgConf)
                            .x(minX)
                            .y(minY)
                            .width(maxX - minX)
                            .height(maxY - minY)
                            .area((maxX - minX) * (maxY - minY))
                            .build();

                    candidates.add(BrandCandidate.builder()
                            .originalText(combinedText)
                            .normalizedText(normalizedCombined)
                            .ocrConfidence(avgConf)
                            .region(combinedRegion)
                            .build());
                }
            }
        }

        return candidates;
    }
}
