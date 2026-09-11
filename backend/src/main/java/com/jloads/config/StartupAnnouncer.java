package com.jloads.config;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Informa o endereço da aplicação ao iniciar e, se configurado, abre o navegador (uso local). */
@Slf4j
@Component
public class StartupAnnouncer {

    private final Environment environment;
    private final boolean openBrowser;

    public StartupAnnouncer(Environment environment, @Value("${app.open-browser:false}") boolean openBrowser) {
        this.environment = environment;
        this.openBrowser = openBrowser;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String port = environment.getProperty("local.server.port");
        if (port == null) {
            return; // contexto sem servidor web (testes)
        }
        String url = "http://localhost:" + port;
        log.info("JLoads pronto em {}", url);
        if (openBrowser) {
            open(url);
        }
    }

    private static void open(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> command = os.contains("win") ? List.of("rundll32", "url.dll,FileProtocolHandler", url)
                : os.contains("mac") ? List.of("open", url)
                : List.of("xdg-open", url);
        try {
            new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException ex) {
            log.info("Abra {} no seu navegador", url);
        }
    }
}
