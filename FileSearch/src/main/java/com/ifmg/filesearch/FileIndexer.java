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

public class FileIndexer implements AutoCloseable {

    private final IndexWriter writer;
    private final ExecutorService executorService;
    private final AtomicInteger commitCounter;
    private static final int COMMIT_THRESHOLD = 200;

    public FileIndexer(String indexPath) throws IOException {
        Directory dir = FSDirectory.open(Paths.get(indexPath));
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        this.writer = new IndexWriter(dir, config);

        int cores = Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(cores);
        this.commitCounter = new AtomicInteger(0);
    }

    public void indexDirectory(Path rootPath) throws IOException {
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName().toString();
                    if (name.startsWith(".") || name.equals("node_modules") || name.equals("target")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isValidFile(file)) {
                        executorService.submit(() -> {
                            try {
                                indexFile(file, attrs);
                            } catch (Exception e) {
                                System.err.println("Erro ao processar arquivo: " + file + " - " + e.getMessage());
                            }
                        });
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    System.err.println("Erro ao acessar: " + file + " - " + exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } finally {
            shutdownAndAwaitTermination();
            writer.commit();
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

    private boolean isValidFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".txt") || name.endsWith(".pdf") ||
                name.endsWith(".docx") || name.endsWith(".log") ||
                name.endsWith(".png") || name.endsWith(".jpg") ||
                name.endsWith(".jpeg") || name.endsWith(".bmp") || name.endsWith(".tiff");
    }

    private void indexFile(Path file, BasicFileAttributes attrs) throws IOException {
        String id = file.toAbsolutePath().toString();
        long lastModified = attrs.lastModifiedTime().toMillis();

        // Verifica se precisamos atualizar o índice
        if (!shouldIndex(id, lastModified)) {
            return;
        }

        String content = FileExtractor.extractFile(id);
        if (content == null || content.trim().isEmpty() || content.startsWith("Erro:")) {
            return;
        }

        Document doc = documentFactory(file, content, lastModified);
        writer.updateDocument(new Term("id", id), doc);

        if (commitCounter.incrementAndGet() % COMMIT_THRESHOLD == 0) {
            writer.commit();
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
