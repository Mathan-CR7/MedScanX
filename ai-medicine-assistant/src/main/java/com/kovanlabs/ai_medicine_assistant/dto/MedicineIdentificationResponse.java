package com.kovanlabs.ai_medicine_assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicineIdentificationResponse {
    private MedicineIdentificationStatus status;
    private String brandName;
    private Double confidence;
    private String ocrCandidate;
    private Long matchedMedicineId;
    private VerificationEvidence evidence;
    private List<String> alternatives;
    private String message;
}
