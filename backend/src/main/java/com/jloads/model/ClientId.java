package com.jloads.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador anônimo do navegador que criou os jobs. Não é um mecanismo de autenticação: apenas
 * separa a lista de downloads de cada visitante enquanto não existe login.
 */
public record ClientId(UUID value) {

    public ClientId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
