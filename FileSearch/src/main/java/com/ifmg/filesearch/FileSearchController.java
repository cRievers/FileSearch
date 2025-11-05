package com.ifmg.filesearch;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileSearchController {

    @FXML private TextArea textAreaDescricao;
    @FXML private CheckBox checkPdf;
    @FXML private CheckBox checkDocx;
    @FXML private CheckBox checkTxt;
    @FXML private CheckBox checkPng;
    @FXML private ListView<String> listViewResultados;
    @FXML private TextField textFieldKeyword;
    @FXML private Button buttonAddKeyword;
    @FXML private Button buttonRemoveKeyword; // <-- novo botão para remover
    @FXML private ListView<String> listViewKeywords;

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
    }

    @FXML
    protected void adicionarPalavraChave() {
        if (textFieldKeyword == null) return; // proteção caso FXML não tenha sido ligado
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
        if (listViewKeywords == null) return;
        String selecionada = listViewKeywords.getSelectionModel().getSelectedItem();
        if (selecionada == null) return;
        palavrasChave.remove(selecionada);
    }

    @FXML
    protected void realizarBusca() {
        // 1. Limpa os resultados anteriores
        listViewResultados.getItems().clear();
        mapaResultados.clear();

        // 2. Obtém a descrição do campo de texto
        String descricao = textAreaDescricao.getText().trim();

        // 3. Verifica quais checkboxes estão selecionados
        List<String> tiposSelecionados = new ArrayList<>();
        if (checkPdf.isSelected()) {
            tiposSelecionados.add(".pdf");
        }
        if (checkDocx.isSelected()) {
            tiposSelecionados.add(".docx");
        }
        if (checkTxt.isSelected()) {
            tiposSelecionados.add(".txt");
        }
        if (checkPng.isSelected()) {
            tiposSelecionados.add(".png");
        }

        // --- SIMULAÇÃO DA BUSCA ---
        System.out.println("--- Iniciando Busca ---");
        System.out.println("Descrição: " + descricao);
        System.out.println("Tipos de arquivo: " + (tiposSelecionados.isEmpty() ? "Qualquer tipo" : String.join(", ", tiposSelecionados)));
        System.out.println("----------------------");

        if (descricao.isEmpty() && tiposSelecionados.isEmpty()) {
            exibirAlerta("Aviso", "Por favor, digite uma descrição ou selecione um tipo de arquivo.");
            return;
        }

        // Exemplo de resultados (substitua com sua lógica de busca real)
        List<Map<String, String>> resultadosFicticios = List.of(
                Map.of("nome", "Relatorio_Anual_2024.pdf", "caminho", "/home/user/documentos/Relatorio_Anual_2024.pdf"),
                Map.of("nome", "Proposta_Comercial.docx", "caminho", "C:\\Users\\Usuario\\Documentos\\Proposta_Comercial.docx"),
                Map.of("nome", "notas_reuniao.txt", "caminho", "/home/user/notas/notas_reuniao.txt"),
                Map.of("nome", "logo_empresa.png", "caminho", "C:\\Imagens\\logo_empresa.png")
        );

        new FileFinder(descricao, tiposSelecionados, "'C:/Users/'");

        String[] paths = FileFinder.search();
        String filesContents[] = new String[paths.length];
        for (int i = 0; i < paths.length; i++) {
            filesContents[i] = FileExtractor.extractFile(paths[i]);
        }

        if (paths.length == 0) {
            System.out.println("INFO: realizarBusca(): nenhum arquivo encontrado.");
        }

        // System.out.println(filesContents[0]);

        /*
        String powershellExecutable = "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe"; // Or the full path if needed
        String pasta = "C:/Users/";


        String powershellCommand = retornarComando(descricao, tiposSelecionados, pasta); // Your PowerShell command

        ProcessBuilder processBuilder = new ProcessBuilder(
                powershellExecutable,
                "-Command",
                powershellCommand
        );

        try {
            Process process = processBuilder.inheritIO().start();

            // Read the output from the PowerShell command
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line); //avaliar cada arquivo aqui
            }

            // Read any errors
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errorReader.readLine()) != null) {
                System.err.println("Error: " + line);
            }

            int exitCode = process.waitFor();
            System.out.println("PowerShell command exited with code: " + exitCode);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        */

        // Filtra os resultados fictícios
        ObservableList<String> resultadosEncontrados = FXCollections.observableArrayList();
        for (Map<String, String> doc : resultadosFicticios) {
            String nomeArquivo = doc.get("nome");
            boolean descricaoCorresponde = descricao.isEmpty() || nomeArquivo.toLowerCase().contains(descricao.toLowerCase());

            if (descricaoCorresponde) {
                resultadosEncontrados.add(nomeArquivo);
                mapaResultados.put(nomeArquivo, doc.get("caminho"));
            }
        }

        if (resultadosEncontrados.isEmpty()) {
            listViewResultados.getItems().add("Nenhum resultado encontrado.");
        } else {
            listViewResultados.setItems(resultadosEncontrados);
        }
    }

    private void aoClicarResultado() {
        String nomeArquivo = listViewResultados.getSelectionModel().getSelectedItem();
        if (nomeArquivo != null && mapaResultados.containsKey(nomeArquivo)) {
            String caminhoCompleto = mapaResultados.get(nomeArquivo);
            exibirAlerta("Abrir Arquivo", "Simulando a abertura do arquivo:\n\n" + caminhoCompleto);
        } else {
            exibirAlerta("Erro", "Não foi possível encontrar o caminho do arquivo.");
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