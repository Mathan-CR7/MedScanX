package com.kovanlabs.ai_medicine_assistant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for mapping the AI substitute brand suggestion response.
 * Uses @JsonAlias to support both snake_case and camelCase keys for absolute mapping robustness.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiAlternativeSuggestion {

    @JsonAlias({"original_medicine", "originalMedicine"})
    private String originalMedicine;

    private String composition;
    private String strength;

    @JsonAlias({"dosage_form", "dosageForm"})
    private String dosageForm;

    @JsonAlias({"alternative_brands", "alternativeBrands", "alternatives"})
    private List<String> alternativeBrands;
}
