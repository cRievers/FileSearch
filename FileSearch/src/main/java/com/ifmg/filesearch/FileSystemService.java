package com.ifmg.filesearch;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

public class FileSystemService {

    public void abrirArquivo(File arquivo) throws IOException {
        if (Desktop.isDesktopSupported() && arquivo.exists()) {
            Desktop.getDesktop().open(arquivo);
        } else {
            throw new IOException("Recurso não suportado ou arquivo inexistente.");
        }
    }

    public void revelarNoExplorer(File arquivo) throws IOException {
        if (!Desktop.isDesktopSupported()) {
            throw new UnsupportedOperationException("Desktop API não suportada.");
        }

        Desktop desktop = Desktop.getDesktop();

        // Verifica se o SO suporta a ação específica de selecionar o arquivo na pasta
        if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
            desktop.browseFileDirectory(arquivo);
        } else {
            // Fallback: Se não suportar destacar o arquivo, apenas abre a pasta pai
            if (arquivo.getParentFile() != null) {
                desktop.open(arquivo.getParentFile());
            }
        }
    }
}
