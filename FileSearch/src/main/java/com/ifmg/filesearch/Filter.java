package com.ifmg.filesearch;

import com.fasterxml.jackson.databind.ObjectMapper; // Usando Jackson pois já está no projeto (AIutils)
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class Filter {

    private Directory indexDirectory;
    private StandardAnalyzer analyzer;
    private AIutils aiUtils;
    private ObjectMapper objectMapper;

    public Filter() {
        // Inicializa um novo diretório na RAM para cada instância (busca nova = índice novo)
        this.indexDirectory = new ByteBuffersDirectory();
        this.analyzer = new StandardAnalyzer();
        this.aiUtils = new AIutils();
        this.objectMapper = new ObjectMapper();
    }

    // 1. Indexa os arquivos encontrados pelo FileFinder
    public void indexarArquivos(List<String> nomes, List<String> conteudos) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(indexDirectory, config)) {
            for (int i = 0; i < nomes.size(); i++) {
                Document doc = new Document();
                doc.add(new TextField("filename", nomes.get(i), Field.Store.YES));
                doc.add(new TextField("content", conteudos.get(i), Field.Store.YES));
                writer.addDocument(doc);
            }
        }
    }

    // 2. Filtro Grosso com Lucene (Keywords + Descrição)
    public List<DocumentoCandidato> buscarNoLucene(String descricao, List<String> keywords, int limite) throws Exception {
        if (indexDirectory.listAll().length == 0) return new ArrayList<>();

        DirectoryReader reader = DirectoryReader.open(indexDirectory);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        // Constrói uma query Boolean: (content:keyword1 OR content:keyword2 OR content:descricao...)
        StringBuilder queryBuilder = new StringBuilder();
        
        // Adiciona keywords com peso alto (boost)
        for (String key : keywords) {
            queryBuilder.append("content:").append(QueryParser.escape(key)).append("^2.0 "); 
        }
        
        // Adiciona palavras da descrição
        if (!descricao.isEmpty()) {
            queryBuilder.append(QueryParser.escape(descricao));
        }

        String queryString = queryBuilder.toString().trim();
        if (queryString.isEmpty()) queryString = "*:*"; // Se vazio, traz tudo

        QueryParser parser = new QueryParser("content", analyzer);
        Query query = parser.parse(queryString);
        
        TopDocs results = searcher.search(query, limite);
        
        List<DocumentoCandidato> lista = new ArrayList<>();
        for (ScoreDoc scoreDoc : results.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            String conteudo = doc.get("content");
            
            // Trunca conteúdo para economizar tokens na próxima etapa
            // 2000 chars é uma margem segura para chunks
            if (conteudo.length() > 2000) conteudo = conteudo.substring(0, 2000) + "...";
            
            lista.add(new DocumentoCandidato(doc.get("filename"), conteudo));
        }
        reader.close();
        return lista;
    }

    // 3. Filtro Fino com Ollama EM LOTES
    public List<String> filtrarComOllamaEmLotes(String intencaoUsuario, List<DocumentoCandidato> candidatos) {
        List<String> arquivosFinais = new ArrayList<>();
        
        // Configuração do Lote
        int tamanhoLote = 5; // Envia 5 arquivos por vez para a IA
        
        for (int i = 0; i < candidatos.size(); i += tamanhoLote) {
            // Cria o subgrupo (batch)
            int fim = Math.min(i + tamanhoLote, candidatos.size());
            List<DocumentoCandidato> lote = candidatos.subList(i, fim);
            
            System.out.println("Processando lote de IA: " + i + " até " + fim);
            
            try {
                // Monta o prompt para este lote específico
                String prompt = montarPromptLote(intencaoUsuario, lote);
                
                // Chama o Ollama (reutilizando sua classe AIutils)
                String respostaJson = aiUtils.generate(prompt);
                
                // Processa a resposta do lote
                List<String> aprovadosNoLote = processarRespostaIA(respostaJson);
                arquivosFinais.addAll(aprovadosNoLote);
                
            } catch (Exception e) {
                System.err.println("Erro ao processar lote " + i + ": " + e.getMessage());
            }
        }
        
        return arquivosFinais;
    }

    private String montarPromptLote(String intencao, List<DocumentoCandidato> lote) throws Exception {
        // Serializa o lote para JSON para ficar organizado no prompt
        String jsonLote = objectMapper.writeValueAsString(lote);

        return """
            Você é um filtro de arquivos preciso.
            
            DESCRIÇÃO DO USUÁRIO: "%s"
            
            Analise os seguintes arquivos candidatos (fornecidos em JSON):
            %s
            
            Quais destes arquivos são SEMANTICAMENTE relevantes para a descrição do usuário?
            Responda APENAS um JSON Array de Strings com o ranking dos nomes exatos ("filename") dos arquivos aprovados.
            Exemplo de resposta: ["relatorio.pdf", "notas.txt"]
            Se nenhum for relevante neste lote, responda: []
            NÃO escreva explicações. Apenas o JSON.
            """.formatted(intencao, jsonLote);
    }

    private List<String> processarRespostaIA(String resposta) {
        List<String> nomes = new ArrayList<>();
        try {
            // Limpeza básica caso o modelo seja "verboso" (markdown blocks)
            String jsonLimpo = resposta.replace("```json", "").replace("```", "").trim();
            
            // O Jackson lê o array de strings
            List<?> lista = objectMapper.readValue(jsonLimpo, List.class);
            for (Object item : lista) {
                nomes.add(item.toString());
            }
        } catch (Exception e) {
            System.err.println("Falha ao parsear resposta da IA: " + resposta);
        }
        return nomes;
    }

    // Classe auxiliar interna
    public static class DocumentoCandidato {
        public String filename;
        public String content;

        public DocumentoCandidato(String filename, String content) {
            this.filename = filename;
            this.content = content;
        }
    }
}