package com.ifmg.filesearch;

public class OllamaRequest {
    public String model;
    public String prompt;
    public boolean stream; // Normalmente true, mas usaremos false para simplificar

    // Construtor
    public OllamaRequest(String model, String prompt) {
        this.model = model;
        this.prompt = prompt;
        this.stream = false; // Requisição síncrona simples
    }

}
