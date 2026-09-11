package com.jloads.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Quando a interface está embutida no jar ({@code classpath:static/index.html}), rotas do Angular abertas
 * diretamente no navegador (ex.: {@code /settings}) recebem o index.html para o roteador do frontend assumir.
 * API, Actuator e WebSocket nunca são capturados.
 */
@Controller
@ConditionalOnResource(resources = "classpath:static/index.html")
public class SpaForwardingController {

    @GetMapping("/{route:^(?!api$|actuator$|ws$)[a-z][a-z-]*$}")
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
