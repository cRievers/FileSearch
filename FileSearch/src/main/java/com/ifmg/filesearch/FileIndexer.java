package com.ifmg.filesearch;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class FileIndexer implements AutoCloseable {

    private final IndexWriter writer;
    private final ExecutorService executorService;
    private final AtomicInteger commitCounter;
    private static final int COMMIT_THRESHOLD = 200;

    // Callbacks opcionais para notificar progresso e conclusão
    private Consumer<Integer> progressCallback;
    private Runnable onCompleteCallback;

    // Configuração de extensões
    private final Set<String> whitelist = new HashSet<>();
    private final Set<String> blacklist = new HashSet<>();
    private final Set<String> dirBlacklist = new HashSet<>();

    // Contadores de diagnóstico
    private final AtomicInteger diagVisited      = new AtomicInteger(0);
    private final AtomicInteger diagValidExt     = new AtomicInteger(0);
    private final AtomicInteger diagSkippedOffline = new AtomicInteger(0);
    private final AtomicInteger diagSkippedUpsert = new AtomicInteger(0);
    private final AtomicInteger diagFailExtract  = new AtomicInteger(0);
    private final AtomicInteger diagIndexed      = new AtomicInteger(0);

    public void setProgressCallback(Consumer<Integer> callback) {
        this.progressCallback = callback;
    }

    public void setOnCompleteCallback(Runnable callback) {
        this.onCompleteCallback = callback;
    }

    public FileIndexer(String indexPath) throws IOException {
        Directory dir = FSDirectory.open(Paths.get(indexPath));
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        this.writer = new IndexWriter(dir, config);

        int cores = Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(cores);
        this.commitCounter = new AtomicInteger(0);
        
        loadConfiguration();
    }

    private void loadConfiguration() {
        Path configPath = Paths.get(System.getProperty("user.home"), ".filesearch", "indexer.properties");
        Properties props = new Properties();
        boolean needsSave = false;

        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Erro ao carregar configurações do indexador: " + e.getMessage());
            }
        } else {
            // Configuração inicial conforme solicitado: apenas ms office e fotos
            props.setProperty("whitelist", ".docx,.xlsx,.pptx,.png,.jpeg,.pdf,.md,.txt,.jpg");
            props.setProperty("blacklist", ".exe,.dll,.bin,.class,.jar,.iso,.sys,.tmp");
            needsSave = true;
        }

        if (props.getProperty("dirBlacklist") == null) {
            props.setProperty("dirBlacklist", "node_modules,target,build,dist,out,bin,obj,venv,env,vendor,__pycache__,tmp,temp,logs,coverage,appdata,program files,program files (x86),windows,$recycle.bin,system volume information,programdata");
            needsSave = true;
        }

        String whitelistProp = props.getProperty("whitelist", "");
        if (!whitelistProp.trim().isEmpty()) {
            for (String ext : whitelistProp.split(",")) {
                String cleanExt = ext.trim().toLowerCase();
                if (!cleanExt.startsWith(".")) cleanExt = "." + cleanExt;
                whitelist.add(cleanExt);
            }
        }

        String blacklistProp = props.getProperty("blacklist", "");
        if (!blacklistProp.trim().isEmpty()) {
            for (String ext : blacklistProp.split(",")) {
                String cleanExt = ext.trim().toLowerCase();
                if (!cleanExt.startsWith(".")) cleanExt = "." + cleanExt;
                blacklist.add(cleanExt);
            }
        }

        String dirBlacklistProp = props.getProperty("dirBlacklist", "");
        if (!dirBlacklistProp.trim().isEmpty()) {
            for (String dir : dirBlacklistProp.split(",")) {
                dirBlacklist.add(dir.trim().toLowerCase());
            }
        }

        if (needsSave) {
            try {
                Files.createDirectories(configPath.getParent());
                try (OutputStream out = Files.newOutputStream(configPath)) {
                    props.store(out, "Configuracao do Indexador.\n# Deixe a whitelist vazia para indexar todos os arquivos não listados na blacklist.\n# dirBlacklist: pastas que o indexador deve ignorar inteiramente.");
                }
            } catch (IOException e) {
                System.err.println("Erro ao salvar configuração padrão: " + e.getMessage());
            }
        }
    }

    public void indexDirectory(Path rootPath) throws IOException {
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName().toString();
                    if (name.startsWith(".") || dirBlacklist.contains(name.toLowerCase())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    diagVisited.incrementAndGet();
                    
                    if (isOfflineOrCloudFile(file)) {
                        diagSkippedOffline.incrementAndGet();
                        return FileVisitResult.CONTINUE;
                    }

                    if (isValidFile(file)) {
                        diagValidExt.incrementAndGet();
                        executorService.submit(() -> {
                            try {
                                indexFile(file, attrs);
                            } catch (Exception e) {
                                System.err.println("Erro ao processar arquivo: " + file + " - " + e.getMessage());
                            } catch (Throwable t) {
                                System.err.println("Erro fatal ao processar arquivo: " + file + " - " + t.getMessage());
                            }
                        });
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Silencia erros esperados: junction points e pastas protegidas do Windows
                    if (exc instanceof AccessDeniedException || exc instanceof FileSystemLoopException) {
                        return FileVisitResult.CONTINUE; // Ignora silenciosamente
                    }
                    System.err.println("Erro inesperado ao acessar: " + file + " - " + exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } finally {
            shutdownAndAwaitTermination();
            writer.commit();

            // Relatório de diagnóstico
            System.out.println("[Indexer] Relatório de diagnóstico:");
            System.out.println("  Arquivos visitados (total): " + diagVisited.get());
            System.out.println("  Extensão válida: " + diagValidExt.get());
            System.out.println("  Pulados (nuvem/offline): " + diagSkippedOffline.get());
            System.out.println("  Pulados (já no índice): " + diagSkippedUpsert.get());
            System.out.println("  Falha na extração: " + diagFailExtract.get());
            System.out.println("  Indexados com sucesso: " + diagIndexed.get());

            // Notifica conclusão após o commit final
            if (onCompleteCallback != null) {
                onCompleteCallback.run();
            }
        }
    }

    private void shutdownAndAwaitTermination() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.MINUTES)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private boolean isOfflineOrCloudFile(Path file) {
        try {
            Integer attrs = (Integer) Files.getAttribute(file, "dos:attributes");
            if (attrs != null) {
                // FILE_ATTRIBUTE_OFFLINE = 0x1000 (4096)
                // FILE_ATTRIBUTE_RECALL_ON_DATA_ACCESS = 0x400000 (4194304) (OneDrive On-Demand)
                if ((attrs & 0x1000) != 0 || (attrs & 0x400000) != 0) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignora se não puder ler os atributos DOS
        }
        return false;
    }

    private boolean isValidFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        int lastDotIndex = name.lastIndexOf('.');
        if (lastDotIndex == -1) return false; // Ignora arquivos sem extensão
        
        String extension = name.substring(lastDotIndex);
        
        if (!whitelist.isEmpty()) {
            return whitelist.contains(extension);
        }
        
        return !blacklist.contains(extension);
    }

    private void indexFile(Path file, BasicFileAttributes attrs) throws IOException {
        String id = file.toAbsolutePath().toString();
        long lastModified = attrs.lastModifiedTime().toMillis();

        // Verifica se precisamos atualizar o índice
        if (!shouldIndex(id, lastModified)) {
            diagSkippedUpsert.incrementAndGet();
            return;
        }

        String content = FileExtractor.extractFile(id);
        if (content == null || content.trim().isEmpty() || content.startsWith("Erro:")) {
            diagFailExtract.incrementAndGet();
            System.err.println("[Indexer] Extração falhou para: " + file.getFileName()
                + " | resultado: " + (content == null ? "null" : "'" + content.substring(0, Math.min(80, content.length())) + "'"));
            return;
        }

        Document doc = documentFactory(file, content, lastModified);
        writer.updateDocument(new Term("id", id), doc);
        diagIndexed.incrementAndGet();

        int count = commitCounter.incrementAndGet();
        if (count % COMMIT_THRESHOLD == 0) {
            writer.commit();
        }
        // Notifica progresso a cada arquivo indexado com sucesso
        if (progressCallback != null) {
            progressCallback.accept(diagIndexed.get());
        }
    }

    private boolean shouldIndex(String id, long lastModifiedOnDisk) {
        try (DirectoryReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TermQuery query = new TermQuery(new Term("id", id));
            TopDocs topDocs = searcher.search(query, 1);

            if (topDocs.totalHits.value > 0) {
                Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
                IndexableField modifiedField = doc.getField("modified");
                if (modifiedField != null) {
                    long lastModifiedInIndex = modifiedField.numericValue().longValue();
                    return lastModifiedOnDisk > lastModifiedInIndex;
                }
            }
        } catch (IOException e) {
            // Ignora se não puder ler, ou se não houver reader (índice novo)
        }
        return true;
    }

    private Document documentFactory(Path p, String content, long lastModified) {
        Document doc = new Document();

        doc.add(new StringField("id", p.toAbsolutePath().toString(), Field.Store.YES));
        doc.add(new TextField("filename", p.getFileName().toString(), Field.Store.YES));
        doc.add(new TextField("content", content, Field.Store.YES));
        doc.add(new LongPoint("modified", lastModified));
        doc.add(new StoredField("modified", lastModified));

        String extension = "";
        String filename = p.getFileName().toString();
        int i = filename.lastIndexOf('.');
        if (i > 0) {
            extension = filename.substring(i + 1).toLowerCase();
        }
        doc.add(new StringField("extension", extension, Field.Store.YES));

        return doc;
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        }
    }
}
