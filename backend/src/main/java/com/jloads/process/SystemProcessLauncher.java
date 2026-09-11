package com.jloads.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SystemProcessLauncher implements ProcessLauncher {

    @Override
    public Process start(List<String> command, Path workingDirectory, boolean mergeErrorIntoOutput) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(List.copyOf(command))
                .redirectErrorStream(mergeErrorIntoOutput);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        Map<String, String> env = builder.environment();
        env.put("PYTHONUTF8", "1");
        env.put("PYTHONIOENCODING", "utf-8");
        Process process = builder.start();
        process.getOutputStream().close();
        return process;
    }
}
