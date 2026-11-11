package com.ifmg.filesearch;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HybridSearchTest {

    @Test
    @DisplayName("Deve executar busca híbrida completa (BM25 + kNN HNSW + RRF) e extrair snippet")
    void testHybridSearchWithSnippet() throws Exception {
        Filter filter = new Filter();

        List<String> nomes = List.of(
                "contrato_locacao.pdf",
                "declaracao_rescisao.docx",
                "relatorio_financeiro.xlsx"
        );

        List<String> conteudos = List.of(
                "Instrumento particular de locação de imóvel residencial urbano firmado entre as partes.",
                "Termo de rescisão contratual amigável decorrente de encerramento do projeto.",
                "Planilha de balanço orçamentário anual contendo despesas com viagens e equipamentos."
        );

        filter.indexarArquivos(nomes, conteudos);

        // Busca por sinônimo/termo: "aluguel" deve recuperar "contrato_locacao.pdf"
        List<Filter.DocumentoCandidato> resultados = filter.hybridSearch("aluguel de imóvel", null, null, null, 10);

        assertNotNull(resultados);
        assertFalse(resultados.isEmpty(), "Deve recuperar ao menos um resultado para 'aluguel de imóvel'");

        Filter.DocumentoCandidato primeiro = resultados.get(0);
        assertEquals("contrato_locacao.pdf", primeiro.filename);
        assertTrue(primeiro.score > 0, "O score RRF deve ser positivo");
        assertNotNull(primeiro.snippet, "Snippet não deve ser nulo");
        System.out.println("Snippet gerado: " + primeiro.snippet);
    }

    @Test
    @DisplayName("Deve resolver descompasso vocabular via vetor denso (cancelamento de contrato -> rescisão)")
    void testVocabularyMismatchViaDenseVector() throws Exception {
        Filter filter = new Filter();

        List<String> nomes = List.of(
                "rescisao_prestacao_servicos.docx",
                "manual_operacao_maquinas.pdf"
        );

        List<String> conteudos = List.of(
                "Cláusula de rescisão contratual por inadimplemento com aplicação de multa compensatória.",
                "Guia prático de manutenção preventiva de motores elétricos trifásicos e circuitos industriais."
        );

        filter.indexarArquivos(nomes, conteudos);

        // Consulta usando termos conceituais sem sobreposição direta com o texto:
        List<Filter.DocumentoCandidato> resultados = filter.hybridSearch(
                "cancelamento de contrato", null, null, null, 5
        );

        assertNotNull(resultados);
        assertFalse(resultados.isEmpty(), "A busca híbrida deve recuperar o documento semântico");

        Filter.DocumentoCandidato primeiro = resultados.get(0);
        assertEquals("rescisao_prestacao_servicos.docx", primeiro.filename,
                "O documento de rescisão deve ser classificado em 1º lugar mesmo buscando por cancelamento");
        assertTrue(primeiro.score > 0, "Score RRF deve ser maior que zero");
    }

    @Test
    @DisplayName("Deve respeitar filtros estruturais de extensão na busca híbrida")
    void testHybridSearchStructuralExtensionFilter() throws Exception {
        Filter filter = new Filter();

        List<String> nomes = List.of(
                "documento_a.pdf",
                "documento_b.docx"
        );

        List<String> conteudos = List.of(
                "Contrato de prestação de serviços de consultoria contábil.",
                "Contrato de prestação de serviços de assessoria jurídica."
        );

        filter.indexarArquivos(nomes, conteudos);

        // Filtra estritamente por .docx
        List<Filter.DocumentoCandidato> resultadosDocx = filter.hybridSearch(
                "serviços", null, null, List.of(".docx"), 10
        );

        assertEquals(1, resultadosDocx.size(), "Deve retornar apenas 1 resultado com extensão .docx");
        assertEquals("documento_b.docx", resultadosDocx.get(0).filename);
    }
}

