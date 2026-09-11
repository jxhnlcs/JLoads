package com.jloads.controller;

import com.jloads.config.AppProperties;
import com.jloads.dto.PublicConfigResponse;
import com.jloads.process.ExternalBinaries;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final AppProperties properties;
    private final ExternalBinaries binaries;

    @GetMapping
    public PublicConfigResponse get() {
        return PublicConfigResponse.from(properties, binaries);
    }
}
