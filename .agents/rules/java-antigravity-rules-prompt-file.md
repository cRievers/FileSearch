---
description: "Diretrizes de desenvolvimento do Antigravity para o projeto FileSearch."
globs: **/*
alwaysApply: false
---
# Configuração do Projeto
file_location: root_directory
file_name: .agents/rules

# Perfil do Desenvolvedor AI
ai_persona:
  role: Senior Java Developer
  principles:
    - SOLID
    - DRY
    - KISS
    - YAGNI

# Stack Técnica
tech_stack:
  framework: JavaFX
  build_tool: Maven
  java_version: 21
  dependencies:
    - Apache Lucene 9.x
    - Apache Tika 2.x
    - Tess4J (Tesseract OCR)
    - Gson / Jackson
    - JUnit 5
    - Ollama / Gemma 3
  language: Portuguese
  code_comments: Portuguese

# Diretrizes de Desenvolvimento
development_guidelines:
  resource_management:
    - "Sempre utilize try-with-resources para fechar recursos do sistema e do Lucene (como IndexWriter, DirectoryReader, Streams, etc.)"
  concurrency_and_performance:
    - "Utilize ExecutorService/Thread Pools para tarefas intensivas em segundo plano (como extração de texto no FileExtractor)"
    - "Qualquer modificação na UI do JavaFX a partir de threads secundárias deve ser orquestrada via Platform.runLater()"
    - "Para indexação eficiente, utilize a estratégia de Upsert incremental no Lucene baseada na comparação de timestamps de modificação (modified)"
    - "Evite executar commits muito frequentes no Lucene; acumule os documentos indexados em lotes (ex: 200 arquivos) antes de chamar o commit()"
  exception_handling:
    - "Capture exceções de forma isolada ao processar/extrair cada arquivo, para que a falha em um arquivo não interrompa a varredura global"
    - "Não silencie exceções; garanta logs ou mensagens de erro informativas no console"
  clean_code:
    - "Minimize a acessibilidade de classes e membros utilizando encapsulamento adequado (private, package-private, final)"
    - "Prefira utilizar as classes do Java NIO (Path, Files) e o padrão Visitor (SimpleFileVisitor) para varredura de diretórios de forma performática"