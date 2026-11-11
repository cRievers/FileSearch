package com.ifmg.filesearch;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Serviço responsável por carregar o modelo ONNX paraphrase-multilingual-MiniLM-L12-v2
 * e o tokenizador WordPiece para gerar vetores de embedding densos (384 dimensões)
 * operando inteiramente em CPU e em ambiente local (air-gapped).
 */
public class EmbeddingService implements AutoCloseable {

    private static volatile EmbeddingService instance;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final Set<String> sessionInputNames;

    public static final int MAX_TOKENS = 128;
    public static final int EMBEDDING_DIM = 384;

    private EmbeddingService() {
        try {
            this.env = OrtEnvironment.getEnvironment("EmbeddingServiceEnv");

            Path modelsDir = Paths.get(System.getProperty("user.home"), ".filesearch", "models");
            Files.createDirectories(modelsDir);

            Path modelPath = modelsDir.resolve("minilm-multilingual.onnx");
            if (!Files.exists(modelPath)) {
                try (InputStream is = getClass().getResourceAsStream("/models/minilm-multilingual.onnx")) {
                    if (is != null) {
                        Files.copy(is, modelPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    System.err.println("Aviso ao tentar copiar modelo do classpath: " + e.getMessage());
                }
            }

            if (!Files.exists(modelPath)) {
                throw new IllegalStateException("Modelo ONNX não encontrado em: " + modelPath
                        + ". Coloque minilm-multilingual.onnx em ~/.filesearch/models/");
            }

            Path tokenizerPath = modelsDir.resolve("tokenizer.json");
            if (!Files.exists(tokenizerPath)) {
                try (InputStream is = getClass().getResourceAsStream("/models/tokenizer.json")) {
                    if (is != null) {
                        Files.copy(is, tokenizerPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    System.err.println("Aviso ao tentar copiar tokenizer do classpath: " + e.getMessage());
                }
            }

            if (!Files.exists(tokenizerPath)) {
                throw new IllegalStateException("Tokenizer não encontrado em: " + tokenizerPath
                        + ". Coloque tokenizer.json em ~/.filesearch/models/");
            }

            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            sessionOptions.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
            this.session = env.createSession(modelPath.toAbsolutePath().toString(), sessionOptions);
            this.sessionInputNames = session.getInputNames();

            this.tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(tokenizerPath)
                    .optMaxLength(MAX_TOKENS)
                    .optTruncation(true)
                    .build();

            System.out.println("[EmbeddingService] Inicializado com sucesso. Modelo: " + modelPath
                    + " (inputs: " + sessionInputNames + ")");
        } catch (Exception e) {
            throw new RuntimeException("Falha ao inicializar EmbeddingService: " + e.getMessage(), e);
        }
    }

    public static EmbeddingService getInstance() {
        if (instance == null) {
            synchronized (EmbeddingService.class) {
                if (instance == null) {
                    instance = new EmbeddingService();
                }
            }
        }
        return instance;
    }

    /**
     * Codifica o texto em um vetor denso L2-normalizado de 384 dimensões.
     * Método sincronizado para garantir thread-safety durante a tokenização (DJL HuggingFace Tokenizers)
     * e evitar contenção na inferência do modelo ONNX quando invocado por múltiplos threads de indexação.
     */
    public synchronized float[] encode(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[EMBEDDING_DIM];
        }

        try {
            Encoding encoding = tokenizer.encode(text, true, false);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();

            if (inputIds.length == 0) {
                return new float[EMBEDDING_DIM];
            }

            Map<String, OnnxTensor> inputs = new HashMap<>();
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, new long[][]{inputIds});
            inputs.put("input_ids", inputTensor);

            OnnxTensor maskTensor = OnnxTensor.createTensor(env, new long[][]{attentionMask});
            inputs.put("attention_mask", maskTensor);

            OnnxTensor typeIdsTensor = null;
            if (sessionInputNames.contains("token_type_ids")) {
                long[] typeIds = encoding.getTypeIds();
                if (typeIds == null || typeIds.length != inputIds.length) {
                    typeIds = new long[inputIds.length];
                }
                typeIdsTensor = OnnxTensor.createTensor(env, new long[][]{typeIds});
                inputs.put("token_type_ids", typeIdsTensor);
            }

            try (OrtSession.Result result = session.run(inputs)) {
                // last_hidden_state de shape [batch_size=1, seq_len, 384]
                Object value = result.get(0).getValue();
                float[][][] lastHidden = (float[][][]) value;
                return meanPooling(lastHidden[0], attentionMask);
            } finally {
                inputTensor.close();
                maskTensor.close();
                if (typeIdsTensor != null) {
                    typeIdsTensor.close();
                }
            }
        } catch (OrtException e) {
            System.err.println("[EmbeddingService] Erro durante inferência ONNX: " + e.getMessage());
            return new float[EMBEDDING_DIM];
        }
    }

    /**
     * Aplica Mean Pooling sobre os tokens ativos (onde attention_mask == 1)
     * e realiza a normalização L2 do vetor final.
     */
    private float[] meanPooling(float[][] tokenEmbeddings, long[] mask) {
        float[] pooled = new float[EMBEDDING_DIM];
        int count = 0;

        for (int i = 0; i < tokenEmbeddings.length; i++) {
            if (i < mask.length && mask[i] == 1) {
                for (int j = 0; j < EMBEDDING_DIM; j++) {
                    pooled[j] += tokenEmbeddings[i][j];
                }
                count++;
            }
        }

        if (count > 0) {
            for (int j = 0; j < EMBEDDING_DIM; j++) {
                pooled[j] /= count;
            }
        }

        // Normalização L2 para similaridade por cosseno
        float sumSquares = 0.0f;
        for (float v : pooled) {
            sumSquares += v * v;
        }
        float norm = (float) Math.sqrt(sumSquares);

        if (norm > 0.0f) {
            for (int j = 0; j < EMBEDDING_DIM; j++) {
                pooled[j] /= norm;
            }
        }

        return pooled;
    }

    @Override
    public void close() {
        if (tokenizer != null) {
            tokenizer.close();
        }
        if (session != null) {
            try {
                session.close();
            } catch (OrtException e) {
                System.err.println("Erro ao fechar OrtSession: " + e.getMessage());
            }
        }
        if (env != null) {
            env.close();
        }
    }
}

