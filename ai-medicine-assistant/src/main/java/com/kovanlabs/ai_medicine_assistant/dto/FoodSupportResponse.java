package com.kovanlabs.ai_medicine_assistant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FoodSupportResponse implements Serializable {

    private static final long serialVersionUID = 1L;
    private String medicine;

    @JsonAlias({"used_for", "usedFor"})
    private String usedFor;

    @JsonAlias({"food_support_available", "foodSupportAvailable"})
    private boolean foodSupportAvailable;

    @JsonAlias({"food_suggestions", "foodSuggestions"})
    private List<FoodSuggestionDTO> foodSuggestions;

    @Builder.Default
    @JsonAlias({"medicine_equivalent", "medicineEquivalent"})
    private boolean medicineEquivalent = false;

    @Builder.Default
    @JsonAlias({"chronic_condition", "is_chronic_condition", "chronicCondition", "isChronicCondition"})
    private boolean chronicCondition = false;

    private String warning;
}