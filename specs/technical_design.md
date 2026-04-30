# Planejamento Técnico - FileIndexer

Este documento detalha a implementação do `FileIndexer.java` e estratégias de performance.

## 1. Estratégia de Varredura
Utilizaremos o `Files.walkFileTree` que implementa o padrão *Visitor*, sendo performático para grandes árvores de diretórios.

### Estratégia Técnica
* **Pattern:** `SimpleFileVisitor<Path>`.
* **Motor de Busca:** Apache Lucene 9.x.
* **Campos do Lucene:**
    * `path`: (StringField) Caminho completo (ID único, não tokenizado).
    * `filename`: (TextField) Nome do arquivo.
    * `content`: (TextField) Conteúdo extraído.
    * `extension`: (StringField) Filtros rápidos.
    * `lastModified`: (LongPoint) Indexação incremental.

## 2. Arquitetura do FileIndexer
O indexador orquestra a descoberta e decide o que indexar.

```java
public class FileIndexer {
    private final IndexWriter writer;
    private final FileExtractor extractor;

    public void indexDirectory(Path rootPath) throws IOException {
        Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
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
                    indexFile(file, attrs);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.err.println("Erro ao acessar: " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void indexFile(Path file, BasicFileAttributes attrs) {
        // 1. Extrair texto usando o FileExtractor
        // 2. Criar Document do Lucene
        // 3. writer.updateDocument(new Term("path", path), doc)
    }
}
```

## 3. Considerações de Performance
1. **Indexação Incremental:** Use `writer.updateDocument` com o campo `path` como chave.
2. **Multithreading:** Use `ExecutorService` para extração de conteúdo (CPU-intensive) enquanto a thread principal varre o disco (I/O).
3. **Limite de Memória:** Defina limites para arquivos gigantes (ex: 50MB).
4. **Try-with-resources:** Garanta o fechamento do `IndexWriter` para evitar corrupção.
