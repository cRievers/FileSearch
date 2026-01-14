package com.ifmg.filesearch;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory; // Use FSDirectory para disco real
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Filter {

    private static Directory indexDirectory = new ByteBuffersDirectory();
    private static StandardAnalyzer analyzer = new StandardAnalyzer();
    //private static Tika tika = new Tika(); // O "Leitor Universal" de arquivos

    // public static void main(String[] args) throws Exception {
        
    //     // 1. Indexar arquivos de uma pasta real (Exemplo)
    //     // indexarPasta(new File("./meus_documentos")); 
        
    //     // Simulação de arquivos indexados para o teste:
    //     simularIndexacao("relatorio_financeiro_2023.pdf", "O lucro líquido da empresa caiu 10% devido a impostos.");
    //     simularIndexacao("receita_torta.txt", "Para o lucro da festa, venda a torta por 10 reais.");
    //     simularIndexacao("manual_java.docx", "Java é uma linguagem verbosa mas robusta.");

    //     // 2. Entrada do Usuário
    //     String userQuery = "Quero o arquivo que fala sobre prejuízo ou queda nos ganhos da empresa";
    //     System.out.println("Usuário procura: " + userQuery);

    //     // 3. FASE 1: Lucene (Filtro Grosso)
    //     // Extraímos palavras-chave da query (simplificado) ou mandamos a query toda
    //     List<DocumentoCandidato> candidatos = buscarNoLucene(userQuery, 5); // Pega top 5

    //     System.out.println("\n--- Lucene encontrou " + candidatos.size() + " candidatos baseados em palavras-chave ---");
    //     candidatos.forEach(d -> System.out.println("- " + d.filename));

    //     // 4. FASE 2: Ollama (Filtro Fino / Semântico)
    //     if (!candidatos.isEmpty()) {
    //         List<String> arquivosFinais = filtrarComOllama(userQuery, candidatos);
            
    //         System.out.println("\n--- Resultado Final da IA ---");
    //         System.out.println("Arquivos relevantes: " + arquivosFinais);
    //     }
    // }

    // --- MÉTODOS DE LUCENE ---

    public static void simularIndexacao(String nome, String conteudo) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(indexDirectory, config)) {
            Document doc = new Document();
            doc.add(new TextField("filename", nome, Field.Store.YES));
            doc.add(new TextField("content", conteudo, Field.Store.YES));
            writer.addDocument(doc);
        }
    }

    public static List<DocumentoCandidato> buscarNoLucene(String textoUsuario, int limite) throws Exception {
        DirectoryReader reader = DirectoryReader.open(indexDirectory);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        // QueryParser padrão busca exato, mas podemos adicionar "~" para Fuzzy (aproximação)
        // Aqui estamos simplificando: busca qualquer palavra que o usuario digitou
        QueryParser parser = new QueryParser("content", analyzer);
        
        // Tratamento básico: transformar a frase do usuário em uma busca boolean OR
        // Ex: "queda ganhos" vira "content:queda OR content:ganhos"
        // O escape previne erros com caracteres especiais
        Query query = parser.parse(QueryParser.escape(textoUsuario)); 
        
        TopDocs results = searcher.search(query, limite);
        
        List<DocumentoCandidato> lista = new ArrayList<>();
        for (ScoreDoc scoreDoc : results.scoreDocs) {
            StoredFields storedFields = searcher.storedFields();

            Document doc = storedFields.document(scoreDoc.doc);
            // Pegamos o conteúdo truncado para não estourar o limite do Ollama depois
            String conteudo = doc.get("content");
            if (conteudo.length() > 500) conteudo = conteudo.substring(0, 500) + "...";
            
            lista.add(new DocumentoCandidato(doc.get("filename"), conteudo));
        }
        reader.close();
        return lista;
    }

    // --- INTEGRAÇÃO OLLAMA ---

    // public static List<String> filtrarComOllama(String intencaoUsuario, List<DocumentoCandidato> candidatos) throws Exception {
    //     Gson gson = new Gson();
        
    //     // Construir um JSON dos candidatos para o Prompt
    //     String jsonCandidatos = gson.toJson(candidatos);

    //     // Prompt de Engenharia: Instrução estrita para retornar apenas nomes
    //     String prompt = """
    //             Você é um assistente de sistema de arquivos.
                
    //             INTENÇÃO DO USUÁRIO: "%s"
                
    //             ARQUIVOS CANDIDATOS (JSON):
    //             %s
                
    //             TAREFA: Analise o conteúdo dos candidatos. Retorne APENAS um Array JSON de Strings contendo os nomes dos arquivos (filename) que atendem semanticamente à intenção do usuário.
    //             Se nenhum arquivo servir, retorne um array vazio [].
    //             NÃO explique nada. NÃO dê introdução. Apenas o JSON.
    //             """.formatted(intencaoUsuario, jsonCandidatos);

    //     // Payload
    //     JsonObject requestJson = new JsonObject();
    //     requestJson.addProperty("model", "llama3"); // ou mistral
    //     requestJson.addProperty("prompt", prompt);
    //     requestJson.addProperty("stream", false);
    //     // Dica: "format": "json" força o Ollama a responder JSON estruturado (funciona bem no Llama3)
    //     requestJson.addProperty("format", "json"); 

    //     HttpClient client = HttpClient.newHttpClient();
    //     HttpRequest request = HttpRequest.newBuilder()
    //             .uri(URI.create("http://localhost:11434/api/generate"))
    //             .header("Content-Type", "application/json")
    //             .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestJson)))
    //             .build();

    //     HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
    //     // Parse da resposta
    //     JsonObject responseBody = gson.fromJson(response.body(), JsonObject.class);
    //     String textoResposta = responseBody.get("response").getAsString();
        
    //     // Tenta extrair a lista do JSON retornado pelo Ollama
    //     try {
    //         // O modelo pode retornar algo como '{ "files": ["a.pdf"] }' ou direto '["a.pdf"]'
    //         // Aqui assumimos que ele obedeceu e mandou array ou objeto simples.
    //         // Para robustez, idealmente use uma classe wrapper.
    //         if (textoResposta.trim().startsWith("[")) {
    //             return gson.fromJson(textoResposta, List.class);
    //         } else {
    //             // Se o modelo encapsulou num objeto, tente extrair (depende do modelo)
    //             System.out.println("Debug Resposta Crua: " + textoResposta); 
    //             return List.of(); // Fallback
    //         }
    //     } catch (Exception e) {
    //         System.err.println("Erro ao ler JSON do Ollama: " + e.getMessage());
    //         return List.of();
    //     }
    // }

    // Classe auxiliar simples
    static class DocumentoCandidato {
        String filename;
        String content;

        public DocumentoCandidato(String filename, String content) {
            this.filename = filename;
            this.content = content;
        }
    }
}