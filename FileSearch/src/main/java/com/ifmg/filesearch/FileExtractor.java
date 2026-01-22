package com.ifmg.filesearch;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class FileExtractor {

    // Instância estática do Tika (é thread-safe e pesada para recriar toda hora)
    private static final Tika tika = new Tika();

    public static String extrairTextoImagem(String caminhoArquivo) {
        File imageFile = new File(caminhoArquivo);

        // 1. Valida existência
        if (!imageFile.exists()) {
            return "Erro: Arquivo não encontrado em " + imageFile.getAbsolutePath();
        }

        // 2. Valida conteúdo
        if (imageFile.length() == 0) {
            return "Erro: O arquivo está vazio (0 bytes).";
        }

        ITesseract instance = new Tesseract();

        // IMPORTANTE: Configure o caminho dos dados (tessdata)
        instance.setDatapath("src/main/resources/tessdata");
        instance.setLanguage("por");

        try {
            // 3. Executa o OCR
            return instance.doOCR(imageFile);
        } catch (TesseractException e) {
            return "Erro ao processar OCR: " + e.getMessage();
        }
    }

    public static String extractFile(String filePath) {
        if (filePath == null || filePath.isEmpty())
            return null;

        File file = new File(filePath);
        if (!file.exists())
            return "Erro: Arquivo não encontrado.";

        // Configura o Tika para não estourar a memória com arquivos gigantes
        tika.setMaxStringLength(100 * 1024); // Ex: Limite de 100KB de texto por arquivo

        try {
            // O método parseToString detecta automaticamente se é PDF, XLSX, PPTX, etc.
            System.out.println("Extraindo texto de: " + file.getName());
            if(file.getName().toLowerCase().endsWith(".png") ||
               file.getName().toLowerCase().endsWith(".jpg") ||
               file.getName().toLowerCase().endsWith(".jpeg") ||
               file.getName().toLowerCase().endsWith(".bmp") ||
               file.getName().toLowerCase().endsWith(".tiff")) {
                // Se for uma imagem, usar o OCR
                return extrairTextoImagem(filePath);
            }
            else return tika.parseToString(file);

        } catch (IOException | TikaException e) {
            System.err.println("Erro ao ler arquivo " + filePath + ": " + e.getMessage());
            return "Erro ao ler conteúdo do arquivo.";
        } catch (Throwable e) {
            // Captura erros de dependências ou memória (ex: PDF corrompido)
            e.printStackTrace();
            return "Formato não suportado ou arquivo corrompido.";
        }
    }

    public static void main(String[] args) {
        System.out.println("--- Iniciando Teste do FileFinder + FileExtractor ---");

        // 1. Configurar parâmetros de busca (Simulando a UI)
        // String pastaTeste = "C:\\Users\\SEU_USUARIO\\Documents"; // <--- ALTERE AQUI
        // PARA UMA PASTA REAL
        // List<String> tipos = Arrays.asList(".txt", ".pdf", ".docx", ".log");

        // // A descrição aqui não afeta a busca do PowerShell (ela é usada depois na
        // IA),
        // // mas precisamos passar para o construtor.
        // String termoBusca = "teste";

        // System.out.println("Buscando na pasta: " + pastaTeste);

        // 2. Inicializar o FileFinder (configura as variáveis estáticas)
        // new FileFinder(termoBusca, tipos, pastaTeste);

        // 3. Executar a busca (PowerShell)
        // long inicio = System.currentTimeMillis();
        String[] caminhosEncontrados = "C:\\Users\\Caio Rievers Duarte\\Pictures\\Saved Pictures\\Merge_x_Quick\\comparisions.png"
                .split(";"); // <--- ALTERE AQUI PARA SIMULAR ARQUIVOS ENCONTRADOS
        // long fim = System.currentTimeMillis();

        // System.out.println("Busca concluída em " + (fim - inicio) + "ms");
        System.out.println("Arquivos encontrados: " + caminhosEncontrados.length);

        // 4. Demonstrar a extração de texto de um arquivo específico (o "arquivo x")
        if (caminhosEncontrados.length > 0) {
            // Pega o primeiro arquivo encontrado como exemplo
            String arquivoX = caminhosEncontrados[0];

            System.out.println("\n--- Demonstrando Extração do Arquivo X ---");
            System.out.println("Arquivo: " + arquivoX);

            // Chama o FileExtractor (que agora usa Tika)
            // Certifique-se de que o FileExtractor.java está atualizado com o código Tika
            String conteudo = FileExtractor.extrairTextoImagem(arquivoX);

            System.out.println("--> Conteúdo Extraído:");
            if (conteudo != null) {
                System.out.println(conteudo);
            } else {
                System.out.println("[Erro ou Conteúdo Vazio]");
            }
        } else {
            System.out.println("Nenhum arquivo encontrado para testar a extração.");
        }
    }
}