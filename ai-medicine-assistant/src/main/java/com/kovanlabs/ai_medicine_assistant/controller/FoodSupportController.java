package com.kovanlabs.ai_medicine_assistant.controller;

import com.kovanlabs.ai_medicine_assistant.dto.FoodSupportRequest;
import com.kovanlabs.ai_medicine_assistant.dto.FoodSupportResponse;
import com.kovanlabs.ai_medicine_assistant.service.FoodSupportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class FoodSupportController {

    private final FoodSupportService foodSupportService;

    @GetMapping("/medicines/{medicineName}/food-support")
    public ResponseEntity<FoodSupportResponse> getFoodSupportByPath(
            @PathVariable String medicineName,
            @RequestParam(value = "symptom", required = false) Boolean symptom) {
        log.info("REST GET request for Food-Based Support (Use Case 4) for medicine: {}, symptom: {}", medicineName, symptom);
        FoodSupportResponse response = foodSupportService.getFoodSupport(medicineName, symptom);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/medicine/{medicineName}/food-support")
    public ResponseEntity<FoodSupportResponse> getFoodSupportByPathSingular(
            @PathVariable String medicineName,
            @RequestParam(value = "symptom", required = false) Boolean symptom) {
        return getFoodSupportByPath(medicineName, symptom);
    }

    @PostMapping("/medicines/food-support")
    public ResponseEntity<FoodSupportResponse> getFoodSupportByPost(@RequestBody FoodSupportRequest request) {
        log.info("REST POST request for Food-Based Support (Use Case 4) for medicine: {}",
                request != null ? request.getMedicineName() : "null");
        FoodSupportResponse response = foodSupportService.getFoodSupport(request);
        return ResponseEntity.ok(response);
    }
}
