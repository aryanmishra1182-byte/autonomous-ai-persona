package com.persona.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersonaServiceTest {

    private final PersonaService personaService = new PersonaService();

    @Test
    void generatePersonaPrompt_ShouldContainNameAndDomain() {
        String prompt = personaService.generatePersonaPrompt("Ada", "AI Security");
        assertNotNull(prompt);
        assertTrue(prompt.contains("Ada"));
        assertTrue(prompt.contains("AI Security"));
        assertTrue(prompt.contains("vulnerabilities"));
    }

    @Test
    void getWritingStyleGuide_ShouldReturnFormattedGuide() {
        String guide = personaService.getWritingStyleGuide("Ada", "AI Security");
        assertNotNull(guide);
        assertTrue(guide.contains("Writing Style Guide for Ada"));
        assertTrue(guide.contains("200-400 words"));
    }

    @Test
    void generateBio_ShouldReturnNonEmptyString() {
        String bio = personaService.generateBio("Ada", "AI Security");
        assertNotNull(bio);
        assertTrue(bio.contains("Ada"));
        assertTrue(bio.contains("AI Security"));
    }
}
