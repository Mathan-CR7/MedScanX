package com.kovanlabs.ai_medicine_assistant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodSuggestionDTO implements Serializable {

    private static final long serialVersionUID = 1L;
    private String food;
    private String quantity;

    @JsonAlias({"supportive_component", "supportiveComponent", "helpful_part", "helpfulPart"})
    private String helpfulPart;

    private String reason;
    private String precaution;
}
