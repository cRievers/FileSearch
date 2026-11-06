package com.ifmg.filesearch;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.br.BrazilianStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.util.CharsRef;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Serviço responsável por carregar o dicionário de sinônimos em português (synonyms_pt.txt)
 * e disponibilizar o analisador léxico com SynonymGraphFilter para expansão em tempo de consulta (query-time).
 */
public class SynonymService {

    private static volatile SynonymService instance;
    private final SynonymMap synonymMap;

    private SynonymService() {
        try {
            this.synonymMap = loadSynonymMap();
        } catch (IOException e) {
            throw new RuntimeException("Falha ao carregar dicionário de sinônimos /synonyms_pt.txt", e);
        }
    }

    public static SynonymService getInstance() {
        if (instance == null) {
            synchronized (SynonymService.class) {
                if (instance == null) {
                    instance = new SynonymService();
                }
            }
        }
        return instance;
    }

    private SynonymMap loadSynonymMap() throws IOException {
        SynonymMap.Builder builder = new SynonymMap.Builder(true); // dedup = true

        try (InputStream is = getClass().getResourceAsStream("/synonyms_pt.txt")) {
            if (is == null) {
                System.err.println("[SynonymService] AVISO: /synonyms_pt.txt não encontrado no classpath.");
                return builder.build();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] parts = line.split(",");
                    if (parts.length < 2) {
                        continue;
                    }
                    for (int i = 0; i < parts.length; i++) {
                        String orig = parts[i].trim().toLowerCase();
                        if (orig.isEmpty()) continue;

                        for (int j = 0; j < parts.length; j++) {
                            if (i == j) continue;
                            String syn = parts[j].trim().toLowerCase();
                            if (syn.isEmpty()) continue;

                            CharsRef inputChars = new CharsRef(orig.replace(' ', SynonymMap.WORD_SEPARATOR));
                            CharsRef outputChars = new CharsRef(syn.replace(' ', SynonymMap.WORD_SEPARATOR));

                            builder.add(inputChars, outputChars, true);
                        }
                    }
                }
            }
        }
        return builder.build();
    }

    public SynonymMap getSynonymMap() {
        return this.synonymMap;
    }

    /**
     * Constrói e retorna um Analyzer que encadeia:
     * StandardTokenizer -> LowerCaseFilter -> SynonymGraphFilter -> BrazilianStemFilter.
     * Deve ser utilizado exclusivamente no momento da consulta (query-time).
     */
    public Analyzer buildSynonymAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tokenizer = new StandardTokenizer();
                TokenStream stream = new LowerCaseFilter(tokenizer);
                // Aplica expansão por sinônimos mantendo termo original
                stream = new SynonymGraphFilter(stream, synonymMap, true);
                // Aplica stemming após a expansão para que variantes flexionadas casem com os radicais
                stream = new BrazilianStemFilter(stream);
                return new TokenStreamComponents(tokenizer, stream);
            }
        };
    }
}

