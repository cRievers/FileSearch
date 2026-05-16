# Documento de Requisitos de Produto (PRD) - FileSearch

## 1. Objetivo
Criar uma ferramenta desktop multiplataforma para busca semântica e técnica em arquivos locais, garantindo 100% de privacidade através de processamento offline (Lucene + Gemma 3). O sistema deve permitir que usuários encontrem documentos não apenas por nome, mas por conteúdo e contexto.

## 2. User Stories (Épicos)
* **Busca por Linguagem Natural:** "Como usuário, quero descrever um assunto (ex: 'contrato de aluguel de 2023') e encontrar o arquivo mesmo sem saber o nome exato."
* **Filtragem Técnica:** "Como usuário, quero restringir minha busca a extensões específicas (.pdf, .java) para acelerar o resultado."
* **Extração de Respostas (RAG):** "Como usuário, quero que a IA leia os arquivos encontrados e me dê uma resposta direta baseada no conteúdo deles."
* **Indexação Transparente:** "Como usuário, quero que o sistema mapeie meus arquivos em segundo plano sem travar meu computador."

## 3. Restrições e Escopo
* **Privacidade:** Nenhum dado ou metadado pode sair da máquina do usuário.
* **Performance:** A indexação deve ser eficiente em termos de I/O e memória, permitindo o uso do computador durante o processo.
* **Extensibilidade:** O sistema deve suportar novos formatos de arquivo via plugins ou bibliotecas de extração (Apache Tika).
* **Multiplataforma:** Deve rodar consistentemente em Windows, Linux e macOS.

## 4. Integração FileIndexer ↔ FileExtractor
A integração visa transformar arquivos brutos encontrados no disco em documentos pesquisáveis.

### Objetivos da Integração
* **Extração Transparente:** Converter PDF, Word, TXT e Imagens (OCR) em texto limpo para o motor de busca.
* **Consistência de Dados:** Sincronizar metadados (caminho, data de modificação) com o conteúdo textual.
* **Resiliência:** Garantir que arquivos corrompidos ou protegidos não interrompam a varredura global.

### User Stories (Back-end)
* **Extração em Fluxo:** O sistema deve processar arquivos e alimentar o motor de busca de forma contínua.
* **Filtragem Inteligente:** O indexador deve ignorar arquivos não suportados ou irrelevantes (pastas de sistema, arquivos temporários).
* **Indexação Incremental:** Apenas arquivos alterados desde a última varredura devem ser reprocessados para otimizar recursos.

## 5. Situação Atual e Análise Técnica
O sistema encontra-se em uma fase de transição crítica: a migração de uma busca "on-the-fly" (indexação em RAM a cada busca) para um sistema de **indexação persistente e incremental**.

### O que já está pronto:
1.  **Motor de Indexação (`FileIndexer.java`):** Implementa o padrão *Visitor*, multithreading para extração e a lógica de *Upsert* (indexação incremental baseada no timestamp).
2.  **Extração Robusta (`FileExtractor.java`):** Utiliza Apache Tika para suporte multiformato e Tesseract para OCR de imagens.
3.  **Integração com IA (`AIutils.java` & `Filter.java`):** Comunicação funcional com Ollama (Gemma 3) para filtros semânticos em lotes.
4.  **Interface Base:** UI em JavaFX funcional, embora ainda dependente do `FileFinder` (PowerShell) para a listagem inicial.

## 6. Próximos Passos (Roadmap de Integração)
Para transformar o sistema em um "Google Desktop Privado" completo, as seguintes ações são prioritárias:

### 1. Migração para Busca Direta no Lucene (Prioridade Máxima)
Atualmente, o `FileSearchController` utiliza o PowerShell para listar arquivos.
*   **Ação:** Alterar o fluxo para que a busca ocorra diretamente no índice persistente do Lucene. Isso trará resultados instantâneos e removerá a dependência do PowerShell no momento da consulta.

### 2. Implementação da Interface de Chat (RAG)
Expandir o uso da IA para além da filtragem de resultados.
*   **Ação:** Criar uma interface de chat onde o usuário possa interagir com os conteúdos encontrados (ex: resumir documentos ou extrair dados específicos) usando a Gemma 3 via RAG.

### 3. Sincronização em Tempo Real (`WatchService`)
Automatizar a atualização do índice.
*   **Ação:** Implementar um serviço de monitoramento (`WatchService`) que detecte alterações no sistema de arquivos e atualize o índice de forma transparente.

### 4. Refinamento de UI/UX
Melhorar a transparência para o usuário.
*   **Ação:** Adicionar indicadores de progresso de indexação em background e um painel de visualização prévia (preview) de documentos.
