package com.ifmg.filesearch;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;

import java.io.File;
import java.io.IOException;

public class FileExtractor {

    // Instância estática do Tika (é thread-safe e pesada para recriar toda hora)
    private static final Tika tika = new Tika();

    public static String extractFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;

        File file = new File(filePath);
        if (!file.exists()) return "Erro: Arquivo não encontrado.";

        // Configura o Tika para não estourar a memória com arquivos gigantes
        tika.setMaxStringLength(100 * 1024); // Ex: Limite de 100KB de texto por arquivo

        try {
            // O método parseToString detecta automaticamente se é PDF, XLSX, PPTX, etc.
            System.out.println("Extraindo texto de: " + file.getName());
            return tika.parseToString(file);

        } catch (IOException | TikaException e) {
            System.err.println("Erro ao ler arquivo " + filePath + ": " + e.getMessage());
            return "Erro ao ler conteúdo do arquivo.";
        } catch (Throwable e) {
            // Captura erros de dependências ou memória (ex: PDF corrompido)
            e.printStackTrace();
            return "Formato não suportado ou arquivo corrompido.";
        }
    }
}