package com.jloads.support;

import com.jloads.process.ProcessLauncher;
import com.jloads.process.SystemProcessLauncher;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Executa {@link FakeYtDlp} numa JVM separada no lugar do binário real, mantendo os argumentos gerados. */
public class FakeYtDlpProcessLauncher implements ProcessLauncher {

    private final SystemProcessLauncher delegate = new SystemProcessLauncher();

    @Override
    public Process start(List<String> command, Path workingDirectory, boolean mergeErrorIntoOutput) throws IOException {
        List<String> fake = new ArrayList<>();
        fake.add(ProcessHandle.current().info().command().orElse("java"));
        fake.add("-Dstdout.encoding=UTF-8");
        fake.add("-Dstderr.encoding=UTF-8");
        fake.add("-cp");
        fake.add(classesDirectory());
        fake.add(FakeYtDlp.class.getName());
        fake.addAll(command.subList(1, command.size()));
        return delegate.start(fake, workingDirectory, mergeErrorIntoOutput);
    }

    private static String classesDirectory() {
        try {
            return Path.of(FakeYtDlp.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
