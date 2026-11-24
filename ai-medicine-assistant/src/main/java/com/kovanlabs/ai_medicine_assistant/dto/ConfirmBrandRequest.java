package com.kovanlabs.ai_medicine_assistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmBrandRequest {
    @NotBlank(message = "Brand name is required for confirmation")
    private String brandName;
}
