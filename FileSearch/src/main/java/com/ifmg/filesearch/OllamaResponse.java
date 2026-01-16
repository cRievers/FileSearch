package com.ifmg.filesearch;

import java.util.List;

public class OllamaResponse {
    public String model;
    public String response; // O texto gerado pela IA
    public List<Double> embedding;
    public String done; // Indicador de conclusão (true/false)
    public String created_at; // "2025-10-17T23:14:07.414671Z"
    public String done_reason; // stop
    public long total_duration; // 174560334
    public long load_duration; // 101397084
    public int prompt_eval_count;// 11
    public long prompt_eval_duration; // 13074791,
    public int eval_count; // 18,
    public long eval_duration; //  52479709
    public int[] context; // null
}
