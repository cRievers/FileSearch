package com.ifmg.filesearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

        String files = "";

        try
        {
            Process process = processBuilder.inheritIO().start();

            // Read the output from the PowerShell command
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                files += line + "\n";
            }
            // Read any errors
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errorReader.readLine()) != null) {
                System.err.println("Error: " + line);
            }

            int exitCode = process.waitFor();
            System.out.println("PowerShell command exited with code: " + exitCode);

        }catch(IOException|
        InterruptedException e)
        {
            e.printStackTrace();
        }
        System.out.println(files);
        return files.split("\n");
    }

    private static String buildCommand(String descricao, List<String>tiposSelecionados, String pasta){
        String resultado = "Get-ChildItem -Path ";
        resultado += pasta;
        resultado += " -Recurse -File -Include ";
        for (int i=0; i<tiposSelecionados.size()-1; i++) {
            resultado += "*" + tiposSelecionados.get(i) + ", ";
        }
        resultado += "*" + tiposSelecionados.getLast();
        resultado += " -ErrorAction SilentlyContinue | Select-Object FullName";
        return resultado;
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
