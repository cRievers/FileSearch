package com.ifmg.filesearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class FileFinder {

    private static String descricao;
    private static List<String> tiposSelecionados;
    private static String pasta;

    public FileFinder(String descricao, List<String> tiposSelecionados, String pasta){
        FileFinder.descricao = descricao;
        FileFinder.tiposSelecionados = tiposSelecionados;
        FileFinder.pasta = pasta;
    }

    public static String[] search(){
        String powershellExecutable = "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
        String powershellCommand = buildCommand(descricao, tiposSelecionados, pasta); // Your PowerShell command

        ProcessBuilder processBuilder = new ProcessBuilder(
            powershellExecutable,
            "-Command",
            powershellCommand
        );

        List<String> filesPaths = new ArrayList<>();
        System.out.println("Executing command: " + powershellCommand); // Debug line

        try {
            Process process = processBuilder.start();

            // Read the output from the PowerShell command in a separate thread to prevent deadlock
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().isEmpty()) {
                            //System.out.println("Found file: " + line); // Debug line
                            StringBuilder lineToAdd = new StringBuilder("");
                            lineToAdd.append(line);
                            lineToAdd.append("");
                            filesPaths.add(lineToAdd.toString());
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            outputThread.start();

            // Read any errors
            Thread errorThread = new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        System.err.println("Error: " + line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            errorThread.start();

            // Wait for both threads to complete
            outputThread.join();
            errorThread.join();

            int exitCode = process.waitFor();
            System.out.println("PowerShell command exited with code: " + exitCode);

        }catch(IOException|
        InterruptedException e)
        {
            e.printStackTrace();
        }

        return filesPaths.toArray(new String[0]);
    }

    private static String buildCommand(String descricao, List<String> tiposSelecionados, String pasta){
        StringBuilder resultado = new StringBuilder();
        resultado.append("Get-ChildItem -Path ");
        // Quote the path in case it contains spaces
        resultado.append('"').append(pasta).append('"');
        resultado.append(" -Recurse -File");
        
        // Handle file types
        if (tiposSelecionados != null && !tiposSelecionados.isEmpty()) {
            resultado.append(" -Include @(");
            for (int i = 0; i < tiposSelecionados.size(); i++) {
                if (i > 0) resultado.append(",");
                resultado.append("'*").append(tiposSelecionados.get(i)).append("'");
            }
            resultado.append(")");
        }
        
        resultado.append(" | Select-Object -ExpandProperty FullName");
        
        resultado.append(" -ErrorAction SilentlyContinue");
        return resultado.toString();
    }

    private String currentFolder(){
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

}
