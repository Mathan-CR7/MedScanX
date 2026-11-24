package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.FoodSupportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class FoodSupportServiceTests {

    @Autowired
    private FoodSupportService foodSupportService;

    @Test
    public void testGetFoodSupportForMinorFeverPain() {
        FoodSupportResponse response = foodSupportService.getFoodSupport("Calpol 500");
        assertNotNull(response);
        assertFalse(response.isMedicineEquivalent());
        assertNotNull(response.getWarning());
    }

    @Test
    public void testGetFoodSupportForSeriousPrescriptionCondition() {
        FoodSupportResponse response = foodSupportService.getFoodSupport("Augmentin 625");
        assertNotNull(response);
        assertFalse(response.isMedicineEquivalent());
        assertNotNull(response.getWarning());
    }

    @Test
    public void testGetFoodSupportForEmptyName() {
        FoodSupportResponse response = foodSupportService.getFoodSupport("");
        assertNotNull(response);
        assertFalse(response.isFoodSupportAvailable());
        assertTrue(response.getFoodSuggestions().isEmpty());
    }
}
