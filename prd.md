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


Este Documento de Requisitos de Produto (PRD) foca especificamente na "ponte" entre a varredura do sistema de arquivos e a preparação dos dados para o motor de busca Apache Lucene, utilizando a estrutura pré-existente no projeto `crievers/filesearch`[cite: 1].

---

## 📄 PRD: Integração FileIndexer ↔ FileExtractor

### 1. Visão Geral
A integração visa transformar arquivos brutos encontrados no disco em **Documentos Lucene** pesquisáveis. O `FileIndexer` atua como o orquestrador de fluxo (pipeline), enquanto o `FileExtractor` atua como o provedor de dados textuais[cite: 1].

### 2. Objetivos da Integração
*   **Extração Transparente:** Converter PDF, Word, TXT e Imagens (OCR) em strings de texto limpas para indexação[cite: 1].
*   **Consistência de Dados:** Garantir que metadados (caminho, data de modificação) estejam sincronizados com o conteúdo textual.
*   **Resiliência:** Impedir que um arquivo corrompido interrompa o processo de varredura total.

### 3. User Stories (Back-end)
*   **Extração em Fluxo:** Como sistema, quero enviar um `Path` para o `FileExtractor` e receber o conteúdo processado para alimentar o `IndexWriter`.
*   **Filtragem por Extensão:** Como sistema, quero que o `FileIndexer` ignore arquivos cujas extensões não sejam suportadas pelo `FileExtractor` antes de tentar abri-los.
*   **Rastreamento de Modificação:** Como usuário, quero que apenas arquivos alterados desde a última varredura sejam reprocessados para economizar bateria e CPU.

---

### 4. Fluxo de Trabalho Técnico (Workflow)

Abaixo, a sequência de operações planejada para o `FileIndexer.java` utilizando a estrutura de `crievers/filesearch`[cite: 1]:

| Etapa | Responsável | Ação Técnica |
| :--- | :--- | :--- |
| **1. Descoberta** | `FileIndexer` | Executa `Files.walkFileTree` para percorrer os diretórios[cite: 1]. |
| **2. Validação** | `FileIndexer` | Verifica se a extensão consta na lista de suporte (PDF, DOCX, TXT, etc.). |
| **3. Extração** | `FileExtractor` | Abre o arquivo, identifica o MIME type e extrai o texto puro[cite: 1]. |
| **4. Transformação** | `FileIndexer` | Cria um objeto `org.apache.lucene.document.Document`. |
| **5. Persistência** | `Lucene Engine` | O `IndexWriter` armazena o documento no índice local. |

---

### 5. Requisitos Funcionais

#### **5.1. Mapeamento de Campos (Lucene Schema)**
Para que a busca funcione conforme o objetivo (estilo ChatGPT/RAG), cada arquivo deve ser indexado com:
*   `id`: Caminho absoluto do arquivo (Chave Primária).
*   `content`: Conteúdo textual retornado pelo `FileExtractor`[cite: 1].
*   `filename`: Apenas o nome para buscas por título.
*   `modified`: Timestamp de última modificação (para indexação incremental).

#### **5.2. Tratamento de Erros e Exceções**
*   **Arquivos Protegidos:** Se o `FileExtractor` encontrar um PDF com senha, o `FileIndexer` deve logar o aviso e pular para o próximo arquivo sem travar o loop[cite: 1].
*   **Arquivos Vazios:** Arquivos sem texto extraível não devem ocupar espaço no índice.

---

### 6. Restrições Técnicas e Performance (Dicas de Sênior)

*   **Memory Management:** O `FileExtractor` deve processar arquivos grandes preferencialmente via *Streams*. Evite carregar um PDF de 500MB inteiro na memória RAM (Heap Space) antes de passar para o Lucene[cite: 1].
*   **Batch Commits:** Não execute `writer.commit()` para cada arquivo. Acumule documentos na memória e faça o commit a cada 100-500 arquivos indexados para otimizar o I/O do disco.
*   **Uso de IA (Gemma 3):** Nesta fase de indexação, a IA ainda não entra. O Lucene prepara o terreno. A Gemma 3 será utilizada na fase de **Busca e Resposta**, lendo o que o Lucene recuperou[cite: 1].

### 7. Roadmap de Implementação da Integração

1.  **Semana 1:** Implementar o `SimpleFileVisitor` no `FileIndexer` e conectar ao `IndexWriter`.
2.  **Semana 1 (Fim):** Criar o método `documentFactory(Path p)` que chama o `FileExtractor.extract(p)` e monta o documento Lucene[cite: 1].
3.  **Semana 2:** Adicionar lógica de "Upsert" (Update ou Insert) baseada no campo `modified` para evitar re-indexação desnecessária.
