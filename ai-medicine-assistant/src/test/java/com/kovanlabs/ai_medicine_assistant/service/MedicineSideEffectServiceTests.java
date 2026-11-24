package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.MedicineSideEffectRequest;
import com.kovanlabs.ai_medicine_assistant.dto.MedicineSideEffectResponse;
import com.kovanlabs.ai_medicine_assistant.repository.MedicineMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for MedicineSideEffectService.
 * Mocks ChatClient so tests run without a real OpenAI API key.
 */
public class MedicineSideEffectServiceTests {

    private MedicineSideEffectService sideEffectService;
    private ChatClient chatClient;
    private MedicineMasterRepository masterRepository;
    private OCRNormalizationService normalizationService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        chatClient = mock(ChatClient.class);
        masterRepository = mock(MedicineMasterRepository.class);
        normalizationService = mock(OCRNormalizationService.class);

        // Stub normalizeCompact to return the input unchanged
        when(normalizationService.normalizeCompact(anyString())).thenAnswer(inv -> inv.getArgument(0));

        // Stub DB lookup to return empty (no catalogue entry) — tests pure AI path
        when(masterRepository.findById(any())).thenReturn(java.util.Optional.empty());
        when(masterRepository.findCandidatesByPrefixOrTerm(any(), any(), any(), any()))
                .thenReturn(java.util.Collections.emptyList());

        // Build a realistic MedicineSideEffectResponse that ChatClient.entity() would produce
        MedicineSideEffectResponse aiResponse = MedicineSideEffectResponse.builder()
                .medicineName("Dolo 650")
                .genericName("Paracetamol")
                .strength("650mg")
                .commonSideEffects(List.of("Nausea", "Mild stomach upset"))
                .lessCommonSideEffects(List.of("Rash", "Dizziness"))
                .seriousWarnings(List.of("Severe liver damage if overdosed — seek emergency care immediately"))
                .avoidIf("You are allergic to Paracetamol.")
                .whatItDoes("Relieves pain and fever.")
                .disclaimer("Educational information only. Consult a doctor.")
                .build();

        // Mock the full ChatClient fluent chain: prompt → system → user → entity
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.ChatClientRequestSpec userSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.entity(MedicineSideEffectResponse.class)).thenReturn(aiResponse);

        sideEffectService = new MedicineSideEffectService(chatClient, masterRepository, normalizationService);
    }

    @Test
    public void testGetSideEffectsForVerifiedMedicine() {
        MedicineSideEffectRequest request = MedicineSideEffectRequest.builder()
                .brandName("Dolo 650")
                .genericName("Paracetamol")
                .strength("650mg")
                .manufacturer("Micro Labs Ltd")
                .build();

        MedicineSideEffectResponse response = sideEffectService.getSideEffectsAndSafetyInfo(request);

        assertNotNull(response);
        assertEquals("Dolo 650", response.getMedicineName());
        assertNotNull(response.getCommonSideEffects());
        assertFalse(response.getCommonSideEffects().isEmpty());
        assertNotNull(response.getSeriousWarnings());
        assertFalse(response.getSeriousWarnings().isEmpty());
        assertNotNull(response.getAvoidIf());
        assertNotNull(response.getWhatItDoes());
        assertNotNull(response.getDisclaimer());
    }

    @Test
    public void testGetSideEffectsForUnverifiedMedicine() {
        MedicineSideEffectRequest request = MedicineSideEffectRequest.builder()
                .brandName("")
                .genericName("")
                .strength("")
                .build();

        MedicineSideEffectResponse response = sideEffectService.getSideEffectsAndSafetyInfo(request);

        assertNotNull(response);
        assertTrue(response.getCommonSideEffects().isEmpty());
        assertTrue(response.getDisclaimer().contains("could not be verified") || response.getDisclaimer().contains("could not be generated"));
    }
}
