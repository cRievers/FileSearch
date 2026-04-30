# PRD: Integração FileIndexer ↔ FileExtractor

Este documento foca especificamente na "ponte" entre a varredura do sistema de arquivos e a preparação dos dados para o motor de busca Apache Lucene.

## 1. Visão Geral
A integração visa transformar arquivos brutos encontrados no disco em **Documentos Lucene** pesquisáveis. O `FileIndexer` atua como o orquestrador de fluxo (pipeline), enquanto o `FileExtractor` atua como o provedor de dados textuais.

## 2. Objetivos da Integração
* **Extração Transparente:** Converter PDF, Word, TXT e Imagens (OCR) em strings de texto limpas.
* **Consistência de Dados:** Sincronizar metadados (caminho, data) com o conteúdo.
* **Resiliência:** Impedir que arquivos corrompidos parem o processo.

## 3. User Stories (Back-end)
* **Extração em Fluxo:** Sistema envia `Path` e recebe texto para o `IndexWriter`.
* **Filtragem por Extensão:** Ignorar arquivos não suportados antes de tentar abrir.
* **Rastreamento de Modificação:** Reprocessar apenas arquivos alterados.

## 4. Fluxo de Trabalho Técnico (Workflow)

| Etapa | Responsável | Ação Técnica |
| :--- | :--- | :--- |
| **1. Descoberta** | `FileIndexer` | Executa `Files.walkFileTree`. |
| **2. Validação** | `FileIndexer` | Verifica extensões suportadas. |
| **3. Extração** | `FileExtractor` | Extrai texto puro e MIME type. |
| **4. Transformação** | `FileIndexer` | Cria `org.apache.lucene.document.Document`. |
| **5. Persistência** | `Lucene Engine` | `IndexWriter` armazena no índice. |

## 5. Requisitos Funcionais

### 5.1. Mapeamento de Campos (Lucene Schema)
* `id`: Caminho absoluto (Chave Primária).
* `content`: Conteúdo textual.
* `filename`: Nome para buscas por título.
* `modified`: Timestamp de última modificação.

### 5.2. Tratamento de Erros
* **Arquivos Protegidos:** Logar aviso e pular.
* **Arquivos Vazios:** Não indexar se não houver texto extraível.

## 6. Restrições e Performance
* **Memory Management:** Usar *Streams* para arquivos grandes.
* **Batch Commits:** Commit a cada 100-500 arquivos.
* **Uso de IA:** Lucene prepara o terreno; Gemma 3 entra na fase de busca.

## 7. Roadmap de Implementação
1. **Semana 1:** Implementar `SimpleFileVisitor` no `FileIndexer` e conectar ao `IndexWriter`.
2. **Semana 1 (Fim):** Criar método `documentFactory(Path p)` chamando `FileExtractor.extract(p)`.
3. **Semana 2:** Adicionar lógica de "Upsert" baseada no campo `modified`.
