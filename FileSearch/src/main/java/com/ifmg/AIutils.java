package com.ifmg;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class AIutils {

    public static class Result {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode; this.stdout = stdout; this.stderr = stderr;
        }
    }

    public static Result runCommand(List<String> command, Duration timeout) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false); // keep stderr separate
        Process proc = pb.start();

        ExecutorService ex = Executors.newFixedThreadPool(2);
        Future<String> outF = streamToStringAsync(proc.getInputStream(), ex);
        Future<String> errF = streamToStringAsync(proc.getErrorStream(), ex);

        boolean finished = proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            // timeout -> attempt graceful destroy then force
            destroyProcessTree(proc);
            if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
            }
        }

        int exit = -1;
        try {
            exit = proc.exitValue();
        } catch (IllegalThreadStateException e) {
            // still running (shouldn't happen after destroy attempts)
            destroyProcessTree(proc);
            proc.destroyForcibly();
            exit = proc.waitFor();
        }

        String stdout = outF.get(1, TimeUnit.SECONDS);
        String stderr = errF.get(1, TimeUnit.SECONDS);

        ex.shutdownNow();
        return new Result(exit, stdout, stderr);
    }

    private static Future<String> streamToStringAsync(InputStream in, ExecutorService ex) {
        return ex.submit(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append(System.lineSeparator());
                }
                return sb.toString();
            }
        });
    }

    private static void destroyProcessTree(Process proc) {
        // Java 9+ ProcessHandle API - kill descendants first
        try {
            ProcessHandle handle = proc.toHandle();
            // kill descendants
            handle.descendants().forEach(ph -> {
                try {
                    ph.destroy();
                } catch (Exception ignored) {}
            });
            // then kill parent
            handle.destroy();
            // give some time; caller may call destroyForcibly if needed
        } catch (Exception ex) {
            // fallback: use destroy on Process
            try { proc.destroy(); } catch (Exception ignored) {}
        }
    }

    // exemplo de uso
    public static void main(String[] args) throws Exception {
        String prompt = "Pergunta: Qual o melhor país do mundo?"; // seu prompt
        // escrevendo prompt em arquivo temporário (opcional, recomendado para prompts longos)
        Path tmp = Files.createTempFile("ollama-prompt-", ".txt");
        Files.write(tmp, prompt.getBytes());
        try {
            List<String> cmd = Arrays.asList(
                "ollama", "run",
                "gemma3:4b",
                prompt
                //tmp.toAbsolutePath().toString() // se sua versão do CLI aceitar
                // se não aceitar --prompt-file, você pode passar o prompt por stdin
            );

            Duration timeout = Duration.ofSeconds(50); // ajuste conforme necessidade
            Result r = runCommand(cmd, timeout);
            System.out.println("exit=" + r.exitCode);
            System.out.println("stdout: " + r.stdout);
            System.err.println("stderr: " + r.stderr);

            // opcional: após isso, você pode chamar uma função que garanta matar (ex: por CMD) - mas preferível não precisar
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}

