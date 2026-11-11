package com.ifmg.filesearch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EmbeddingServiceTest {

    @Test
    @DisplayName("Deve inicializar EmbeddingService e gerar embedding de 384 dimensões")
    void testEmbeddingDimensionsAndNormalization() {
        EmbeddingService service = EmbeddingService.getInstance();
        assertNotNull(service, "Instância de EmbeddingService não deve ser nula");

        String text = "Contrato de prestação de serviços e rescisão de avença.";
        float[] vector = service.encode(text);

        assertNotNull(vector);
        assertEquals(384, vector.length, "O vetor deve ter exatamente 384 dimensões");

        // Verifica que a norma L2 é aproximadamente 1.0
        float sumSquares = 0.0f;
        for (float v : vector) {
            sumSquares += v * v;
        }
        float norm = (float) Math.sqrt(sumSquares);
        assertEquals(1.0f, norm, 1e-4, "O vetor deve ser unitário (norma L2 = 1.0)");
    }

    @Test
    @DisplayName("Deve apresentar maior similaridade semântica para paráfrases (rescisão vs cancelamento de contrato)")
    void testSemanticSimilarityCosine() {
        EmbeddingService service = EmbeddingService.getInstance();

        float[] vecRescisao = service.encode("rescisão de contrato de prestação de serviços");
        float[] vecCancelamento = service.encode("cancelamento de contrato e encerramento de vínculo");
        float[] vecReceitaBolo = service.encode("receita de bolo de chocolate com morango e calda");

        float simParaphrase = cosineSimilarity(vecRescisao, vecCancelamento);
        float simUnrelated = cosineSimilarity(vecRescisao, vecReceitaBolo);

        System.out.println("Similaridade semântica (paráfrase): " + simParaphrase);
        System.out.println("Similaridade semântica (não relacionado): " + simUnrelated);

        assertTrue(simParaphrase > 0.65f, "A similaridade entre termos correlatos deve ser alta (> 0.65)");
        assertTrue(simParaphrase > simUnrelated + 0.30f, "A similaridade da paráfrase deve ser expressivamente superior à do texto não correlato");
    }

    private float cosineSimilarity(float[] v1, float[] v2) {
        float dot = 0.0f;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
        }
        return dot;
    }
}

