package com.kovanlabs.ai_medicine_assistant.controller;

import com.kovanlabs.ai_medicine_assistant.dto.MedicineSideEffectRequest;
import com.kovanlabs.ai_medicine_assistant.dto.MedicineSideEffectResponse;
import com.kovanlabs.ai_medicine_assistant.service.MedicineSideEffectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Independent Controller for Use Case 2: AI-Based Possible Side Effects Suggestion.
 * Exposes POST /api/medicine/side-effects and POST /api/medicines/side-effects.
 * Supports dynamic per-request model selection via Postman JSON body or query parameter.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class MedicineSideEffectController {

    private final MedicineSideEffectService sideEffectService;

    @GetMapping({"/api/medicines/{medicineName}/side-effects", "/api/medicine/{medicineName}/side-effects"})
    public ResponseEntity<MedicineSideEffectResponse> getSideEffectsByPath(@PathVariable("medicineName") String medicineName) {
        log.info("[MedicineSideEffectController] Received GET side effect inquiry for medicine: '{}'", medicineName);
        MedicineSideEffectResponse response = sideEffectService.getSideEffectsAndSafetyInfo(
                MedicineSideEffectRequest.builder().brandName(medicineName).build()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/api/medicine/side-effects", "/api/medicines/side-effects"})
    public ResponseEntity<MedicineSideEffectResponse> getSideEffects(
            @RequestBody(required = false) MedicineSideEffectRequest request,
            @RequestParam(value = "brandName", required = false) String brandName,
            @RequestParam(value = "genericName", required = false) String genericName,
            @RequestParam(value = "strength", required = false) String strength,
            @RequestParam(value = "manufacturer", required = false) String manufacturer,
            @RequestParam(value = "modelName", required = false) String modelName,
            @RequestParam(value = "medicineId", required = false) Long medicineId) {

        if (request == null) {
            request = MedicineSideEffectRequest.builder()
                    .brandName(brandName)
                    .genericName(genericName)
                    .strength(strength)
                    .manufacturer(manufacturer)
                    .medicineId(medicineId)
                    .modelName(modelName)
                    .build();
        } else {
            if (request.getBrandName() == null && brandName != null) request.setBrandName(brandName);
            if (request.getGenericName() == null && genericName != null) request.setGenericName(genericName);
            if (request.getStrength() == null && strength != null) request.setStrength(strength);
            if (request.getManufacturer() == null && manufacturer != null) request.setManufacturer(manufacturer);
            if (request.getMedicineId() == null && medicineId != null) request.setMedicineId(medicineId);
            if (request.getModelName() == null && modelName != null) request.setModelName(modelName);
        }

        log.info("[MedicineSideEffectController] Received side effect inquiry for medicine: '{}' (model: '{}')",
                request.getBrandName(), request.getModelName());
        MedicineSideEffectResponse response = sideEffectService.getSideEffectsAndSafetyInfo(request);
        return ResponseEntity.ok(response);
    }
}
