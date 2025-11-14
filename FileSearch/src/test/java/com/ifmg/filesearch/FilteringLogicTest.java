package com.ifmg.filesearch;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

// Esta é uma classe auxiliar para testar a lógica de filtragem que estaria no Controller
public class FilteringLogicTest {

    // Método auxiliar para isolar a lógica de filtragem
    private boolean contentMatchesKeywords(String content, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return true; // Se não há palavras-chave, o arquivo sempre corresponde
        }
        if (content == null) {
            return false;
        }

        String contentLower = content.toLowerCase();
        List<String> keywordsLower = keywords.stream().map(String::toLowerCase).toList();

        // O arquivo corresponde se O CONTEÚDO contém TODAS as palavras-chave
        return keywordsLower.stream()
            .allMatch(contentLower::contains);
    }

    @Test
    void testMatch_AllKeywordsPresent() {
        System.out.println("TESTE: testMatch_AllKeywordsPresent");
        String content = "O projeto final deve incluir testes unitários e refatoração.";
        List<String> keywords = List.of("projeto", "testes unitários");
        assertTrue(contentMatchesKeywords(content, keywords), "Deve ser TRUE se todas as palavras-chave estiverem presentes.");
    }

    @Test
    void testMatch_MissingKeyword() {
        System.out.println("TESTE: testMatch_MissingKeyword");
        String content = "O projeto final deve incluir testes.";
        List<String> keywords = List.of("projeto", "refatoração");
        assertFalse(contentMatchesKeywords(content, keywords), "Deve ser FALSE se uma palavra-chave estiver faltando.");
    }

    @Test
    void testMatch_CaseInsensitive() {
        System.out.println("TESTE: testMatch_CaseInsensitive");
        String content = "O ProJeTo e ótimo.";
        List<String> keywords = List.of("projeto", "ótimo");
        assertTrue(contentMatchesKeywords(content, keywords), "Deve ser TRUE, ignorando o caso (case-insensitive).");
    }

    @Test
    void testMatch_NoKeywords() {
        System.out.println("TESTE: testMatch_NoKeywords");
        String content = "Qualquer coisa.";
        List<String> keywords = List.of();
        assertTrue(contentMatchesKeywords(content, keywords), "Deve ser TRUE se a lista de palavras-chave estiver vazia.");
    }
}