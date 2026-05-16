# Planejamento Técnico - FileIndexer

Este documento detalha a implementação do `FileIndexer.java` e as estratégias de performance para a indexação de arquivos locais.

## 1. Estratégia de Varredura
Utilizaremos o `Files.walkFileTree`, que implementa o padrão *Visitor*. Esta é a escolha mais performática para grandes árvores de diretórios, superando o `Files.list()` em termos de eficiência de I/O.

### Detalhes Técnicos
* **Pattern:** `SimpleFileVisitor<Path>`.
* **Motor de Busca:** Apache Lucene 9.x.
* **Workflow de Indexação:**
    1. **Descoberta:** `FileIndexer` percorre diretórios via `walkFileTree`.
    2. **Validação:** Filtra extensões suportadas (PDF, DOCX, TXT, etc.).
    3. **Extração:** `FileExtractor` obtém o texto puro e metadados.
    4. **Transformação:** Conversão para `org.apache.lucene.document.Document`.
    5. **Persistência:** `IndexWriter` armazena o documento de forma incremental.

## 2. Arquitetura e Lucene Schema
O indexador orquestra a descoberta e decide o que indexar, mapeando os arquivos para o seguinte esquema:

| Campo | Tipo Lucene | Descrição |
| :--- | :--- | :--- |
| `id` | `StringField` | Caminho absoluto (Chave Primária, não tokenizado). |
| `filename` | `TextField` | Nome do arquivo para buscas parciais por título. |
| `content` | `TextField` | Conteúdo textual extraído (tokenizado). |
| `extension` | `StringField` | Extensão para filtragem rápida. |
| `modified` | `LongPoint` | Timestamp de última modificação para indexação incremental. |

### Esboço da Implementação
```java
public class FileIndexer {
    private final IndexWriter writer;
    private final FileExtractor extractor;

    public void indexDirectory(Path rootPath) throws IOException {
        Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName().toString();
                // Ignorar pastas ocultas ou de sistema
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
        // 3. writer.updateDocument(new Term("id", path), doc) -> Upsert
    }
}
```

## 3. Considerações de Performance e Resiliência
1. **Indexação Incremental (Upsert):** Use `writer.updateDocument` com o campo `id` como chave. Compare o timestamp `modified` no disco com o do índice para evitar reprocessamento desnecessário.
2. **Multithreading:** Utilize um `ExecutorService` (FixedThreadPool) para a extração de conteúdo (CPU-intensive), enquanto a thread principal continua a varredura do disco (I/O-intensive).
3. **Batch Commits:** Não execute `writer.commit()` para cada arquivo. Acumule documentos e faça o commit a cada 100-500 arquivos para otimizar o I/O.
4. **Gerenciamento de Memória:** 
    - O `FileExtractor` deve usar *Streams* para processar arquivos grandes (ex: > 50MB).
    - Evite carregar conteúdos gigantes inteiros na Heap Space.
5. **Tratamento de Erros:**
    - **Arquivos Protegidos:** Se um PDF tiver senha, logue o aviso e pule para o próximo.
    - **Arquivos Vazios:** Não devem ser indexados para economizar espaço.
    - **Try-with-resources:** Sempre garanta o fechamento do `IndexWriter` para evitar corrupção do índice (`write.lock`).

