package com.ifmg.filesearch;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SynonymServiceTest {

    @Test
    @DisplayName("Deve carregar o mapa de sinônimos com sucesso a partir de /synonyms_pt.txt")
    void testSynonymServiceInitialization() {
        SynonymService service = SynonymService.getInstance();
        assertNotNull(service, "Instância de SynonymService não deve ser nula");
        assertNotNull(service.getSynonymMap(), "SynonymMap não deve ser nulo");
    }

    @Test
    @DisplayName("Deve recuperar documento de 'locação' pesquisando pelo sinônimo 'aluguel'")
    void testSynonymExpansionAluguelLocacao() throws Exception {
        Directory dir = new ByteBuffersDirectory();
        Analyzer indexAnalyzer = AnalyzerStrategyFactory.createMultilingualAnalyzer();
        Analyzer searchAnalyzer = AnalyzerStrategyFactory.createSearchAnalyzer();

        // 1. Indexa documento com o termo 'locacao'
        try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(indexAnalyzer))) {
            Document doc = new Document();
            doc.add(new TextField("filename", "contrato_imovel.pdf", Field.Store.YES));
            doc.add(new TextField("content_pt", "Contrato formal de locacao de bem imovel comercial urbano.", Field.Store.YES));
            writer.addDocument(doc);
        }

        // 2. Executa busca pelo termo 'aluguel' usando o analisador de consulta
        long start = System.currentTimeMillis();
        try (DirectoryReader reader = DirectoryReader.open(dir)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            MultiFieldQueryParser parser = new MultiFieldQueryParser(
                    new String[]{"filename", "content_pt"},
                    searchAnalyzer
            );
            parser.setSplitOnWhitespace(false);
            Query query = parser.parse("aluguel");

            TopDocs results = searcher.search(query, 10);
            long latency = System.currentTimeMillis() - start;

            System.out.println("Tempo de busca com expansao de sinonimos: " + latency + " ms");
            assertEquals(1, results.totalHits.value, "O documento contendo 'locacao' deve ser encontrado pela query 'aluguel'");

            Document matchedDoc = searcher.storedFields().document(results.scoreDocs[0].doc);
            assertEquals("contrato_imovel.pdf", matchedDoc.get("filename"));
            assertTrue(latency < 150, "A latência da consulta deve ser inferior a 150 ms");
        }
    }

    @Test
    @DisplayName("Deve recuperar documento de 'distrato' pesquisando pelo sinônimo 'rescisao' no Filter")
    void testSynonymExpansionRescisaoDistratoViaFilter() throws Exception {
        Filter filter = new Filter();
        List<String> nomes = List.of("termo_distrato.docx", "manual_usuario.txt");
        List<String> conteudos = List.of(
                "Termo de distrato bilateral de prestacao de servicos de tecnologia.",
                "Instrucoes para operacao do sistema operacional de computadores."
        );
        filter.indexarArquivos(nomes, conteudos);

        long start = System.currentTimeMillis();
        List<Filter.DocumentoCandidato> resultados = filter.buscarNoLucene("rescisao", List.of(), 5);
        long latency = System.currentTimeMillis() - start;

        System.out.println("Tempo de busca no Filter: " + latency + " ms");
        assertFalse(resultados.isEmpty(), "Deve recuperar documento com distrato via expansão de sinônimo");
        assertEquals("termo_distrato.docx", resultados.get(0).filename);
        assertTrue(latency < 150, "A latência no Filter deve ser inferior a 150 ms");
    }
}
