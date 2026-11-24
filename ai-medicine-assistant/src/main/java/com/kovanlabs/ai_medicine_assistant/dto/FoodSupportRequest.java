package com.kovanlabs.ai_medicine_assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodSupportRequest {
    private String medicineName;
    private String modelName;
    private Boolean forceSymptom;
}
