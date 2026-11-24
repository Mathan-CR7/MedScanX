package com.kovanlabs.ai_medicine_assistant.controller;

import com.kovanlabs.ai_medicine_assistant.dto.MedicineAlternativeResponse;
import com.kovanlabs.ai_medicine_assistant.model.Medicine;
import com.kovanlabs.ai_medicine_assistant.service.MedicineAlternativeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for Use Case 3: Compare Lower-Cost Medicines with the Same Composition.
 * Exposes GET /api/medicines/{medicineName}/alternatives and GET /api/medicine/{medicineName}/alternatives.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class MedicineAlternativeController {
    private final MedicineAlternativeService alternativeService;

    @GetMapping({"/api/medicines/{medicineName}/alternatives", "/api/medicine/{medicineName}/alternatives"})
    public ResponseEntity<MedicineAlternativeResponse> getAlternativesByPath(@PathVariable("medicineName") String medicineName) {
        log.info("[MedicineAlternativeController] Received GET request for lower-cost alternatives for: '{}'", medicineName);
        MedicineAlternativeResponse response = alternativeService.findLowerCostAlternatives(medicineName);
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/api/medicines/alternatives", "/api/medicine/alternatives"})
    public ResponseEntity<MedicineAlternativeResponse> getAlternativesByBody(@RequestBody Map<String, String> body) {
        String medicineName = body.getOrDefault("medicineName", body.getOrDefault("brandName", ""));
        log.info("[MedicineAlternativeController] Received POST request for lower-cost alternatives for: '{}'", medicineName);
        MedicineAlternativeResponse response = alternativeService.findLowerCostAlternatives(medicineName);
        return ResponseEntity.ok(response);
    }
}