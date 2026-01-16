package com.ifmg.filesearch;

import java.util.HashMap;
import java.util.Map;

public class OllamaRequest {
    public String model;
    public String prompt;
    public boolean stream; 
    public Map<String, Object> options;

    // Construtor
    public OllamaRequest(String model, String prompt) {
        this.model = model;
        this.prompt = prompt;
        this.stream = false;
        this.options = new HashMap<>();
    }

    public void setContextSize(int contextSize) {
        this.options.put("num_ctx", contextSize);
    }
}