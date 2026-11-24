package com.kovanlabs.ai_medicine_assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicineSideEffectRequest {
    private String brandName;
    private String genericName;
    private String strength;
    private String manufacturer;
    private Long medicineId;
    private String modelName;
}
