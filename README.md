# FileSearch - Busca Inteligente de Arquivos Locais

O **FileSearch** é uma aplicação desktop desenvolvida em Java que utiliza Inteligência Artificial local (via Ollama) para buscar e ranquear arquivos no seu computador com base em descrições em linguagem natural e palavras-chave.

## 🚀 Funcionalidades

* **Busca Semântica:** Descreva o que você procura (ex: "Trabalho da faculdade sobre redes") e a IA analisa o conteúdo dos arquivos para encontrar os melhores resultados.
* **Filtro por Palavras-Chave:** Refine a busca exigindo que termos específicos estejam presentes no conteúdo.
* **Suporte a Tipos de Arquivo:**
    * Documentos de Texto (`.txt`).
    * Documentos Word (`.docx`) usando Apache POI.
* **Indexação Rápida:** Utiliza scripts PowerShell nativos para varrer diretórios recursivamente.
* **Interação com Sistema:** Abra a pasta do arquivo encontrado com um duplo clique.

## 🛠️ Tecnologias Utilizadas

* **Linguagem:** Java 21.
* **Interface Gráfica:** JavaFX 21 (FXML).
* **IA / LLM:** Integração com **Ollama** (Modelo padrão: `gemma3:4b`).
* **Processamento de Documentos:** Apache POI (OOXML).
* **JSON Parsing:** Jackson Databind.
* **Gerenciamento de Dependências:** Maven.

## 📋 Pré-requisitos

1.  **Java JDK 21** instalado.
2.  **Maven** instalado (ou utilize o wrapper `mvnw` incluso).
3.  **Ollama** instalado e rodando localmente na porta padrão (`11434`).
    * É necessário ter o modelo `gemma3:4b` baixado (ou alterar a constante `DEFAULT_MODEL` em `AIutils.java`).
    * Comando para baixar: `ollama pull gemma3:4b`.
4.  **Sistema Operacional:** Windows (Devido ao uso de caminhos do PowerShell em `FileFinder.java`).

## 🚀 Como Rodar

1.  Clone o repositório:
    ```bash
    git clone [https://github.com/seu-usuario/FileSearch.git](https://github.com/seu-usuario/FileSearch.git)
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