package com.ifmg.filesearch;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class FileExtractorTest {

    // Variáveis estáticas para os arquivos temporários
    private static File tempTxtFile;
    private static File tempDocxFile;
    private static final String TEMP_TXT_CONTENT = "Linha 1 de teste.\r\nLinha 2 de teste.\r\n";

    // O conteúdo DOCX simulado é mais complicado, mas testamos a função de leitura
    private static final String TEMP_DOCX_PATH = "temp_test_file.docx";

    // --- Configuração: Criar arquivos temporários ---
    @BeforeAll
    static void setUp() throws IOException {
        // 1. Cria um arquivo .TXT temporário
        tempTxtFile = File.createTempFile("testFile", ".txt");
        try (FileWriter writer = new FileWriter(tempTxtFile)) {
            writer.write(TEMP_TXT_CONTENT.replace("\r\n", System.lineSeparator()));
        }

        // 2. Cria um arquivo DOCX (A criação de um DOCX real é complexa para unit test.
        //    Aqui, apenas garantimos que o caminho existe para testar o fluxo de erro ou o sucesso simulado.)
        //    Em um teste real, você usaria uma biblioteca para criar o DOCX com conteúdo.
        //    Para este exemplo, vamos focar no TXT e nos casos de erro/limite.
    }

    // --- Limpeza: Excluir arquivos temporários ---
    @AfterAll
    static void tearDown() {
        if (tempTxtFile != null) {
            tempTxtFile.delete();
        }
        // Excluiria o DOCX também, se criado
    }

    @Test
    void testExtractTxt_Success() {
        System.out.println("TESTE: testExtractTxt_Success");
        String content = FileExtractor.extractFile(tempTxtFile.getAbsolutePath());
        // O conteúdo deve corresponder, ignorando o último separador de linha do StringBuilder
        assertTrue(content.contains("Linha 1 de teste."), "O conteúdo deve ser lido corretamente.");
    }

    @Test
    void testExtractFile_UnsupportedType() {
        System.out.println("TESTE: testExtractFile_UnsupportedType");
        String result = FileExtractor.extractFile("file.pdf"); // .pdf não é suportado na sua implementação
        assertEquals("Unsupported file type", result, "Deve retornar 'Unsupported file type' para PDF.");
    }

    @Test
    void testExtractFile_InvalidPath() {
        System.out.println("TESTE: testExtractFile_InvalidPath");
        String result = FileExtractor.extractFile("/caminho/nao/existente/arquivo.txt");
        // O método readTxt imprime um erro no console, mas retorna null
        assertNull(result, "Deve retornar null para um arquivo não encontrado.");
    }

    @Test
    void testExtractFile_NullOrEmptyPath() {
        System.out.println("TESTE: testExtractFile_NullOrEmptyPath");
        assertNull(FileExtractor.extractFile(null), "Deve retornar null para caminho nulo.");
        assertNull(FileExtractor.extractFile(""), "Deve retornar null para caminho vazio.");
    }
}