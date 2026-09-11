package com.jloads.model;

import java.nio.file.Path;

/** Arquivo final armazenado para um job. */
public record StoredFile(String filename, Path path, long sizeBytes) {
}
