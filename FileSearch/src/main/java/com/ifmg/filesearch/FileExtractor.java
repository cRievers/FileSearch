package com.ifmg.filesearch;

// Tika (usado apenas para PDF como fallback; pode falhar em ambientes JPMS)
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

// Apache POI (extração nativa de Office — sem ServiceLoader, JPMS-safe)
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

public class FileExtractor {

    private static final int MAX_TEXT_CHARS = 100 * 1024; // 100 KB por arquivo

    // AutoDetectParser é inicializado em bloco estático protegido:
    // new AutoDetectParser() pode falhar em ambientes JPMS (ServiceLoader não encontra parsers
    // de módulos automáticos inválidos como tika-parsers-standard-package).
    // Nesse caso TIKA_PARSER será null e PDFs serão pulados graciosamente.
    private static final AutoDetectParser TIKA_PARSER;
    static {
        AutoDetectParser parser = null;
        try {
            parser = new AutoDetectParser();
            System.out.println("[FileExtractor] AutoDetectParser inicializado com sucesso.");
        } catch (Throwable e) {
            System.err.println("[FileExtractor] AutoDetectParser indisponível (JPMS): " + e.getMessage()
                + " - PDFs serão pulados. Use POI para Office files.");
        }
        TIKA_PARSER = parser;
    }

    // Resolve o caminho do tessdata em um local ASCII-safe
    // Tesseract (C nativo via JNA) não suporta caracteres não-ASCII em caminhos no Windows.
    // Copia os .traineddata para ~/.filesearch/tessdata/ para garantir compatibilidade.
    private static final String[] TRAINEDDATA_FILES = { "por.traineddata", "eng.traineddata" };
    private static final String TESSDATA_PATH = resolveTessdataPath();

    private static String resolveTessdataPath() {
        Path safeTessdataDir = Paths.get(System.getProperty("user.home"), ".filesearch", "tessdata");

        try {
            Files.createDirectories(safeTessdataDir);

            for (String fileName : TRAINEDDATA_FILES) {
                Path destFile = safeTessdataDir.resolve(fileName);
                if (!Files.exists(destFile)) {
                    boolean copied = false;

                    // Estratégia 1: Copiar via classpath (funciona fora de JPMS)
                    try (InputStream is = FileExtractor.class.getResourceAsStream("/tessdata/" + fileName)) {
                        if (is != null) {
                            Files.copy(is, destFile, StandardCopyOption.REPLACE_EXISTING);
                            System.out.println("Tessdata copiado (classpath): " + fileName + " -> " + destFile);
                            copied = true;
                        }
                    }

                    // Estratégia 2: Copiar diretamente via caminho no disco (JPMS-safe)
                    // Java NIO lida com Unicode; apenas o Tesseract nativo não consegue
                    if (!copied) {
                        try {
                            URL classUrl = FileExtractor.class.getResource("FileExtractor.class");
                            if (classUrl != null) {
                                Path classPath = Paths.get(classUrl.toURI());
                                // target/classes/com/ifmg/filesearch/FileExtractor.class -> target/classes/
                                Path classesRoot = classPath.getParent().getParent().getParent().getParent();
                                Path sourceFile = classesRoot.resolve("tessdata").resolve(fileName);
                                if (Files.exists(sourceFile)) {
                                    Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
                                    System.out.println("Tessdata copiado (disco): " + fileName + " -> " + destFile);
                                    copied = true;
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Aviso: Falha ao copiar " + fileName + " via disco: " + e.getMessage());
                        }
                    }

                    if (!copied) {
                        System.err.println("ERRO: Não foi possível copiar " + fileName + " para " + destFile);
                    }
                }
            }

            String path = safeTessdataDir.toAbsolutePath().toString();
            System.out.println("Tessdata resolvido em (ASCII-safe): " + path);
            return path;

        } catch (IOException e) {
            System.err.println("Erro ao preparar tessdata: " + e.getMessage());
            e.printStackTrace();
        }

        // Fallback (vai falhar com acentos, mas pelo menos tenta)
        return "src/main/resources/tessdata";
    }

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

        try {
            ITesseract instance = new Tesseract();

            // Usa o caminho absoluto resolvido via classpath
            instance.setDatapath(TESSDATA_PATH);
            instance.setLanguage("por");

            // 3. Executa o OCR
            return instance.doOCR(imageFile);
        } catch (TesseractException e) {
            return "Erro ao processar OCR: " + e.getMessage();
        } catch (Throwable e) {
            // Captura erros nativos (Invalid memory access) que podem ocorrer com Tesseract/JNA
            System.err.println("Erro fatal no OCR para " + caminhoArquivo + ": " + e.getMessage());
            return "Erro: OCR indisponível para este arquivo.";
        }
    }

    public static String extractFile(String filePath) {
        if (filePath == null || filePath.isEmpty())
            return null;

        File file = new File(filePath);
        if (!file.exists())
            return "Erro: Arquivo não encontrado.";
        if (file.length() == 0)
            return null; // arquivo vazio, pular

        String name = file.getName().toLowerCase();
        System.out.println("Extraindo texto de: " + file.getName());

        // --- Imagens: OCR via Tesseract ---
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".bmp") || name.endsWith(".tiff")) {
            return extrairTextoImagem(filePath);
        }

