Estrutura que há: `AIutils`, `FileExtractor`, `FileSystemService` e integração com JavaFX)
Organização em camadas: Controller, Service, Utils.

Abaixo, apresento o planejamento macro (PRD) e o detalhamento técnico para a implementação do `FileIndexer`.

---

### 1. Documento de Requisitos de Produto (PRD) - FileSearch

**Objetivo:** Criar uma ferramenta desktop multiplataforma para busca semântica e técnica em arquivos locais, garantindo 100% de privacidade através de processamento offline (Lucene + Gemma 3).

#### **User Stories (Épicos)**
* **Busca por Linguagem Natural:** "Como usuário, quero descrever um assunto (ex: 'contrato de aluguel de 2023') e encontrar o arquivo mesmo sem saber o nome exato."
* **Filtragem Técnica:** "Como usuário, quero restringir minha busca a extensões específicas (.pdf, .java) para acelerar o resultado."
* **Extração de Respostas (RAG):** "Como usuário, quero que a IA leia os arquivos encontrados e me dê uma resposta direta baseada no conteúdo deles."
* **Indexação Transparente:** "Como usuário, quero que o sistema mapeie meus arquivos em segundo plano sem travar meu computador."

#### **Restrições e Escopo**
* **Privacidade:** Nenhum dado ou metadado pode sair da máquina do usuário.
* **Performance:** A indexação deve ser eficiente em termos de I/O e memória.
* **Extensibilidade:** O sistema deve suportar novos formatos de arquivo via plugins ou bibliotecas de extração (Apache Tika).
* **Multiplataforma:** Deve rodar consistentemente em Windows, Linux e macOS.

#### **Roadmap Sugerido**
1.  **Fase 1 (Atual):** Motor de Indexação (Lucene) e Varredura Nativa (`walkFileTree`).
2.  **Fase 2:** Extração de conteúdo multiformato (PDF, Docx) e OCR básico para imagens.
3.  **Fase 3:** Integração com Ollama (Gemma 3) para interface de chat/RAG.
4.  **Fase 4:** Interface Gráfica avançada e monitoramento de mudanças no sistema de arquivos (WatchService).

---

### 2. Planejamento do `FileIndexer.java`

Para um desenvolvedor sênior, a palavra-chave aqui é **Eficiência**. Usar o `Files.walkFileTree` é a escolha correta porque ele implementa o padrão *Visitor*, que é muito mais performático que o `Files.list()` para grandes árvores de diretórios.



#### **Estratégia Técnica**
* **Pattern:** Utilizaremos o `SimpleFileVisitor<Path>`.
* **Motor de Busca:** Apache Lucene 9.x (ou versão atual no seu `pom.xml`).
* **Campos do Lucene:**
    * `path`: (StringField) Caminho completo (ID único, não tokenizado).
    * `filename`: (TextField) Nome do arquivo (tokenizado para busca parcial).
    * `content`: (TextField) Conteúdo extraído (armazenado ou apenas indexado).
    * `extension`: (StringField) Para filtros rápidos.
    * `lastModified`: (LongPoint) Para controle de versão e re-indexação incremental.

#### **Arquitetura do FileIndexer**
O indexador não deve apenas "andar" nas pastas; ele deve decidir o que merece ser indexado.

```java
// Esboço da lógica Senior para o FileIndexer
public class FileIndexer {
    private final IndexWriter writer;
    private final FileExtractor extractor;

    public void indexDirectory(Path rootPath) throws IOException {
        Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // Ignore pastas de sistema ou pesadas que não interessam
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
                // Log de erro de permissão, mas continua a varredura
                System.err.println("Erro ao acessar: " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void indexFile(Path file, BasicFileAttributes attrs) {
        // 1. Extrair texto usando o seu FileExtractor
        // 2. Criar Document do Lucene
        // 3. writer.updateDocument(new Term("path", path), doc) -> Garante que não duplique
    }
}
```

### 3. Considerações de Performance (Dica Sênior)

1.  **Indexação Incremental:** Não apague o índice toda vez. Use `writer.updateDocument`. O Lucene usará o campo `path` para substituir a versão antiga pela nova se o arquivo foi modificado.
2.  **Multithreading:** O `walkFileTree` é single-threaded por natureza de I/O de disco. No entanto, a **extração de conteúdo** (especialmente PDF e OCR) é CPU-intensive. Use um `ExecutorService` (FixedThreadPool) para processar o conteúdo dos arquivos em paralelo enquanto a thread principal continua varrendo o disco.
3.  **Análise de Arquivos Grandes:** Defina um limite de tamanho (ex: 50MB) para evitar estourar a memória (Heap Space) ao ler arquivos de texto gigantes.
4.  **Try-with-resources:** Garanta que o `IndexWriter` seja fechado corretamente para evitar corrupção do índice (`lock` do Lucene).
