# FileSearch - Busca Inteligente de Arquivos Locais

O **FileSearch** é uma aplicação desktop desenvolvida em Java que utiliza Inteligência Artificial local (via Ollama) e o motor de busca **Apache Lucene** para indexar, buscar e ranquear arquivos no seu computador com base em descrições em linguagem natural, palavras-chave e análise semântica.

## 🚀 Funcionalidades

* **Busca Semântica:** Descreva o que você procura (ex: "Trabalho da faculdade sobre redes") e a IA analisa o conteúdo dos arquivos para encontrar os melhores resultados.
* **Motor de Busca Avançado (Lucene):** Indexação robusta de arquivos que permite buscas instantâneas e filtragem inteligente.
* **Suporte Multilíngue Automático:** Identificação automática do idioma do documento (Português ou Inglês) utilizando **Apache Tika**. Aplicação de `BrazilianAnalyzer` ou `EnglishAnalyzer` nativamente no Lucene para regras de stemming e remoção de *stop words* adequadas para cada idioma.
* **Filtro por Palavras-Chave:** Refine a busca exigindo que termos específicos estejam presentes no conteúdo ou no nome do arquivo utilizando consultas avançadas com suporte a múltiplos campos.
* **Suporte a Múltiplos Tipos de Arquivo:**
    * Extração e leitura de arquivos facilitada (Textos, PDFs, Documentos Word `.docx`, etc.).
* **Interação com Sistema:** Abra a pasta do arquivo encontrado com um duplo clique.

## 🛣️ Roadmap (Em Desenvolvimento)

As seguintes funcionalidades estão mapeadas e serão implementadas nas próximas versões:
* **Interface de Chat (RAG):** Uma nova aba de chat na interface gráfica que integrará os resultados da busca com o modelo (Gemma 3) para responder a perguntas diretamente sobre o conteúdo dos seus arquivos.
* **Sincronização em Tempo Real:** Monitoramento contínuo de pastas com atualização automática (incremental) do índice Lucene sem necessidade de varreduras manuais completas.
* **Refinamentos na UI/UX:** Adição de barra de progresso para a indexação em segundo plano e um painel de preview para visualizar o conteúdo dos arquivos diretamente pelo app.


## 🛠️ Tecnologias Utilizadas

* **Linguagem:** Java 21.
* **Interface Gráfica:** JavaFX 21 (FXML).
* **Motor de Busca:** Apache Lucene.
* **Processamento e Detecção de Idioma:** Apache Tika (LanguageDetector e Parser).
* **IA / LLM:** Integração com **Ollama** (Modelo padrão: `gemma3:4b`).
* **JSON Parsing:** Jackson Databind.
* **Gerenciamento de Dependências:** Maven.

## 📋 Pré-requisitos

1.  **Java JDK 21** instalado.
2.  **Maven** instalado (ou utilize o wrapper `mvnw` incluso).
3.  **Ollama** instalado e rodando localmente na porta padrão (`11434`).
    * É necessário ter o modelo `gemma3:4b` baixado (ou alterar a constante `DEFAULT_MODEL` em `AIutils.java`).
    * Comando para baixar: `ollama pull gemma3:4b`.
4.  **Sistema Operacional:** Windows.

## 🚀 Como Rodar

1.  Clone o repositório:
    ```bash
    git clone https://github.com/seu-usuario/FileSearch.git
    cd FileSearch/FileSearch
    ```

2.  Certifique-se de que o servidor do Ollama está rodando:
    ```bash
    ollama serve
    ```

3.  Compile e execute o projeto via Maven:
    ```bash
    mvn clean javafx:run
    ```

## ⚙️ Configuração

### Alterar o Modelo de IA
Por padrão, o projeto utiliza o modelo `gemma3:4b`. Para alterar, edite a classe `AIutils.java`:

```java
private static final String DEFAULT_MODEL = "seu-modelo-aqui"; // Ex: llama3, mistral
```