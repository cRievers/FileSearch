package com.ifmg.filesearch;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Responsável pelo gerenciamento do índice Lucene (em memória ou persistente em disco)
 * e pela execução da busca híbrida:
 *  1. Braço Lexical BM25 com expansão de sinônimos (SynonymGraphFilter) e filtros estruturais;
 *  2. Braço Vetorial Denso kNN via grafo HNSW (KnnFloatVectorQuery) com filtros de escopo;
 *  3. Fusão dos rankings pelo algoritmo Reciprocal Rank Fusion (RRF, k=60);
 *  4. Destaque contextual de trechos (Snippet Highlighting) com UnifiedHighlighter.
 */
public class Filter {

    private final Directory indexDirectory;
    private final Analyzer indexAnalyzer;
    private final Analyzer searchAnalyzer;

    public Filter() {
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
     * Indexa arquivos na instância em memória gerando índice invertido e vetores densos HNSW.
     */
    public void indexarArquivos(List<String> nomes, List<String> conteudos) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(indexAnalyzer);
        try (IndexWriter writer = new IndexWriter(indexDirectory, config)) {
            for (int i = 0; i < nomes.size(); i++) {
                Document doc = new Document();
                String filename = nomes.get(i);
                String content = conteudos.get(i);

                doc.add(new TextField("filename", filename, Field.Store.YES));
                doc.add(new TextField("content_pt", content, Field.Store.YES));

                String ext = "";
                int dot = filename.lastIndexOf('.');
                if (dot > 0) ext = filename.substring(dot + 1).toLowerCase();
                doc.add(new org.apache.lucene.document.StringField("extension", ext, Field.Store.YES));
                doc.add(new org.apache.lucene.document.StringField("id", filename, Field.Store.YES));

                try {
                    float[] vec = EmbeddingService.getInstance().encode(content);
                    doc.add(new KnnFloatVectorField("content_vec", vec, VectorSimilarityFunction.COSINE));
                } catch (Exception e) {
                    System.err.println("Aviso ao gerar embedding em memória: " + e.getMessage());
                }

                writer.addDocument(doc);
            }
        }
    }

    /**
     * Busca na instância em memória delegando para o pipeline híbrido.
     */
    public List<DocumentoCandidato> buscarNoLucene(String descricao, List<String> keywords, int limite) throws Exception {
        return hybridSearch(descricao, keywords, null, null, limite);
    }

    /**
     * Busca no índice persistente delegando para o pipeline híbrido.
     */
    public List<DocumentoCandidato> buscarNoLucene(String descricao, List<String> keywords, String pastaBase, List<String> tiposSelecionados, int limite) throws Exception {
        return hybridSearch(descricao, keywords, pastaBase, tiposSelecionados, limite);
    }

