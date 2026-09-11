package com.jloads.controller;

import com.jloads.dto.CreateBatchDownloadRequest;
import com.jloads.dto.CreateBatchDownloadResponse;
import com.jloads.dto.CreateDownloadRequest;
import com.jloads.dto.CreateDownloadResponse;
import com.jloads.dto.DownloadResponse;
import com.jloads.model.ClientId;
import com.jloads.model.DownloadJobSnapshot;
import com.jloads.model.StoredFile;
import com.jloads.service.DownloadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/downloads")
@RequiredArgsConstructor
public class DownloadController {

    private final DownloadService downloadService;

    @PostMapping
    public ResponseEntity<CreateDownloadResponse> create(@Valid @RequestBody CreateDownloadRequest request,
                                                         ClientId clientId, HttpServletRequest http) {
        DownloadJobSnapshot job = downloadService.create(
                request.url(), request.type(), request.quality(), clientId, http.getRemoteAddr());
        return ResponseEntity.accepted()
                .location(URI.create("/api/downloads/" + job.id()))
                .body(CreateDownloadResponse.from(job));
    }

    @PostMapping("/batch")
    public ResponseEntity<CreateBatchDownloadResponse> createBatch(@Valid @RequestBody CreateBatchDownloadRequest request,
                                                                   ClientId clientId, HttpServletRequest http) {
        List<DownloadJobSnapshot> jobs = downloadService.createBatch(
                request.urls(), request.type(), request.quality(), clientId, http.getRemoteAddr());
        return ResponseEntity.accepted().body(CreateBatchDownloadResponse.from(jobs));
    }

    @GetMapping
    public List<DownloadResponse> list(ClientId clientId) {
        return downloadService.list(clientId).stream().map(DownloadResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DownloadResponse get(@PathVariable UUID id) {
        return DownloadResponse.from(downloadService.get(id));
    }

    @DeleteMapping("/{id}")
    public DownloadResponse cancel(@PathVariable UUID id, ClientId clientId) {
        return DownloadResponse.from(downloadService.cancel(id, clientId));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable UUID id) {
        StoredFile file = downloadService.getCompletedFile(id);
        MediaType mediaType = MediaTypeFactory.getMediaType(file.filename()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(file.sizeBytes())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file.path()));
    }
}