        // --- TXT / LOG: leitura direta (rápida, sem dependência de parsers) ---
        if (name.endsWith(".txt") || name.endsWith(".log")) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                if (content.length() > MAX_TEXT_CHARS) content = content.substring(0, MAX_TEXT_CHARS);
                return content;
            } catch (IOException e) {
                // Tenta UTF-16 ou Latin-1 como fallback
                try {
                    String content = Files.readString(file.toPath(), StandardCharsets.ISO_8859_1);
                    if (content.length() > MAX_TEXT_CHARS) content = content.substring(0, MAX_TEXT_CHARS);
                    return content;
                } catch (IOException e2) {
                    System.err.println("Erro ao ler TXT: " + filePath + " - " + e2.getMessage());
                    return "Erro: não foi possível ler o arquivo de texto.";
                }
            }
        }

        // --- DOCX: Apache POI (sem ServiceLoader, JPMS-safe) ---
        if (name.endsWith(".docx")) {
            try (FileInputStream fis = new FileInputStream(file);
                 XWPFDocument doc = new XWPFDocument(fis)) {
                StringBuilder sb = new StringBuilder();
                
                // 1. Parágrafos regulares
                for (XWPFParagraph para : doc.getParagraphs()) {
                    sb.append(para.getText()).append("\n");
                }
                
                // 2. Tabelas
                for (XWPFTable table : doc.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            sb.append(cell.getText()).append(" ");
                        }
                        sb.append("\n");
                    }
                }

                // 3. Imagens (OCR)
                for (XWPFPictureData pic : doc.getAllPictures()) {
                    try {
                        String ext = pic.suggestFileExtension();
                        if (ext.equals("png") || ext.equals("jpeg") || ext.equals("jpg") || ext.equals("bmp") || ext.equals("tiff")) {
                            Path tempImg = Files.createTempFile("docx_img_", "." + ext);
                            Files.write(tempImg, pic.getData());
                            String ocrText = extrairTextoImagem(tempImg.toAbsolutePath().toString());
                            if (ocrText != null && !ocrText.startsWith("Erro")) {
                                sb.append(ocrText).append("\n");
                            }
                            Files.deleteIfExists(tempImg);
                        }
                    } catch (Exception e) {
                        System.err.println("Aviso: Falha ao processar OCR em imagem do DOCX: " + e.getMessage());
                    }
                }

                String result = sb.toString().trim();
                return result.isEmpty() ? null : result;
            } catch (Exception e) {
                System.err.println("Erro POI DOCX " + file.getName() + ": " + e.getMessage());
                return "Erro: falha ao ler DOCX.";
            }
        }

        // --- XLSX: Apache POI ---
        if (name.endsWith(".xlsx")) {
            try (FileInputStream fis = new FileInputStream(file);
                 var wb = WorkbookFactory.create(fis)) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    for (Row row : wb.getSheetAt(i)) {
                        for (Cell cell : row) {
                            sb.append(cell.toString()).append(" ");
                        }
                        sb.append("\n");
                    }
                }
                String result = sb.toString().trim();
                return result.isEmpty() ? null : result;
            } catch (Exception e) {
                System.err.println("Erro POI XLSX " + file.getName() + ": " + e.getMessage());
                return "Erro: falha ao ler XLSX.";
            }
        }

        // --- PPTX: Apache POI ---
        if (name.endsWith(".pptx")) {
            try (FileInputStream fis = new FileInputStream(file);
                 XMLSlideShow ppt = new XMLSlideShow(fis)) {
                StringBuilder sb = new StringBuilder();
                for (XSLFSlide slide : ppt.getSlides()) {
                    for (XSLFShape shape : slide.getShapes()) {
                        if (shape instanceof XSLFTextShape ts) {
                            sb.append(ts.getText()).append("\n");
                        }
                    }
                }
                String result = sb.toString().trim();
                return result.isEmpty() ? null : result;
            } catch (Exception e) {
                System.err.println("Erro POI PPTX " + file.getName() + ": " + e.getMessage());
                return "Erro: falha ao ler PPTX.";
            }
        }

        // --- PDF: Tika AutoDetectParser (se disponível) ---
        if (name.endsWith(".pdf")) {
            if (TIKA_PARSER == null) {
                System.err.println("[FileExtractor] PDF pulado (Tika não inicializado): " + file.getName());
                return null;
            }
            try (InputStream stream = new FileInputStream(file)) {
                BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_CHARS);
                Metadata metadata = new Metadata();
                ParseContext context = new ParseContext();

                TIKA_PARSER.parse(stream, handler, metadata, context);
                String result = handler.toString().trim();
                return result.isEmpty() ? null : result;

            } catch (org.xml.sax.SAXException e) {
                System.out.println("Limite de texto atingido para " + file.getName() + " (parcial OK)");
                return "[conteúdo PDF truncado]";
            } catch (TikaException | IOException e) {
                System.err.println("Erro Tika PDF " + file.getName() + ": " + e.getMessage());
                return "Erro: falha na extração do PDF.";
            } catch (Throwable e) {
                System.err.println("Erro fatal PDF " + file.getName() + ": " + e.getMessage());
                return "Erro: falha inesperada no PDF.";
            }
        }

        System.err.println("[FileExtractor] Tipo de arquivo não suportado: " + file.getName());
        return null;
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