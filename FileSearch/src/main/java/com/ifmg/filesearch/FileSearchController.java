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
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
    private Button buttonRemoveKeyword;
    @FXML
    private ListView<String> listViewKeywords;
    @FXML
    private Button btnBuscar;
    @FXML
    private Button btnParar;
    @FXML
    private CheckBox checkXlsx;
    @FXML
    private CheckBox checkPptx;
    @FXML
    private TextField textFieldPastaBase;

    // Variável para controlar a tarefa atual e permitir o cancelamento
    private Task<LinkedHashMap<String, String>> tarefaBuscaAtual;

    // Mapa para associar o nome do arquivo ao seu caminho completo
    private final Map<String, String> mapaResultados = new HashMap<>();
    private final ObservableList<String> palavrasChave = FXCollections.observableArrayList();
    private static final String INDEX_PATH = Paths.get(System.getProperty("user.home"), ".filesearch", "lucene_index").toAbsolutePath().toString();

    // --- Estado da indexação em background ---
    private volatile boolean indexacaoConcluida = false;
    private final AtomicInteger arquivosIndexados = new AtomicInteger(0);
    private volatile long indexacaoInicio = 0;
    // Timeline para auto-retry da busca assim que a indexação terminar
    private Timeline retryTimeline;

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

        // Indexação inicial em background usando a pasta base padrão
        indexacaoInicio = System.currentTimeMillis();
        new Thread(() -> {
            String pastaBase = textFieldPastaBase.getText() != null ? textFieldPastaBase.getText().trim() : "C:/Users/";
            if (pastaBase.isEmpty()) pastaBase = "C:/Users/";
            System.out.println("--- Iniciando Indexação em Background ---");
            System.out.println("Pasta base: " + pastaBase);
            System.out.println("Índice em: " + INDEX_PATH);

            // Atualiza o status na UI a cada arquivo indexado
            Platform.runLater(() -> listViewResultados.getItems().setAll(
                "⏳ Indexando seus arquivos em segundo plano...",
                "   A primeira busca será executada automaticamente ao concluir."
            ));

            try (FileIndexer indexer = new FileIndexer(INDEX_PATH)) {
                indexer.setProgressCallback(count -> {
                    arquivosIndexados.set(count);
                    // Atualiza o contador na UI a cada 50 arquivos para não sobrecarregar
                    if (count % 50 == 0) {
                        long elapsed = (System.currentTimeMillis() - indexacaoInicio) / 1000;
                        Platform.runLater(() -> listViewResultados.getItems().setAll(
                            "⏳ Indexando... " + count + " arquivos processados (≈" + elapsed + "s)",
                            "   A busca será executada automaticamente ao concluir."
                        ));
                    }
                });

                indexer.setOnCompleteCallback(() -> {
                    indexacaoConcluida = true;
                    long elapsed = (System.currentTimeMillis() - indexacaoInicio) / 1000;
                    System.out.println("--- Indexação em Background Concluída: "
                        + arquivosIndexados.get() + " arquivos em " + elapsed + "s ---");
                    Platform.runLater(() -> {
                        // Se há um retry agendado, dispara a busca agora
                        if (retryTimeline != null) {
                            retryTimeline.stop();
                            retryTimeline = null;
                            realizarBusca();
                        } else {
                            listViewResultados.getItems().setAll(
                                "✅ Indexação concluída! " + arquivosIndexados.get() + " arquivos indexados em " + elapsed + "s.",
                                "   Pronto para buscar."
                            );
                        }
                    });
                });

                indexer.indexDirectory(Paths.get(pastaBase));
            } catch (Exception e) {
                System.err.println("Erro na indexação em background: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> listViewResultados.getItems().setAll(
                    "⚠️ Erro na indexação: " + e.getMessage()
                ));
            }
        }, "BackgroundIndexer").start();
    }

    @FXML
    protected void selecionarPastaBase(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Selecione a pasta base para a busca");

        // Tenta iniciar na pasta que já está escrita ou na home do usuário
        String caminhoAtual = textFieldPastaBase.getText().trim();
        caminhoAtual = caminhoAtual.replace("\\", "/");
        if (!caminhoAtual.isEmpty()) {
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
            textFieldPastaBase.setText(selectedDirectory.getAbsolutePath().replace("\\", "/"));
        }
    }

    @FXML
    protected void pararBusca() {
        if (tarefaBuscaAtual != null && tarefaBuscaAtual.isRunning()) {
            tarefaBuscaAtual.cancel(true);
            listViewResultados.getItems().add("Cancelando busca... aguarde.");
            FileFinder.killPowershell();
        }
    }

    @FXML
    protected void adicionarPalavraChave() {
        if (textFieldKeyword == null)
            return;
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
        final String pastaBaseFinal = pastaBaseRaw;

        System.out.println("--- Iniciando Busca em Segundo Plano ---");
        System.out.println("Descrição: " + descricao);

        // --- Guarda de Indexação: se o índice ainda não está pronto, mostra status e agenda retry ---
        if (!indexacaoConcluida) {
            long elapsed = (System.currentTimeMillis() - indexacaoInicio) / 1000;
            int indexed = arquivosIndexados.get();

            listViewResultados.getItems().setAll(
                "⏳ Indexação em andamento... aguarde.",
                "   📁 " + indexed + " arquivos processados até agora (≈" + elapsed + "s decorridos)",
                "   🔄 Sua busca será executada automaticamente ao concluir a indexação.",
                "   Ou aguarde e clique em Buscar novamente."
            );

            // Agenda um retry que vai disparar assim que indexacaoConcluida = true
            if (retryTimeline == null) {
                retryTimeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
                    if (indexacaoConcluida && retryTimeline != null) {
                        retryTimeline.stop();
                        retryTimeline = null;
                        realizarBusca();
                    } else {
                        long el = (System.currentTimeMillis() - indexacaoInicio) / 1000;
                        int idx = arquivosIndexados.get();
                        listViewResultados.getItems().setAll(
                            "⏳ Indexação em andamento... aguarde.",
                            "   📁 " + idx + " arquivos processados (≈" + el + "s decorridos)",
                            "   🔄 Sua busca será executada automaticamente ao concluir.",
                            "   Ou aguarde e clique em Buscar novamente."
                        );
                    }
                }));
                retryTimeline.setCycleCount(Timeline.INDEFINITE);
                retryTimeline.play();
            }
            return;
        }

        if (retryTimeline != null) {
            retryTimeline.stop();
            retryTimeline = null;
        }

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

                // --- Passo A: Busca Preliminar (Lucene) ---
                updateMessage("Filtrando índice persistente...");
                Filter filtroService = new Filter(INDEX_PATH);

                if (isCancelled())
                    return null;

                List<String> listaKeywords = new ArrayList<>(palavrasChave);

                // Busca candidatos no índice persistente do Lucene
                List<Filter.DocumentoCandidato> candidatos = filtroService.buscarNoLucene(
                    descricao, listaKeywords, pastaBaseFinal, tiposSelecionados, 15
                );

                System.out.println("Lucene retornou " + candidatos.size() + " candidatos relevantes após o filtro de pontuação mínima.");

                if (candidatos.isEmpty()) {
                    return new LinkedHashMap<>();
                }

                if (isCancelled())
                    return null;

                // --- Passo B: Montar Resultado Final com Candidatos Aprovados ---
                LinkedHashMap<String, String> resultadoFinal = new LinkedHashMap<>();
                for (Filter.DocumentoCandidato d : candidatos) {
                    resultadoFinal.put(d.filename, d.id);
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
                fileService.revelarNoExplorer(arquivo);
            } catch (Exception e) {
                exibirAlerta("Erro", "Não foi possível abrir o diretório.\nErro: " + e.getMessage());
                e.printStackTrace();
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
}
