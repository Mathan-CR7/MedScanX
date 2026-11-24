package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.BrandCandidate;
import com.kovanlabs.ai_medicine_assistant.model.MedicineMaster;
import com.kovanlabs.ai_medicine_assistant.repository.MedicineMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedicineMatchingService {

    private final MedicineMasterRepository masterRepository;
    private final OCRNormalizationService normalizationService;
    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();
    private final LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();

    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");

    public record MatchResult(MedicineMaster masterRecord, double similarityScore, BrandCandidate matchedCandidate) {}

    /**
     * Scalable candidate matching using indexed database pre-filtering and hybrid JaroWinkler-Levenshtein similarity.
     */
    public List<MatchResult> findBestMatches(List<BrandCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<MatchResult> matches = new ArrayList<>();

        for (BrandCandidate candidate : candidates) {
            String normCandidate = normalizationService.normalizeCompact(candidate.getNormalizedText());
            if (normCandidate == null || normCandidate.isBlank() || normCandidate.length() > 30) {
                continue;
            }

            // Extract prefix (first 3 letters) for indexed database pre-filtering
            String prefix = normCandidate.length() >= 3 ? normCandidate.substring(0, 3) : normCandidate;

            // Indexed pre-filter: fetch up to 100 relevant candidates matching prefix/term
            List<MedicineMaster> candidateMasters = masterRepository.findCandidatesByPrefixOrTerm(prefix, candidate.getNormalizedText(), normCandidate, PageRequest.of(0, 100));

            // If prefix/term query returns no candidates in DB, skip DB match so pipeline uses exact OCR brand candidate
            if (candidateMasters == null || candidateMasters.isEmpty()) {
                continue;
            }

            List<String> candidateDigits = extractDigits(normCandidate);

            for (MedicineMaster master : candidateMasters) {
                String normMasterBrand = normalizationService.normalizeCompact(master.getNormalizedBrandName());
                if (normMasterBrand == null || normMasterBrand.isBlank()) continue;

                // Compute hybrid string similarity (Jaro-Winkler + Relative Levenshtein)
                double jaroSim = jaroWinkler.apply(normCandidate, normMasterBrand);
                int levDist = levenshtein.apply(normCandidate, normMasterBrand);
                int maxLen = Math.max(normCandidate.length(), normMasterBrand.length());
                double levSim = maxLen == 0 ? 1.0 : 1.0 - ((double) levDist / maxLen);

                double similarity = (0.50 * jaroSim) + (0.50 * levSim);

                // Substring inclusion bonus applies ONLY between compact brand tokens with similar length (e.g. "PAND" vs "PAN-D")
                double lengthRatio = (double) Math.min(normCandidate.length(), normMasterBrand.length()) 
                        / Math.max(normCandidate.length(), normMasterBrand.length());
                if (lengthRatio >= 0.85 && (normCandidate.contains(normMasterBrand) || normMasterBrand.contains(normCandidate))) {
                    similarity = Math.max(similarity, 0.90);
                }

                // Strict numerical dosage/strength mismatch penalty (e.g., "Calpol 650" vs "Calpol 500")
                List<String> masterDigits = extractDigits(normMasterBrand);
                if (!candidateDigits.isEmpty() && !masterDigits.isEmpty() && !candidateDigits.equals(masterDigits)) {
                    log.debug("Penalizing digit mismatch between candidate '{}' ({}) and master brand '{}' ({})",
                            normCandidate, candidateDigits, normMasterBrand, masterDigits);
                    similarity = Math.max(0.0, similarity - 0.45);
                }

                // High threshold (0.80) to prevent distinct brands (e.g. Pantocid vs Pan-D) from false-matching
                if (similarity >= 0.80) {
                    matches.add(new MatchResult(master, similarity, candidate));
                }
            }
        }

        // Sort matches by descending similarity score
        matches.sort(Comparator.comparingDouble(MatchResult::similarityScore).reversed());
        return matches;
    }

    private List<String> extractDigits(String input) {
        if (input == null) return Collections.emptyList();
        Matcher matcher = DIGIT_PATTERN.matcher(input);
        List<String> digits = new ArrayList<>();
        while (matcher.find()) {
            digits.add(matcher.group());
        }
        return digits;
    }
}
