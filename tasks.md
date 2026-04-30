# Tasks: Implementação da Integração FileIndexer ↔ FileExtractor

Este documento lista as tarefas pendentes para a integração do motor de busca conforme definido em `specs/integration_spec.md`.

## Fase 1: Infraestrutura de Varredura e Indexação
- [x] **Implementar `SimpleFileVisitor` no `FileIndexer`**
    - [x] Criar classe anônima ou interna que herda de `SimpleFileVisitor<Path>`.
    - [x] Implementar `preVisitDirectory` para ignorar pastas de sistema (`.git`, `node_modules`, `target`).
    - [x] Implementar `visitFile` para filtrar extensões válidas.
- [x] **Conectar `FileIndexer` ao `IndexWriter`**
    - [x] Configurar o `IndexWriter` com `StandardAnalyzer`.
    - [x] Garantir o uso de Try-with-resources para fechar o writer.

## Fase 2: Fábrica de Documentos e Extração
- [x] **Criar método `documentFactory(Path p)`**
    - [x] Invocar `FileExtractor.extract(p)` para obter o conteúdo textual.
    - [x] Mapear metadados do arquivo (caminho, nome, data de modificação).
    - [x] Construir o objeto `org.apache.lucene.document.Document` com os campos: `id`, `content`, `filename`, `modified`.
- [x] **Integrar `FileExtractor` no fluxo**
    - [x] Lidar com exceções de extração (arquivos protegidos, corrompidos) sem interromper a varredura.
    - [x] Ignorar arquivos vazios no índice.

## Fase 3: Otimização e Sincronização (Upsert)
- [x] **Implementar lógica de "Upsert"**
    - [x] Usar `writer.updateDocument(new Term("id", path), doc)` para evitar duplicatas.
    - [x] Adicionar verificação de timestamp: só indexar se `modified` no disco > `modified` no índice.
- [x] **Batch Commits**
    - [x] Implementar contador para executar `writer.commit()` a cada 100-500 arquivos.

## Fase 4: Performance e Segurança
- [x] **Processamento Assíncrono**
    - [x] Configurar `ExecutorService` para desacoplar a varredura (I/O) da extração de conteúdo (CPU).
- [x] **Gerenciamento de Memória**
    - [x] Validar o uso de Streams no `FileExtractor` para arquivos grandes.
