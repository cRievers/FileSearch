package com.ifmg.filesearch;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

    // Mapa para associar o nome do arquivo ao seu caminho completo
    private final Map<String, String> mapaResultados = new HashMap<>();

    @FXML
    public void initialize() {
        // Adiciona um listener para o evento de clique duplo na lista
        listViewResultados.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                aoClicarResultado();
            }
        });
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

        // Filtra os resultados fictícios
        ObservableList<String> resultadosEncontrados = FXCollections.observableArrayList();
        for (Map<String, String> doc : resultadosFicticios) {
            String nomeArquivo = doc.get("nome");
            boolean tipoCorresponde = tiposSelecionados.isEmpty() || tiposSelecionados.stream().anyMatch(nomeArquivo::endsWith);
            boolean descricaoCorresponde = descricao.isEmpty() || nomeArquivo.toLowerCase().contains(descricao.toLowerCase());

            if (tipoCorresponde && descricaoCorresponde) {
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

    private String retornarComando(String descricao, List<String>tiposSelecionados, String pasta){
        String resultado = "Get-ChildItem -Path ";
        resultado += pasta;
        resultado += " -Recurse -File -Include ";
        for (int i=0; i<tiposSelecionados.size()-1; i++) {
            resultado += "*" + tiposSelecionados.get(i) + ", ";
        }
        resultado += "*" + tiposSelecionados.getLast();
        resultado += " -ErrorAction SilentlyContinue | Select-Object FullName, Length, LastWriteTime";
        return resultado;
    }

    private String retornarPasta(){
        ProcessBuilder processBuilder = new ProcessBuilder(
                "powershell.exe",
                "-Command",
                "pwd"
        );
        String line = "";
        try {
            Process process = processBuilder.start();

            // Read the output from the PowerShell command
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            while ((line = reader.readLine()) != null) {
                System.out.println(line);
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
        return line;
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