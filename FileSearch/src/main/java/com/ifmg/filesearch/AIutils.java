package com.ifmg.filesearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class AIutils {

    // Configurações Locais e do Modelo (Seguindo a documentação local)
    private static final String OLLAMA_API_URL = "http://localhost:11434/api/generate";
    private static final String DEFAULT_MODEL = "gemma3:4b";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AIutils() {
        this.httpClient = HttpClient.newBuilder().build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Envia uma requisição HTTP POST para o endpoint /api/generate do Ollama.
     * 
     * @param prompt O prompt ou a pergunta a ser enviada ao modelo.
     * @return O texto de resposta da IA ou uma mensagem de erro.
     */
    public String generate(String prompt) {
        return generate(DEFAULT_MODEL, prompt);
    }

    public String generate(String model, String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            return "Erro: Prompt não pode ser vazio.";
        }

        // Constrói o DTO que será serializado: {"model": "...", "prompt": "...",
        // "stream": false}
        OllamaRequest ollamaRequest = new OllamaRequest(model, prompt);
        ollamaRequest.setContextSize(16384); // Ajuste conforme necessário

        try {
            // 1. Serializa o objeto Java para o JSON exigido pelo Ollama
            String requestBody = objectMapper.writeValueAsString(ollamaRequest);

            // 2. Constrói a requisição HTTP (POST para o endereço local)
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_API_URL))
                    .header("Content-Type", "application/json") // Essencial para o Ollama
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // 3. Envia a requisição e obtém a resposta
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 4. Trata erros de status HTTP
            if (response.statusCode() != 200) {
                System.err.println("Erro Ollama. Status: " + response.statusCode() + ". Corpo: " + response.body());
                return "Erro de comunicação com o servidor Ollama (Status: " + response.statusCode() + ").";
            }

            // 5. Desserializa o JSON de resposta para o DTO
            OllamaResponse ollamaResponse = objectMapper.readValue(response.body(), OllamaResponse.class);

            // 6. Retorna o texto gerado
            return ollamaResponse.response != null ? ollamaResponse.response : "Resposta da IA veio vazia.";

        } catch (IOException e) {
            // Captura erros de rede ou de serialização/desserialização (ex: Ollama não está
            // rodando)
            System.err.println("Erro de I/O (rede ou JSON): Verifique se o Ollama está rodando em " + OLLAMA_API_URL);
            e.printStackTrace();
            return "Erro de I/O: Verifique se o servidor Ollama está acessível em localhost:11434.";
        } catch (InterruptedException e) {
            // Captura interrupções de thread
            Thread.currentThread().interrupt();
            e.printStackTrace();
            return "Erro: A requisição foi interrompida.";
        }
    }

    public String generatePrompt(List<Integer> matchedIndexes, String[] filesContents, String description) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(
                "Você é um buscador de arquivos inteligente. Com base nos seguintes conteúdos de arquivos:\n\n");

        for (int index : matchedIndexes) {
            promptBuilder.append("Conteúdo do arquivo ").append(index).append(":\n");
            promptBuilder.append(filesContents[index]).append("\n\n");
        }

        promptBuilder.append("E de acordo com a seguinte descrição da busca: ").append(description).append("\n");
        promptBuilder.append(
                "Ranqueie os índices dos arquivos que mais se encaixam na descrição fornecida. Responda APENAS com os índices separados por vírgulas. Se nenhum arquivo for relevante, responda com -1.\n");

        String prompt = promptBuilder.toString();
        return prompt;
    }

    public void ensureOllamaServerIsRunning() {
        if (!isOllamaRunning()) {
            System.out.println("Ollama não detectado. Iniciando servidor...");
            startOllamaServer();

            // Aguarda alguns segundos para o servidor subir
            try {
                System.out.println("Aguardando inicialização do Ollama...");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            System.out.println("Servidor Ollama já está rodando.");
        }
    }

    private boolean isOllamaRunning() {
        try {
            // Tenta fazer um ping simples na raiz do servidor
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434")) // URL base do Ollama
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void startOllamaServer() {
        try {
            // Comando para iniciar o servidor. No Windows, 'ollama serve' funciona se
            // estiver no PATH.
            ProcessBuilder builder = new ProcessBuilder("ollama", "serve");
            builder.redirectErrorStream(true); // Redireciona erros para o output padrão

            // Inicia o processo em background
            Process process = builder.start();

            // Opcional: Ler a saída do processo em uma thread separada para debug (similar
            // ao FileFinder)
            // Mas como 'serve' roda continuamente, não devemos usar 'waitFor()' aqui.
            System.out.println("Comando de inicialização enviado.");

        } catch (IOException e) {
            System.err.println("Falha ao tentar iniciar o Ollama automaticamente: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        AIutils aiutils = new AIutils();
        String prompt = "Quais são as cores RGB?";
        String resposta = aiutils.generate(prompt);
        System.out.println("Resposta da IA: " + resposta);
    }
}
