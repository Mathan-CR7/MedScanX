package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.MedicineSideEffectRequest;
import com.kovanlabs.ai_medicine_assistant.dto.MedicineSideEffectResponse;
import com.kovanlabs.ai_medicine_assistant.model.MedicineMaster;
import com.kovanlabs.ai_medicine_assistant.repository.MedicineMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Use Case 2: AI-Based Possible Side Effects Suggestion Service.
 * Formats all side effects, warning signs, and precautions in simple, clear, user-understandable language.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MedicineSideEffectService {

    private final ChatClient chatClient;
    private final MedicineMasterRepository masterRepository;
    private final OCRNormalizationService normalizationService;

    private static final String DEFAULT_DISCLAIMER =
            "Educational safety information only. This does not replace medical advice or diagnosis from a qualified doctor or pharmacist.";

    @Cacheable(value = "medicineSideEffects", key = "#request != null && #request.brandName != null ? #request.brandName.trim().toLowerCase() : ''", unless = "#result == null")
    public MedicineSideEffectResponse getSideEffectsAndSafetyInfo(MedicineSideEffectRequest request) {
        if (request == null) {
            return unverifiedResponse("No medicine details provided.");
        }

        String searchBrand = request.getBrandName() != null ? request.getBrandName().trim() : "";
        String searchGeneric = request.getGenericName() != null ? request.getGenericName().trim() : "";
        String searchStrength = request.getStrength() != null ? request.getStrength().trim() : "";
        String searchMfg = request.getManufacturer() != null ? request.getManufacturer().trim() : "";

        log.info("[MedicineSideEffectService] Processing user-understandable side effect request for brand: '{}', generic: '{}'",
                searchBrand, searchGeneric);

        // ── 1. Retrieve Trusted Database Context ──────────────────────────────────
        Optional<MedicineMaster> masterOpt = Optional.empty();

        if (request.getMedicineId() != null) {
            masterOpt = masterRepository.findById(request.getMedicineId());
        }

        if (masterOpt.isEmpty() && !searchBrand.isBlank()) {
            String normBrand = normalizationService.normalizeCompact(searchBrand);
            String prefix = normBrand.length() >= 3 ? normBrand.substring(0, 3) : normBrand;
            List<MedicineMaster> candidates = masterRepository.findCandidatesByPrefixOrTerm(
                    prefix, searchBrand, normBrand, PageRequest.of(0, 5));
            if (!candidates.isEmpty()) {
                masterOpt = Optional.of(candidates.get(0));
            }
        }

        // If medicine cannot be determined or verified, return friendly unverified notice
        if (masterOpt.isEmpty() && searchBrand.isBlank() && searchGeneric.isBlank()) {
            return unverifiedResponse("Reliable side-effect information could not be verified for uncataloged medicine.");
        }

        // Extract canonical values
        String brandName = masterOpt.map(MedicineMaster::getBrandName).orElse(searchBrand.isBlank() ? "Unknown Medicine" : searchBrand);
        String genericName = masterOpt.map(MedicineMaster::getGenericName).orElse(searchGeneric.isBlank() ? "Unspecified Composition" : searchGeneric);
        String strength = masterOpt.map(MedicineMaster::getStrength).orElse(searchStrength.isBlank() ? "" : searchStrength);
        String manufacturer = masterOpt.map(MedicineMaster::getManufacturer).orElse(searchMfg);

        // ── 2. Construct User-Understandable Spring AI Prompt ───────────────────────
        String systemPrompt = """
                You are a friendly, patient-focused medicine safety assistant for Indian patients.

                RULES:
                - Write EVERYTHING in simple, plain everyday English that any patient (non-doctor) can easily understand.
                - Avoid all complex medical jargon. Replace it with simple words.
                  Example: Instead of "hepatotoxicity" say "liver damage".
                - Do NOT diagnose, prescribe, or recommend changing dosage.
                - Do NOT use any emoji, symbols, or special characters in any field. Plain text only.
                - Always populate ALL fields — never leave a list empty. Minimum 3 items per list.
                - If the active ingredient (generic name) is 'Unspecified Composition' or unknown, please identify the actual generic active composition for the brand name (e.g. for Calpol 500, it is Paracetamol) and populate the generic_name field with it, then fill all other fields based on that composition.
                - Return ONLY valid JSON using snake_case keys. No markdown, no explanation.
                """;

        String userPrompt = String.format("""
                Medicine: %s
                Active Ingredient (Generic Name): %s
                Strength: %s
                Manufacturer: %s

                Provide complete, user-friendly medicine safety information.

                Return ONLY valid JSON in this EXACT snake_case structure (fill ALL fields):
                {
                  "medicine_name": "Calpol 500",
                  "generic_name": "Paracetamol",
                  "strength": "500 mg",
                  "manufacturer": "GlaxoSmithKline Pharmaceuticals Ltd",
                  "what_it_does": "Relieves mild to moderate pain (headache, toothache, body ache) and brings down fever.",
                  "avoid_if": "You are allergic to Paracetamol. You have severe liver disease. You already take other medicines containing Paracetamol.",
                  "dosage_guidelines": "Typical adult dosage is 1 to 2 tablets every 4 to 6 hours as needed for pain or fever. Leave at least 4 hours between doses. Do not exceed 8 tablets (4000 mg) in a 24-hour period.",
                  "storage_instructions": "Store below 30°C in a dry place. Protect from direct heat, sunlight, and moisture. Keep safely out of reach of children.",
                  "common_side_effects": [
                    "Feeling sick or queasy (nausea) — usually mild and goes away on its own",
                    "Mild stomach discomfort or upset stomach",
                    "Feeling slightly tired or drowsy after taking the tablet"
                  ],
                  "less_common_side_effects": [
                    "Skin rash or mild itching",
                    "Slight dizziness, especially if taken on an empty stomach",
                    "Mild headache that improves after a short while"
                  ],
                  "serious_warnings": [
                    "Go to emergency immediately if your skin or eyes turn yellow (jaundice) — this can mean liver damage",
                    "Seek urgent help if you get a severe skin rash, blistering, or your face/lips/throat swells up (signs of a serious allergic reaction)",
                    "Call Poison Control immediately if you or anyone accidentally takes too many tablets — even if you feel fine, overdose can silently damage the liver",
                    "Stop the medicine and see your doctor if you notice unusual bruising or bleeding, or feel very tired and weak"
                  ],
                  "disclaimer": "Educational safety information only. This does not replace medical advice or diagnosis from a qualified doctor or pharmacist."
                }
                """, brandName, genericName, strength, manufacturer);


        // ── 3. Execute Spring AI ChatClient Direct Execution ──────────────────────
        if (chatClient != null) {
            try {
                var chatSpec = chatClient.prompt().system(systemPrompt).user(userPrompt);
                if (request.getModelName() != null && !request.getModelName().isBlank()) {
                    chatSpec.model(request.getModelName());
                }
                MedicineSideEffectResponse response = chatSpec.entity(MedicineSideEffectResponse.class);

                if (response != null && response.getCommonSideEffects() != null && !response.getCommonSideEffects().isEmpty()) {
                    // Override with canonical values from DB for accuracy
                    response.setMedicineName(brandName);
                    response.setGenericName(genericName);
                    response.setStrength(strength);
                    if (manufacturer != null && !manufacturer.isBlank()) {
                        response.setManufacturer(manufacturer);
                    }
                    response.setDisclaimer(DEFAULT_DISCLAIMER);
                    return response;
                }
            } catch (Exception e) {
                log.warn("[MedicineSideEffectService] Spring AI execution notice: {}", e.getMessage());
            }
        }

        return unverifiedResponse("Side effect information could not be generated by Spring AI.");
    }

    private MedicineSideEffectResponse unverifiedResponse(String message) {
        return MedicineSideEffectResponse.builder()
                .medicineName("Unverified Medicine")
                .genericName("")
                .strength("")
                .commonSideEffects(List.of())
                .lessCommonSideEffects(List.of())
                .seriousWarnings(List.of())
                .disclaimer(message)
                .build();
    }
}