    /**
     * Pipeline híbrido completo: BM25 + kNN HNSW + RRF (k=60) + UnifiedHighlighter.
     */
    public List<DocumentoCandidato> hybridSearch(String descricao, List<String> keywords, String pastaBase, List<String> tiposSelecionados, int topK) throws Exception {
        if (!DirectoryReader.indexExists(indexDirectory)) {
            return new ArrayList<>();
        }

        try (DirectoryReader reader = DirectoryReader.open(indexDirectory)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            // 1. Constrói filtros estruturais comuns (Pasta Base e Extensões)
            Query structuralFilter = buildStructuralFilter(pastaBase, tiposSelecionados);

            // 2. --- Braço Lexical BM25 (com expansão de sinônimos via searchAnalyzer) ---
            String queryString = construirQueryString(descricao, keywords);
            String rawSearchText = buildRawSearchText(descricao, keywords);

            if (queryString.isEmpty()) {
                queryString = "*:*";
            }

            String[] fields = new String[]{"filename", "content_pt", "content_en"};
            Map<String, Float> boosts = new HashMap<>();
            boosts.put("filename", 2.0f);
            boosts.put("content_pt", 1.0f);
            boosts.put("content_en", 1.0f);

            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, searchAnalyzer, boosts);
            parser.setSplitOnWhitespace(false);
            Query contentQuery = parser.parse(queryString);

            BooleanQuery.Builder bm25QueryBuilder = new BooleanQuery.Builder();
            bm25QueryBuilder.add(contentQuery, BooleanClause.Occur.MUST);
            if (structuralFilter != null) {
                bm25QueryBuilder.add(structuralFilter, BooleanClause.Occur.MUST);
            }
            Query bm25CombinedQuery = bm25QueryBuilder.build();

            TopDocs bm25Docs = searcher.search(bm25CombinedQuery, 50);

            // Aplica nota de corte relativa sobre o braço BM25 conforme Seção 4.5.1 / 5.4.1
            TopDocs bm25Filtrados = filtrarPorNotaDeCorte(bm25Docs);

            // 3. --- Braço Vetorial Denso kNN (HNSW) ---
            TopDocs knnDocs;
            try {
                float[] queryVec = EmbeddingService.getInstance().encode(rawSearchText);
                KnnFloatVectorQuery knnQuery = new KnnFloatVectorQuery("content_vec", queryVec, 50, structuralFilter);
                knnDocs = searcher.search(knnQuery, 50);
            } catch (Exception e) {
                System.out.println("[Filter] Aviso: busca vetorial indisponível (" + e.getMessage() + "). Operando em modo lexical.");
                knnDocs = new TopDocs(new TotalHits(0, TotalHits.Relation.EQUAL_TO), new ScoreDoc[0]);
            }

            // 4. --- Reciprocal Rank Fusion (RRF, k=60) ---
            Map<Integer, Double> rrfScores = reciprocalRankFusion(bm25Filtrados, knnDocs, 60);

            List<Map.Entry<Integer, Double>> topEntries = rrfScores.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                    .limit(topK)
                    .collect(Collectors.toList());

            // 5. --- Snippet Highlighting com UnifiedHighlighter ---
            UnifiedHighlighter highlighter = new UnifiedHighlighter(searcher, searchAnalyzer);
            highlighter.setMaxLength(300);

            List<DocumentoCandidato> resultados = new ArrayList<>();
            System.out.println("=== Resultados da Busca Híbrida (BM25: " + bm25Docs.totalHits.value
                    + " hits, kNN: " + knnDocs.totalHits.value + " hits, RRF combinados: " + topEntries.size() + ") ===");

            for (Map.Entry<Integer, Double> entry : topEntries) {
                int docId = entry.getKey();
                double score = entry.getValue();

                Document doc = searcher.storedFields().document(docId);
                String filename = doc.get("filename");
                String id = doc.get("id");
                String conteudo = doc.get("content_pt");
                if (conteudo == null) conteudo = doc.get("content_en");
                if (conteudo == null) conteudo = doc.get("content");

                // Extração do snippet com termos destacados
                String snippet = extractSnippet(highlighter, contentQuery, docId, doc, conteudo);

                System.out.println("[APROVADO RRF] Arquivo: " + filename + " | RRF Score: " + score);

                if (conteudo != null && conteudo.length() > 2000) {
                    conteudo = conteudo.substring(0, 2000) + "...";
                }

                resultados.add(new DocumentoCandidato(id, filename, conteudo, snippet, score));
            }

            return resultados;
        }
    }

    /**
     * Aplica a nota de corte relativa (threshold dinâmico) sobre os resultados do BM25:
     * Threshold = max(0.5, Score_max * 0.20) quando Score_max > 0.5.
     */
    private TopDocs filtrarPorNotaDeCorte(TopDocs docs) {
        if (docs == null || docs.scoreDocs == null || docs.scoreDocs.length == 0) {
            return docs;
        }

        float maxScore = docs.scoreDocs[0].score;
        float minScoreThreshold = (maxScore <= 0.5f) ? (maxScore * 0.20f) : Math.max(0.5f, maxScore * 0.20f);

        List<ScoreDoc> aprovados = new ArrayList<>();
        for (ScoreDoc sd : docs.scoreDocs) {
            if (sd.score >= minScoreThreshold) {
                aprovados.add(sd);
            }
        }

        return new TopDocs(new TotalHits(aprovados.size(), TotalHits.Relation.EQUAL_TO),
                aprovados.toArray(new ScoreDoc[0]));
    }

    /**
     * Algoritmo Reciprocal Rank Fusion: combina múltiplos rankings de candidatos ordenados.
     * RRF(d) = sum( 1.0 / (k + rank_m(d)) )
     */
    private Map<Integer, Double> reciprocalRankFusion(TopDocs bm25, TopDocs knn, int k) {
        Map<Integer, Double> scoreMap = new HashMap<>();

        if (bm25 != null && bm25.scoreDocs != null) {
            for (int rank = 0; rank < bm25.scoreDocs.length; rank++) {
                int docId = bm25.scoreDocs[rank].doc;
                scoreMap.merge(docId, 1.0 / (k + rank + 1), Double::sum);
            }
        }

        if (knn != null && knn.scoreDocs != null) {
            for (int rank = 0; rank < knn.scoreDocs.length; rank++) {
                int docId = knn.scoreDocs[rank].doc;
                scoreMap.merge(docId, 1.0 / (k + rank + 1), Double::sum);
            }
        }

        return scoreMap;
    }

    /**
     * Extrai snippet formatado com demarcação 【termo】 ou fallback semântico.
     */
    private String extractSnippet(UnifiedHighlighter highlighter, Query query, int docId, Document doc, String conteudo) {
        String snippet = null;

        // Tenta destacar primeiro em content_pt
        if (doc.get("content_pt") != null) {
            try {
                String[] highlights = highlighter.highlight("content_pt", query,
                        new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), new ScoreDoc[]{new ScoreDoc(docId, 1.0f)}), 1);
                if (highlights != null && highlights.length > 0 && highlights[0] != null && !highlights[0].trim().isEmpty()) {
                    snippet = highlights[0].replaceAll("<b>", "【").replaceAll("</b>", "】");
                }
            } catch (Exception e) {
                // Fallback silencioso
            }
        }

        // Tenta destacar em content_en se content_pt não tiver highlight
        if (snippet == null && doc.get("content_en") != null) {
            try {
                String[] highlightsEn = highlighter.highlight("content_en", query,
                        new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), new ScoreDoc[]{new ScoreDoc(docId, 1.0f)}), 1);
                if (highlightsEn != null && highlightsEn.length > 0 && highlightsEn[0] != null && !highlightsEn[0].trim().isEmpty()) {
                    snippet = highlightsEn[0].replaceAll("<b>", "【").replaceAll("</b>", "】");
                }
            } catch (Exception e) {
                // Fallback silencioso
            }
        }

        // Se não houver termos correspondentes na esteira léxica (ex: resultado recuperado por vetor)
        if (snippet == null || snippet.trim().isEmpty()) {
            if (conteudo != null && !conteudo.trim().isEmpty()) {
                snippet = conteudo.length() > 250 ? conteudo.substring(0, 250).trim() + "..." : conteudo.trim();
            } else {
                snippet = "(Recuperado por similaridade semântica vetorial)";
            }
        }

        return snippet;
    }

    /**
     * Cria filtro booleano estrutural para Pasta Base e Extensões.
     */
    private Query buildStructuralFilter(String pastaBase, List<String> tiposSelecionados) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        boolean hasClause = false;

        if (pastaBase != null && !pastaBase.trim().isEmpty()) {
            String normalizedBasePath = Paths.get(pastaBase).toAbsolutePath().toString();
            if (!normalizedBasePath.endsWith(java.io.File.separator)) {
                normalizedBasePath += java.io.File.separator;
            }
            builder.add(new PrefixQuery(new Term("id", normalizedBasePath)), BooleanClause.Occur.MUST);
            hasClause = true;
        }

        if (tiposSelecionados != null && !tiposSelecionados.isEmpty()) {
            BooleanQuery.Builder extBuilder = new BooleanQuery.Builder();
            for (String ext : tiposSelecionados) {
                String clean = ext.startsWith(".") ? ext.substring(1) : ext;
                extBuilder.add(new TermQuery(new Term("extension", clean.toLowerCase())), BooleanClause.Occur.SHOULD);
            }
            builder.add(extBuilder.build(), BooleanClause.Occur.MUST);
            hasClause = true;
        }

        return hasClause ? builder.build() : null;
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

    private String buildRawSearchText(String descricao, List<String> keywords) {
        StringBuilder sb = new StringBuilder();
        if (keywords != null) {
            for (String key : keywords) {
                if (key != null && !key.trim().isEmpty()) {
                    sb.append(key.trim()).append(" ");
                }
            }
        }
        if (descricao != null && !descricao.trim().isEmpty()) {
            sb.append(descricao.trim());
        }
        return sb.toString().trim();
    }

    public static class DocumentoCandidato {
        public String id;
        public String filename;
        public String content;
        public String snippet;
        public double score;

        public DocumentoCandidato(String filename, String content) {
            this.filename = filename;
            this.content = content;
        }

        public DocumentoCandidato(String id, String filename, String content) {
            this.id = id;
            this.filename = filename;
            this.content = content;
        }

        public DocumentoCandidato(String id, String filename, String content, String snippet, double score) {
            this.id = id;
            this.filename = filename;
            this.content = content;
            this.snippet = snippet;
            this.score = score;
        }
    }
}
