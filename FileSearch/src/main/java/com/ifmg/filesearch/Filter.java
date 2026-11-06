package com.ifmg.filesearch;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Responsável pelo gerenciamento do índice Lucene (em memória ou persistente em disco)
 * e pela execução de consultas com ponderação BM25, expansão por sinônimos em tempo de busca
 * e filtragem por limiar relativo de relevância.
 */
public class Filter {

    private final Directory indexDirectory;
    private final Analyzer indexAnalyzer;
    private final Analyzer searchAnalyzer;

    public Filter() {
        // Inicializa índice em memória para operações voláteis
        this.indexDirectory = new ByteBuffersDirectory();
        this.indexAnalyzer = AnalyzerStrategyFactory.createMultilingualAnalyzer();
        this.searchAnalyzer = AnalyzerStrategyFactory.createSearchAnalyzer();
    }

    public Filter(String indexPath) throws IOException {
        Path path = Paths.get(indexPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        this.indexDirectory = FSDirectory.open(path);
        this.indexAnalyzer = AnalyzerStrategyFactory.createMultilingualAnalyzer();
        this.searchAnalyzer = AnalyzerStrategyFactory.createSearchAnalyzer();
    }

    /**
     * Indexa arquivos na instância em memória (utilizado em buscas dinâmicas/voláteis).
     */
    public void indexarArquivos(List<String> nomes, List<String> conteudos) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(indexAnalyzer);
        try (IndexWriter writer = new IndexWriter(indexDirectory, config)) {
            for (int i = 0; i < nomes.size(); i++) {
                Document doc = new Document();
                doc.add(new TextField("filename", nomes.get(i), Field.Store.YES));
                doc.add(new TextField("content_pt", conteudos.get(i), Field.Store.YES));
                writer.addDocument(doc);
            }
        }
    }

