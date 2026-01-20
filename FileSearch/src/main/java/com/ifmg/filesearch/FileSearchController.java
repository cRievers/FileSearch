package com.ifmg.filesearch;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.application.Platform;
import java.util.LinkedHashMap;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileSearchController {

    @FXML
    private TextArea textAreaDescricao;
    @FXML
    private CheckBox checkPdf;
    @FXML
    private CheckBox checkDocx;
    @FXML
    private CheckBox checkTxt;
    @FXML
    private CheckBox checkPng;
    @FXML
    private ListView<String> listViewResultados;
    @FXML
    private TextField textFieldKeyword;
    @FXML
    private Button buttonAddKeyword;
    @FXML
    private Button buttonRemoveKeyword; // <-- novo botão para remover
    @FXML
    private ListView<String> listViewKeywords;
    @FXML
    private Button btnBuscar;
    @FXML
    private Button btnParar;
    @FXML
    private CheckBox checkXlsx; // Adicionar no FXML
    @FXML
    private CheckBox checkPptx;
    @FXML
    private TextField textFieldPastaBase;

    // Variável para controlar a tarefa atual e permitir o cancelamento
    private Task<LinkedHashMap<String, String>> tarefaBuscaAtual;

    // Mapa para associar o nome do arquivo ao seu caminho completo
    private final Map<String, String> mapaResultados = new HashMap<>();
    private final ObservableList<String> palavrasChave = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Adiciona um listener para o evento de clique duplo na lista
        listViewResultados.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                aoClicarResultado();
            }
        });

        // Inicializa a ListView de palavras-chave e configura o botão/enter
        if (listViewKeywords != null) {
            listViewKeywords.setItems(palavrasChave);

            // Valor padrão para a pasta base
            textFieldPastaBase.setText("C:/Users/");

            // Context menu para remover palavra-chave
            ContextMenu ctx = new ContextMenu();
            MenuItem removerItem = new MenuItem("Remover");
            removerItem.setOnAction(e -> removerPalavraChave());
            ctx.getItems().add(removerItem);
            listViewKeywords.setContextMenu(ctx);

            // Tecla Delete remove a seleção
            listViewKeywords.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.DELETE) {
                    removerPalavraChave();
                }
            });

            // Duplo clique também remove (opcional)
            listViewKeywords.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    removerPalavraChave();
                }
            });
        }
        if (buttonAddKeyword != null) {
            buttonAddKeyword.setOnAction(e -> adicionarPalavraChave());
        }
        if (buttonRemoveKeyword != null) {
            buttonRemoveKeyword.setOnAction(e -> removerPalavraChave());
        }
        if (textFieldKeyword != null) {
            textFieldKeyword.setOnAction(e -> adicionarPalavraChave()); // permite Enter
        }
        if (btnParar != null) {
            btnParar.setDisable(true);
        }

        new Thread(() -> {
            AIutils ai = new AIutils();
            ai.ensureOllamaServerIsRunning();
        }).start();
    }

    @FXML
    protected void selecionarPastaBase(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Selecione a pasta base para a busca");

        // Tenta iniciar na pasta que já está escrita ou na home do usuário
        String caminhoAtual = textFieldPastaBase.getText().trim();
        caminhoAtual = caminhoAtual.replace("\\", "/");
        if (caminhoAtual != null && !caminhoAtual.isEmpty()) {
            File f = new File(caminhoAtual);
            if (f.exists() && f.isDirectory()) {
                directoryChooser.setInitialDirectory(f);
            }
        } else {
            directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        }

        // Obtém a janela atual para abrir o diálogo modal
        Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            // Atualiza o campo de texto com o caminho absoluto (trocando barras invertidas
            // se necessário)
            textFieldPastaBase.setText(selectedDirectory.getAbsolutePath().replace("\\", "/"));
        }
    }

    @FXML
    protected void pararBusca() {
        if (tarefaBuscaAtual != null && tarefaBuscaAtual.isRunning()) {
            // true indica que pode interromper a thread se ela estiver em execução
            tarefaBuscaAtual.cancel(true);
            listViewResultados.getItems().add("Cancelando busca... aguarde.");
            FileFinder.killPowershell();
        }
    }

    @FXML
    protected void adicionarPalavraChave() {
        if (textFieldKeyword == null)
            return; // proteção caso FXML não tenha sido ligado
        String palavra = textFieldKeyword.getText().trim();
        if (palavra.isEmpty()) {
            exibirAlerta("Aviso", "Digite uma palavra-chave antes de adicionar.");
            return;
        }
        if (!palavrasChave.contains(palavra)) {
            palavrasChave.add(palavra);
        }
        textFieldKeyword.clear();
    }

    // NOVO: método para remover a palavra-chave selecionada
    @FXML
    protected void removerPalavraChave() {
        if (listViewKeywords == null)
            return;
        String selecionada = listViewKeywords.getSelectionModel().getSelectedItem();
        if (selecionada == null)
            return;
        palavrasChave.remove(selecionada);
    }

    @FXML
    protected void realizarBusca() {
        // 1. Limpa os resultados anteriores
        listViewResultados.getItems().clear();
        mapaResultados.clear();

        // 2. Coleta dados da UI
        String descricao = textAreaDescricao.getText().trim();
        String pastaBaseRaw = textFieldPastaBase.getText().trim();

        List<String> tiposSelecionados = new ArrayList<>();
        if (checkPdf.isSelected())
            tiposSelecionados.add(".pdf");
        if (checkDocx.isSelected())
            tiposSelecionados.add(".docx");
        if (checkTxt.isSelected())
            tiposSelecionados.add(".txt");
        if (checkPng.isSelected()){
            tiposSelecionados.add(".png");
            tiposSelecionados.add(".jpg");
            tiposSelecionados.add(".jpeg");
        }
        if (checkXlsx.isSelected())
            tiposSelecionados.add(".xlsx");
        if (checkPptx.isSelected())
            tiposSelecionados.add(".pptx");

        // Validação básica
        if (descricao.isEmpty() && tiposSelecionados.isEmpty()) {
            exibirAlerta("Aviso", "Por favor, digite uma descrição ou selecione um tipo de arquivo.");
            return;
        }

        if (pastaBaseRaw.isEmpty()) {
            pastaBaseRaw = "C:/Users/";
        }
        final String pastaBaseFinal = pastaBaseRaw; // Variável final para usar na Task

        System.out.println("--- Iniciando Busca em Segundo Plano ---");
        System.out.println("Descrição: " + descricao);

        // 3. Configura botões
        if (btnBuscar != null)
            btnBuscar.setDisable(true);
        if (btnParar != null)
            btnParar.setDisable(false);
        listViewResultados.getItems().add("Buscando em: " + pastaBaseFinal);

        // 4. Criação da Task
        tarefaBuscaAtual = new Task<>() {
            @Override
            protected LinkedHashMap<String, String> call() throws Exception {
                if (isCancelled())
                    return null;

                // --- Passo A: Busca de Arquivos no Sistema (PowerShell) ---
                updateMessage("Listando arquivos...");
                // Nota: Ajuste o caminho 'C:/Users/...' conforme sua necessidade ou pegue de um
                // input
                new FileFinder(descricao, tiposSelecionados, pastaBaseFinal);
                String[] paths = FileFinder.search();

                if (paths == null || paths.length == 0) {
                    return new LinkedHashMap<>();
                }

                if (isCancelled())
                    return null;

                // --- Passo B: Extração de Texto ---
                updateMessage("Lendo conteúdo (" + paths.length + " arquivos)...");

                List<String> conteudos = new ArrayList<>();
                List<String> nomesArquivos = new ArrayList<>();
                Map<String, String> mapNomeParaPath = new HashMap<>();

                for (String path : paths) {
                    if (isCancelled())
                        return null;

                    File f = new File(path);
                    // Otimização: Se o arquivo for muito grande, talvez pular ou ler parcial no
                    // FileExtractor
                    String texto = FileExtractor.extractFile(path);

                    if (texto != null && !texto.startsWith("Unsupported") && !texto.startsWith("This is not")) {
                        conteudos.add(texto);
                        nomesArquivos.add(f.getName());
                        mapNomeParaPath.put(f.getName(), path);
                    }
                }

                if (conteudos.isEmpty())
                    return new LinkedHashMap<>();

                // --- Passo C: Filtro Híbrido (Lucene + IA em Lotes) ---

                // 1. Configura e Indexa no Lucene
                updateMessage("Criando índice de busca...");
                Filter filtroService = new Filter();
                filtroService.indexarArquivos(nomesArquivos, conteudos);

                if (isCancelled())
                    return null;

                // 2. Busca Preliminar (Lucene) - Reduz de N arquivos para Top 20 (por exemplo)
                updateMessage("Filtrando por palavras-chave...");
                // Converte ObservableList para List normal
                List<String> listaKeywords = new ArrayList<>(palavrasChave);

                // Busca até 20 candidatos que contenham as palavras-chave ou termos da
                // descrição
                List<Filter.DocumentoCandidato> candidatos = filtroService.buscarNoLucene(descricao, listaKeywords, 20);

                System.out.println("Lucene encontrou " + candidatos.size() + " candidatos.");

                if (candidatos.isEmpty()) {
                    return new LinkedHashMap<>();
                }

                // 3. Refinamento Semântico (Ollama em Lotes)
                updateMessage("Analisando contexto com IA (em lotes)...");
                List<String> nomesAprovados;

                // Se o usuário não digitou descrição, só keywords, confiamos no Lucene
                // Se tem descrição semântica ("quero o arquivo que fala sobre..."), usamos o
                // Ollama
                if (!descricao.trim().isEmpty()) {
                    nomesAprovados = filtroService.filtrarComOllamaEmLotes(descricao, candidatos);
                } else {
                    // Se só tem keywords, o Lucene já fez o trabalho exato
                    nomesAprovados = new ArrayList<>();
                    for (Filter.DocumentoCandidato d : candidatos)
                        nomesAprovados.add(d.filename);
                }

                // --- Passo D: Montar Resultado Final ---
                LinkedHashMap<String, String> resultadoFinal = new LinkedHashMap<>();
                for (String nome : nomesAprovados) {
                    if (mapNomeParaPath.containsKey(nome)) {
                        resultadoFinal.put(nome, mapNomeParaPath.get(nome));
                    }
                }

                return resultadoFinal;
            }
        };

        // --- Eventos da Task ---

        tarefaBuscaAtual.setOnSucceeded(event -> {
            listViewResultados.getItems().clear();
            LinkedHashMap<String, String> resultados = tarefaBuscaAtual.getValue();

            if (resultados == null || resultados.isEmpty()) {
                listViewResultados.getItems().add("Nenhum resultado encontrado.");
            } else {
                mapaResultados.putAll(resultados);
                listViewResultados.getItems().addAll(resultados.keySet());
                exibirAlerta("Pronto!", "Busca concluída. Encontrados: " + resultados.size());
            }
            resetaBotoes();
        });

        tarefaBuscaAtual.setOnFailed(event -> {
            listViewResultados.getItems().clear();
            listViewResultados.getItems().add("Erro na busca.");
            Throwable erro = tarefaBuscaAtual.getException();
            erro.printStackTrace();
            exibirAlerta("Erro", "Falha: " + erro.getMessage());
            resetaBotoes();
        });

        tarefaBuscaAtual.setOnCancelled(event -> {
            listViewResultados.getItems().clear();
            listViewResultados.getItems().add("Busca cancelada pelo usuário.");
            resetaBotoes();
        });

        // Inicia a Thread
        new Thread(tarefaBuscaAtual).start();
    }

    private void resetaBotoes() {
        if (btnBuscar != null)
            btnBuscar.setDisable(false);
        if (btnParar != null)
            btnParar.setDisable(true);
    }

    private void aoClicarResultado() {
        String nomeArquivo = listViewResultados.getSelectionModel().getSelectedItem();
        FileSystemService fileService = new FileSystemService();

        if (nomeArquivo != null && mapaResultados.containsKey(nomeArquivo)) {
            String caminhoCompleto = mapaResultados.get(nomeArquivo);
            File arquivo = new File(caminhoCompleto);

            try {
                // Chama o método para revelar na pasta em vez de abrir
                fileService.revelarNoExplorer(arquivo);

            } catch (Exception e) {
                exibirAlerta("Erro", "Não foi possível abrir o diretório.\nErro: " + e.getMessage());
                e.printStackTrace(); // Log para debug
            }
        } else {
            exibirAlerta("Atenção", "Selecione um arquivo válido.");
        }
    }

    private void exibirAlerta(String titulo, String mensagem) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensagem);
        alert.showAndWait();
    }

    // Filtra os arquivos lidos por TODAS as palavras-chave
    private List<Integer> filterFilesbyKeyWords(String[] contents, List<String> keywords) {
        if (keywords.isEmpty()) {
            // Se nenhuma palavra-chave, retorna todos os índices
            System.out.print("Adicionando todos os documentos. Índices retornados: "); // debug
            List<Integer> allIndexes = new ArrayList<>();
            for (int i = 0; i < contents.length; i++) {
                allIndexes.add(i);
                System.out.print(i + " "); // debug
            }
            return allIndexes;
        }
        List<Integer> matchedIndexes = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            String content = contents[i].toLowerCase();
            boolean allMatch = true;
            for (String keyword : keywords) {
                if (!content.contains(keyword.toLowerCase())) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                matchedIndexes.add(i);
            }
        }
        return matchedIndexes;
    }
}