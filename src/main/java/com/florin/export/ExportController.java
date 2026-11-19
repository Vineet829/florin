package com.florin.export;

import com.florin.business.BusinessAccess;
import com.florin.business.Requires;
import com.florin.business.Permission;
import com.florin.ratelimit.RateLimit;
import com.florin.ratelimit.RateLimiter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final DataExport dataExport;
    private final RateLimiter rateLimiter;

    public ExportController(DataExport dataExport, RateLimiter rateLimiter) {
        this.dataExport = dataExport;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/all")
    public ResponseEntity<byte[]> all(@Requires(Permission.EXPORT_ARCHIVE) BusinessAccess access) {
        rateLimiter.consume(RateLimit.WHOLE_EXPORT, String.valueOf(access.businessPkid()));
        DataExport.Archive archive = dataExport.all(access);
        return ResponseEntity.ok()
                .contentType(ZIP)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archive.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(archive.content());
    }
}