    /**
     * Busca léxica com ponderação BM25 e expansão por sinônimos na instância em memória.
     */
    public List<DocumentoCandidato> buscarNoLucene(String descricao, List<String> keywords, int limite) throws Exception {
        if (indexDirectory.listAll().length == 0) return new ArrayList<>();

        try (DirectoryReader reader = DirectoryReader.open(indexDirectory)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            String queryString = construirQueryString(descricao, keywords);
            if (queryString.isEmpty()) queryString = "*:*";

            String[] fields = new String[]{"filename", "content_pt", "content_en"};
            Map<String, Float> boosts = new HashMap<>();
            boosts.put("filename", 2.0f);
            boosts.put("content_pt", 1.0f);
            boosts.put("content_en", 1.0f);

            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, searchAnalyzer, boosts);
            parser.setSplitOnWhitespace(false);
            Query query = parser.parse(queryString);

            TopDocs results = searcher.search(query, limite);

            List<DocumentoCandidato> lista = new ArrayList<>();
            float maxScore = results.scoreDocs.length > 0 ? results.scoreDocs[0].score : 0;
            float minScoreThreshold = (maxScore <= 0.5f) ? (maxScore * 0.20f) : Math.max(0.5f, maxScore * 0.20f);

            for (ScoreDoc scoreDoc : results.scoreDocs) {
                if (scoreDoc.score < minScoreThreshold) {
                    continue;
                }

                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String conteudo = doc.get("content_pt");
                if (conteudo == null) conteudo = doc.get("content_en");
                if (conteudo == null) conteudo = doc.get("content");

                if (conteudo != null && conteudo.length() > 2000) {
                    conteudo = conteudo.substring(0, 2000) + "...";
                }

                lista.add(new DocumentoCandidato(doc.get("filename"), conteudo));
            }
            return lista;
        }
    }

    /**
     * Busca no índice persistente aplicando filtros de caminho base, extensões de arquivo,
     * expansão de consulta via tesauro e nota de corte relativa.
     */
    public List<DocumentoCandidato> buscarNoLucene(String descricao, List<String> keywords, String pastaBase, List<String> tiposSelecionados, int limite) throws Exception {
        if (!DirectoryReader.indexExists(indexDirectory)) return new ArrayList<>();

        try (DirectoryReader reader = DirectoryReader.open(indexDirectory)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            BooleanQuery.Builder mainQueryBuilder = new BooleanQuery.Builder();

            // 1. Constrói a query de busca textual (Keywords + Descrição)
            String queryString = construirQueryString(descricao, keywords);
            if (queryString.isEmpty()) queryString = "*:*";

            String[] fields = new String[]{"filename", "content_pt", "content_en"};
            Map<String, Float> boosts = new HashMap<>();
            boosts.put("filename", 2.0f);
            boosts.put("content_pt", 1.0f);
            boosts.put("content_en", 1.0f);

            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, searchAnalyzer, boosts);
            parser.setSplitOnWhitespace(false);
            Query contentQuery = parser.parse(queryString);
            mainQueryBuilder.add(contentQuery, BooleanClause.Occur.MUST);

            // 2. Filtro por Pasta Base (PrefixQuery no campo id)
            if (pastaBase != null && !pastaBase.isEmpty()) {
                String normalizedBasePath = Paths.get(pastaBase).toAbsolutePath().toString();
                if (!normalizedBasePath.endsWith(java.io.File.separator)) {
                    normalizedBasePath += java.io.File.separator;
                }
                PrefixQuery pathQuery = new PrefixQuery(new Term("id", normalizedBasePath));
                mainQueryBuilder.add(pathQuery, BooleanClause.Occur.MUST);
            }

            // 3. Filtro por extensões
            if (tiposSelecionados != null && !tiposSelecionados.isEmpty()) {
                BooleanQuery.Builder extQueryBuilder = new BooleanQuery.Builder();
                for (String ext : tiposSelecionados) {
                    String extClean = ext.startsWith(".") ? ext.substring(1) : ext;
                    extQueryBuilder.add(new TermQuery(new Term("extension", extClean.toLowerCase())), BooleanClause.Occur.SHOULD);
                }
                mainQueryBuilder.add(extQueryBuilder.build(), BooleanClause.Occur.MUST);
            }

            TopDocs results = searcher.search(mainQueryBuilder.build(), limite);

            List<DocumentoCandidato> lista = new ArrayList<>();
            float maxScore = results.scoreDocs.length > 0 ? results.scoreDocs[0].score : 0;
            float minScoreThreshold = (maxScore <= 0.5f) ? (maxScore * 0.20f) : Math.max(0.5f, maxScore * 0.20f);

            System.out.println("=== Resultados da Busca Lucene (Max Score: " + maxScore + ", Threshold: " + minScoreThreshold + ") ===");

            for (ScoreDoc scoreDoc : results.scoreDocs) {
                // Descarta candidatos abaixo do limiar relativo (Threshold = max(0.5, ScoreMax * 0.20))
                if (scoreDoc.score < minScoreThreshold) {
                    continue;
                }

                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String filename = doc.get("filename");
                String id = doc.get("id");
                String conteudo = doc.get("content_pt");
                if (conteudo == null) conteudo = doc.get("content_en");
                if (conteudo == null) conteudo = doc.get("content");

                System.out.println("[APROVADO] Arquivo: " + filename + " | Score: " + scoreDoc.score);

                if (conteudo != null && conteudo.length() > 2000) {
                    conteudo = conteudo.substring(0, 2000) + "...";
                }

                lista.add(new DocumentoCandidato(id, filename, conteudo));
            }
            return lista;
        }
    }

    private String construirQueryString(String descricao, List<String> keywords) {
        StringBuilder queryBuilder = new StringBuilder();

        if (keywords != null) {
            for (String key : keywords) {
                if (key != null && !key.trim().isEmpty()) {
                    queryBuilder.append(QueryParser.escape(key.trim())).append("^2.0 ");
                }
            }
        }

        if (descricao != null && !descricao.trim().isEmpty()) {
            queryBuilder.append(QueryParser.escape(descricao.trim()));
        }

        return queryBuilder.toString().trim();
    }

    public static class DocumentoCandidato {
        public String id;
        public String filename;
        public String content;

        public DocumentoCandidato(String filename, String content) {
            this.filename = filename;
            this.content = content;
        }

        public DocumentoCandidato(String id, String filename, String content) {
            this.id = id;
            this.filename = filename;
            this.content = content;
        }
    }
}
