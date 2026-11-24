package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.FoodSuggestionDTO;
import com.kovanlabs.ai_medicine_assistant.dto.FoodSupportRequest;
import com.kovanlabs.ai_medicine_assistant.dto.FoodSupportResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Use Case 4: Dynamic Food-Based Support for Minor Problems Service
 * Guarantees high-quality food-based supportive recommendations for all medicines & symptoms.
 */
@Service
@Slf4j
public class FoodSupportService {

    private final ChatClient chatClient;

    private static final String DEFAULT_WARNING =
            "⚠️ These foods can help you feel better for a minor problem, but they are NOT a replacement for medicine. If your symptoms continue or get worse, please see a doctor.";

    @Autowired
    public FoodSupportService(@Autowired(required = false) ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public FoodSupportResponse getFoodSupport(String medicineOrSymptom) {
        return getFoodSupport(FoodSupportRequest.builder().medicineName(medicineOrSymptom).build());
    }

    public FoodSupportResponse getFoodSupport(String medicineOrSymptom, Boolean forceSymptom) {
        return getFoodSupport(FoodSupportRequest.builder()
                .medicineName(medicineOrSymptom)
                .forceSymptom(forceSymptom)
                .build());
    }

    @Cacheable(value = "medicineFoodSupport", key = "#request != null && #request.medicineName != null ? #request.medicineName.trim().toLowerCase() + '_' + (#request.forceSymptom != null ? #request.forceSymptom : 'false') : ''", unless = "#result == null")
    public FoodSupportResponse getFoodSupport(FoodSupportRequest request) {
        if (request == null || request.getMedicineName() == null || request.getMedicineName().trim().isEmpty()) {
            return FoodSupportResponse.builder()
                    .medicine("Unspecified")
                    .usedFor("General Care")
                    .foodSupportAvailable(false)
                    .foodSuggestions(java.util.Collections.emptyList())
                    .medicineEquivalent(false)
                    .warning(DEFAULT_WARNING)
                    .build();
        }

        String inputQuery = request.getMedicineName().trim();
        log.info("[FoodSupportService] Processing Use Case 4 Food Support request for query: '{}'", inputQuery);

        boolean isSymptom = request.getForceSymptom() != null ? request.getForceSymptom() : isSymptomQuery(inputQuery);
        boolean isChronic = isChronicConditionQuery(inputQuery);

        // ── Step 1: Try Spring AI ChatClient Engine ────────────────────────────────
        if (chatClient != null) {
            try {
                String systemPrompt = """
                        You are an expert clinical nutritionist and pharmacological safety assistant.
                        Analyze the input query (medicine name, minor symptom, or chronic condition) and provide evidence-based supportive food/drink recommendations.

                        CRITICAL SAFETY RULES:
                        1. Determine if the query is a MINOR SYMPTOM (e.g. fever, cold, headache, acidity) vs a SEVERE/CHRONIC CONDITION (e.g. Kidney Disease, Renal Failure, Cancer, Heart Failure, Liver Disease).
                        2. For SEVERE/CHRONIC CONDITIONS (like Kidney Disease): Set chronic_condition = true, used_for = 'Chronic Condition Support (Doctor Supervision Required)'.
                        3. For MINOR SYMPTOMS: Set chronic_condition = false, used_for = 'Minor Symptom Care'.
                        4. Provide 4 safe, evidence-based supportive food or drink options.
                        5. Write EVERYTHING in simple, plain, friendly everyday English.
                        6. Return ONLY valid JSON matching the exact structure.
                        """;

                String userPrompt = String.format("""
                        User Query: %s

                        Return ONLY valid JSON matching this exact structure:
                        {
                          "medicine": "%s",
                          "used_for": "%s",
                          "food_support_available": true,
                          "chronic_condition": %b,
                          "food_suggestions": [
                            {
                              "food": "Warm chicken soup",
                              "quantity": "1 bowl",
                              "helpful_part": "Easy to digest hydration",
                              "reason": "Helps keep you hydrated and provides comfort when you have a mild fever.",
                              "precaution": "Make sure the soup is not too hot before eating."
                            },
                            {
                              "food": "Coconut water",
                              "quantity": "1 glass",
                              "helpful_part": "Natural fluids",
                              "reason": "Helps your body stay hydrated when you feel warm or tired.",
                              "precaution": "Drink it fresh at room temperature."
                            },
                            {
                              "food": "Plain boiled rice",
                              "quantity": "1/2 cup",
                              "helpful_part": "Gentle energy",
                              "reason": "Gives you gentle energy without upsetting your stomach when you are unwell.",
                              "precaution": "Eat in small portions."
                            },
                            {
                              "food": "Warm ginger tea",
                              "quantity": "1 cup",
                              "helpful_part": "Soothing warmth",
                              "reason": "Helps you feel relaxed and warm when dealing with mild body aches or chills.",
                              "precaution": "Do not add too much sugar."
                            }
                          ],
                          "medicine_equivalent": false,
                          "warning": "%s"
                        }
                        """, inputQuery, isSymptom ? "" : inputQuery,
                        isChronic ? "Chronic Condition Support (Doctor Supervision Required)" : "Minor Symptom Care",
                        isChronic,
                        isChronic ? "⚠️ CRITICAL MEDICAL NOTICE: " + inputQuery + " is a serious, chronic medical condition requiring clinical management by a doctor/specialist. Supportive food recommendations are strictly complementary and MUST be supervised by your physician." : DEFAULT_WARNING);

                var chatSpec = chatClient.prompt().system(systemPrompt).user(userPrompt);
                if (request.getModelName() != null && !request.getModelName().isBlank()) {
                    chatSpec.model(request.getModelName());
                }

                FoodSupportResponse response = chatSpec.entity(FoodSupportResponse.class);

                if (response != null && response.getFoodSuggestions() != null && !response.getFoodSuggestions().isEmpty()) {
                    if (isSymptom) {
                        response.setMedicine(null);
                    }
                    response.setFoodSupportAvailable(true);
                    response.setMedicineEquivalent(false);
                    if (isChronic) {
                        response.setChronicCondition(true);
                        response.setUsedFor("Chronic Condition Support (Doctor Supervision Required)");
                        response.setWarning("⚠️ CRITICAL MEDICAL NOTICE: " + inputQuery + " is a serious, chronic medical condition requiring clinical management by a doctor/specialist. Supportive food recommendations are strictly complementary and MUST be supervised by your physician.");
                    } else {
                        response.setChronicCondition(false);
                        response.setWarning(DEFAULT_WARNING);
                    }
                    log.info("[FoodSupportService] Spring AI successfully generated {} food suggestions for '{}' (isChronic: {})",
                            response.getFoodSuggestions().size(), inputQuery, isChronic);
                    return response;
                }
            } catch (Exception e) {
                log.warn("[FoodSupportService] Spring AI call failed ({}), activating fallback food recommendations.", e.getMessage());
            }
        }

        // ── Step 2: Fallback Engine — Always guarantees 4 high quality food recommendations ──
        return buildFallbackResponse(inputQuery, isSymptom ? null : inputQuery);
    }

    private FoodSupportResponse buildFallbackResponse(String query, String medicine) {
        String usedFor = determineConditionName(query);
        List<FoodSuggestionDTO> suggestions = getDefaultFoodSuggestions(query);

        return FoodSupportResponse.builder()
                .medicine(medicine)
                .usedFor(usedFor)
                .foodSupportAvailable(true)
                .foodSuggestions(suggestions)
                .medicineEquivalent(false)
                .warning(DEFAULT_WARNING)
                .build();
    }

    private String determineConditionName(String query) {
        String lower = query != null ? query.toLowerCase().trim() : "";
        if (lower.contains("calpol") || lower.contains("dolo") || lower.contains("crocin") || lower.contains("paracetamol") || lower.contains("fever") || lower.contains("pain") || lower.contains("headache")) {
            return "Mild fever or body aches";
        } else if (lower.contains("cough") || lower.contains("cold") || lower.contains("throat") || lower.contains("benadryl") || lower.contains("alex")) {
            return "Mild cough or throat irritation";
        } else if (lower.contains("stomach") || lower.contains("acidity") || lower.contains("gelusil") || lower.contains("digene") || lower.contains("pantocid")) {
            return "Mild stomach discomfort or acidity";
        }
        return "General health & recovery support";
    }

    private List<FoodSuggestionDTO> getDefaultFoodSuggestions(String medicineOrSymptom) {
        String lower = medicineOrSymptom != null ? medicineOrSymptom.toLowerCase() : "";
        List<FoodSuggestionDTO> list = new ArrayList<>();

        if (lower.contains("calpol") || lower.contains("dolo") || lower.contains("crocin") || lower.contains("paracetamol") || lower.contains("fever") || lower.contains("pain") || lower.contains("headache")) {
            list.add(FoodSuggestionDTO.builder()
                    .food("Warm chicken soup")
                    .quantity("1 bowl")
                    .helpfulPart("Easy to digest hydration")
                    .reason("Helps keep you hydrated and provides comfort when you have a mild fever.")
                    .precaution("Make sure the soup is not too hot before eating.")
                    .build());
            list.add(FoodSuggestionDTO.builder()
                    .food("Coconut water")
                    .quantity("1 glass")
                    .helpfulPart("Natural fluids")
                    .reason("Helps your body stay hydrated when you feel warm or tired.")
                    .precaution("Drink it fresh at room temperature.")
                    .build());
            list.add(FoodSuggestionDTO.builder()
                    .food("Plain boiled rice")
                    .quantity("1/2 cup")
                    .helpfulPart("Gentle energy")
                    .reason("Gives you gentle energy without upsetting your stomach when you are unwell.")
                    .precaution("Eat in small portions.")
                    .build());
            list.add(FoodSuggestionDTO.builder()
                    .food("Warm ginger tea")
                    .quantity("1 cup")
                    .helpfulPart("Soothing warmth")
                    .reason("Helps you feel relaxed and warm when dealing with mild body aches or chills.")
                    .precaution("Do not add too much sugar.")
                    .build());
        } else if (lower.contains("cough") || lower.contains("cold") || lower.contains("throat") || lower.contains("benadryl") || lower.contains("ascoril") || lower.contains("alex")) {
            list.add(FoodSuggestionDTO.builder()
                    .food("Warm honey water")
                    .quantity("1-2 teaspoons")
                    .helpfulPart("Soothes and coats your throat")
                    .reason("Soothes irritated throat membranes and provides mild cough relief.")
                    .precaution("Do not give honey to infants under 1 year of age.")
                    .build());
            list.add(FoodSuggestionDTO.builder()
                    .food("Steaming vegetable soup")
                    .quantity("1 cup")
                    .helpfulPart("Warm vapor & fluids")
                    .reason("Warm steam helps soothe nasal passages while keeping you hydrated.")
                    .precaution("Sip slowly while warm.")
                    .build());
            list.add(FoodSuggestionDTO.builder()
                    .food("Warm turmeric milk")
                    .quantity("1 small glass")
                    .helpfulPart("Comforting warmth")
                    .reason("Provides comforting warmth for throat irritation before sleep.")
                    .precaution("Drink warm before sleeping.")
                    .build());
            list.add(FoodSuggestionDTO.builder()
                    .food("Warm ginger & tulsi tea")
                    .quantity("1 cup")
                    .helpfulPart("Aromatic herbal warmth")
                    .reason("Relieves throat tickle and keeps your airways comfortable.")
                    .precaution("Drink 1-2 times daily.")
                    .build());
        } else {
            list.add(FoodSuggestionDTO.builder()
                    .food("Warm chicken or veg broth")
                    .quantity("1 bowl")
                    .helpfulPart("Light hydration")
                    .reason("Provides light fluids and soothing warmth during recovery.")
                    .precaution("Serve warm, not hot.")
                    .build());
            list.add(FoodSuggestionDTO.builder()
                    .food("Coconut water")
                    .quantity("1 glass")
                    .helpfulPart("Natural fluids")
                    .reason("Keeps your body hydrated and refreshed.")
                    .precaution("Drink fresh.")
                    .build());
            list.add(FoodSuggestionDTO.builder()
                    .food("Plain khichdi or boiled rice")
                    .quantity("1/2 cup")
                    .helpfulPart("Easy to digest food")
                    .reason("Provides gentle energy without straining your digestive system.")
                    .precaution("Eat in small, light portions.")
                    .build());
            list.add(FoodSuggestionDTO.builder()
                    .food("Warm ginger tea")
                    .quantity("1 cup")
                    .helpfulPart("Soothing warmth")
                    .reason("Offers comforting warmth and helps relax your body.")
                    .precaution("Do not add excess sugar.")
                    .build());
        }
        return list;
    }

    private boolean isSymptomQuery(String input) {
        String lower = input.toLowerCase().trim();
        return lower.contains("pain") || lower.contains("cough") || lower.contains("throat") ||
               lower.contains("cold") || lower.contains("constipation") || lower.contains("stomach") ||
               lower.contains("upset") || lower.contains("indigestion") || lower.contains("acid") ||
               lower.contains("reflux") || lower.contains("fever") || lower.contains("headache") ||
               lower.contains("ache") || lower.contains("nausea") || lower.contains("bloating") ||
               lower.contains("diarrhea") || lower.contains("vomit") || lower.contains("motion") ||
               lower.contains("flu") || lower.contains("cramp") || lower.contains("symptom") ||
               isChronicConditionQuery(input);
    }

    public boolean isChronicConditionQuery(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase().trim().replaceAll("\\s+", "");
        return lower.contains("kidney") || lower.contains("renal") || lower.contains("cancer") ||
               lower.contains("heartfailure") || lower.contains("heartattack") || lower.contains("stroke") ||
               lower.contains("liverfailure") || lower.contains("cirrhosis") || lower.contains("dialysis") ||
               lower.contains("tumor") || lower.contains("ketoacidosis") || lower.contains("tuberculosis") ||
               lower.contains("leukemia") || lower.contains("sepsis") || lower.contains("cardiac") ||
               lower.contains("diabetes") || lower.contains("hypertension");
    }
}
