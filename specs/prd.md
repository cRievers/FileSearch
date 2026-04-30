# FileSearch - Product Requirements Document (PRD)

## 1. Visão Geral do Produto
**Objetivo:** Criar uma ferramenta desktop multiplataforma para busca semântica e técnica em arquivos locais, garantindo 100% de privacidade através de processamento offline (Lucene + Gemma 3).

### User Stories (Épicos)
* **Busca por Linguagem Natural:** "Como usuário, quero descrever um assunto (ex: 'contrato de aluguel de 2023') e encontrar o arquivo mesmo sem saber o nome exato."
* **Filtragem Técnica:** "Como usuário, quero restringir minha busca a extensões específicas (.pdf, .java) para acelerar o resultado."
* **Extração de Respostas (RAG):** "Como usuário, quero que a IA leia os arquivos encontrados e me dê uma resposta direta baseada no conteúdo deles."
* **Indexação Transparente:** "Como usuário, quero que o sistema mapeie meus arquivos em segundo plano sem travar meu computador."

### Restrições e Escopo
* **Privacidade:** Nenhum dado ou metadado pode sair da máquina do usuário.
* **Performance:** A indexação deve ser eficiente em termos de I/O e memória.
* **Extensibilidade:** O sistema deve suportar novos formatos de arquivo via plugins ou bibliotecas de extração (Apache Tika).
* **Multiplataforma:** Deve rodar consistentemente em Windows, Linux e macOS.

### Roadmap Sugerido
1. **Fase 1 (Atual):** Motor de Indexação (Lucene) e Varredura Nativa (`walkFileTree`).
2. **Fase 2:** Extração de conteúdo multiformato (PDF, Docx) e OCR básico para imagens.
3. **Fase 3:** Integração com Ollama (Gemma 3) para interface de chat/RAG.
4. **Fase 4:** Interface Gráfica avançada e monitoramento de mudanças no sistema de arquivos (WatchService).
