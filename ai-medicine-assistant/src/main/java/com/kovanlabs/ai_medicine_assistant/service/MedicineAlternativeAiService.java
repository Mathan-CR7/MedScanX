package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.AiAlternativeSuggestion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI LLM Service:
 * Uses Generative AI (Spring AI / ChatClient) to analyze a medicine brand name,
 * extract its generic active composition & strength, and suggest real Indian alternative brand names with the exact same composition.
 */
@Service
@Slf4j
public class MedicineAlternativeAiService {

    private final ChatClient chatClient;

    @Autowired
    public MedicineAlternativeAiService(@Autowired(required = false) ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public AiAlternativeSuggestion getAlternativeBrandsFromAi(String brandName) {
        log.info("[MedicineAlternativeAiService] Requesting AI generic substitute analysis for brand: '{}'", brandName);

        if (chatClient != null) {
            try {
                String systemPrompt = """
                        You are an expert Indian pharmacology assistant.
                        - Return ONLY valid JSON using snake_case keys. No markdown, no explanation outside the JSON.
                        - Do NOT use any emoji or special characters.
                        - alternative_brands must always contain 4 to 5 real Indian brand names.
                        """;

                String userPrompt = String.format("""
                        Analyze the Indian medicine brand: '%s'

                        Return ONLY valid JSON in this EXACT snake_case structure:
                        {
                          "original_medicine": "Calpol 500",
                          "composition": "Paracetamol",
                          "strength": "500 mg",
                          "dosage_form": "Tablet",
                          "alternative_brands": ["Dolo 500", "P-500", "Pacimol 500", "Pyrigesic 500", "Febridol 500"]
                        }

                        Rules:
                        - composition: the active generic ingredient name only (e.g. "Paracetamol")
                        - strength: dose strength with unit (e.g. "500 mg")
                        - dosage_form: Tablet / Capsule / Syrup / Injection
                        - alternative_brands: exactly 4 to 5 REAL Indian brand names from DIFFERENT manufacturers
                          with the EXACT SAME composition and strength as '%s'
                        - Do NOT include any brand name that contains the word '%s'
                        """, brandName, brandName, brandName.split(" ")[0]);

                AiAlternativeSuggestion suggestion = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .entity(AiAlternativeSuggestion.class);

                if (suggestion != null && suggestion.getAlternativeBrands() != null && !suggestion.getAlternativeBrands().isEmpty()) {
                    log.info("[MedicineAlternativeAiService] Spring AI successfully identified {} alternative brands for '{}'",
                            suggestion.getAlternativeBrands().size(), brandName);
                    return suggestion;
                }
            } catch (Exception e) {
                log.warn("[MedicineAlternativeAiService] Notice invoking Spring AI: {}", e.getMessage());
            }
        }

        return AiAlternativeSuggestion.builder()
                .originalMedicine(brandName)
                .composition("Unknown")
                .strength("N/A")
                .dosageForm("Tablet")
                .alternativeBrands(new ArrayList<>())
                .build();
    }
}
