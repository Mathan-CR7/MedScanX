package com.kovanlabs.ai_medicine_assistant.dto;

public record TrackMedicineRequest(String name) {
    public TrackMedicineRequest {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Medicine name cannot be null or empty");
        }
    }
}
