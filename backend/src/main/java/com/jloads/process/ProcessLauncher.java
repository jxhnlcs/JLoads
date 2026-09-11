package com.jloads.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Abstração para iniciar processos do sistema operacional (substituível em testes). */
public interface ProcessLauncher {

    /**
     * Inicia um processo com a lista de argumentos exata informada — sem shell, sem interpolação.
     *
     * @param command              executável e argumentos, já validados
     * @param workingDirectory     diretório de trabalho; {@code null} usa o atual
     * @param mergeErrorIntoOutput se {@code true}, stderr é redirecionado para stdout
     */
    Process start(List<String> command, Path workingDirectory, boolean mergeErrorIntoOutput) throws IOException;
}
