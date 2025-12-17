package com.ifmg.filesearch;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.concurrent.Task;
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
    }

    @FXML
    protected void pararBusca() {
        if (tarefaBuscaAtual != null && tarefaBuscaAtual.isRunning()) {
            // true indica que pode interromper a thread se ela estiver em execução
            tarefaBuscaAtual.cancel(true);
            listViewResultados.getItems().add("Cancelando busca... aguarde.");
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
        // 1. Limpa os resultados anteriores (Operação de UI)
        listViewResultados.getItems().clear();
        mapaResultados.clear();

        // 2. Coleta dados da UI (Deve ser feito na Thread principal)
        String descricao = textAreaDescricao.getText().trim();

        List<String> tiposSelecionados = new ArrayList<>();
        if (checkPdf.isSelected())
            tiposSelecionados.add(".pdf");
        if (checkDocx.isSelected())
            tiposSelecionados.add(".docx");
        if (checkTxt.isSelected())
            tiposSelecionados.add(".txt");
        if (checkPng.isSelected())
            tiposSelecionados.add(".png");

        // Cria uma cópia das palavras-chave para passar para a thread (thread-safety)
        List<String> keywordsCopy = new ArrayList<>(palavrasChave);

        // Validação básica
        if (descricao.isEmpty() || tiposSelecionados.isEmpty()) {
            exibirAlerta("Aviso", "Por favor, digite uma descrição ou selecione um tipo de arquivo.");
            return;
        }

        System.out.println("--- Iniciando Busca em Segundo Plano ---");
        System.out.println("Descrição: " + descricao);

        // 3. Configura botões
        if (btnBuscar != null)
            btnBuscar.setDisable(true);
        if (btnParar != null)
            btnParar.setDisable(false);
        listViewResultados.getItems().add("Buscando...");

        // Opcional: Mostrar um indicador de carregamento na ListView
        listViewResultados.getItems().add("Buscando... aguarde...");

        // 4. Criação da Task
        tarefaBuscaAtual = new Task<>() {
            @Override
            protected LinkedHashMap<String, String> call() throws Exception {
                // VERIFICAÇÃO DE CANCELAMENTO
                if (isCancelled())
                    return null;

                // --- Busca de Arquivos ---
                new FileFinder(descricao, tiposSelecionados, "'C:/Users/'");
                // Se a thread for interrompida aqui, o FileFinder captura a exceção e retorna o
                // que achou
                String[] paths = FileFinder.search();

                if (isCancelled())
                    return null; // Verifica se cancelou após a busca

                // --- Extração de Conteúdo ---
                String[] filesContents = new String[paths.length];
                for (int i = 0; i < paths.length; i++) {
                    if (isCancelled())
                        return null; // Verifica a cada iteração
                    filesContents[i] = FileExtractor.extractFile(paths[i]);
                }

                // --- Filtragem ---
                List<Integer> matchedIndexes = filterFilesbyKeyWords(filesContents, keywordsCopy);

                // --- IA ---
                if (isCancelled())
                    return null;
                updateMessage("Consultando IA..."); // Opcional: feedback visual

                AIutils aiutils = new AIutils();
                String prompt = aiutils.generatePrompt(matchedIndexes, filesContents, descricao);

                if (isCancelled())
                    return null;
                String resposta = aiutils.generate(prompt);

                // --- Processamento da Resposta ---
                if (isCancelled())
                    return null;

                // (Lógica de processamento dos índices igual à anterior...)
                int[] rankedIndexes;
                if (resposta == null || resposta.trim().equals("-1")) {
                    rankedIndexes = new int[0];
                } else {
                    String[] partes = resposta.split(",");
                    List<Integer> validIndexes = new ArrayList<>();
                    for (String parte : partes) {
                        try {
                            int idx = Integer.parseInt(parte.trim());
                            if (idx >= 0 && idx < paths.length)
                                validIndexes.add(idx);
                        } catch (NumberFormatException e) {
                            /* ignorar */ }
                    }
                    rankedIndexes = validIndexes.stream().mapToInt(i -> i).toArray();
                }

                LinkedHashMap<String, String> resultadosProcessados = new LinkedHashMap<>();
                for (int i : rankedIndexes) {
                    File arquivo = new File(paths[i]);
                    resultadosProcessados.put(arquivo.getName(), paths[i]);
                }

                return resultadosProcessados;
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
                exibirAlerta("Pronto!", "Busca concluída.");
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