package com.jloads.controller;

import com.jloads.dto.AnalyzeVideoRequest;
import com.jloads.dto.AnalyzeVideoResponse;
import com.jloads.service.VideoAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoAnalysisService analysisService;

    @PostMapping("/analyze")
    public AnalyzeVideoResponse analyze(@Valid @RequestBody AnalyzeVideoRequest request) {
        return AnalyzeVideoResponse.from(analysisService.analyze(request.url()));
    }
}
