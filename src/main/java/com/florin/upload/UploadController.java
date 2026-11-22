package com.florin.upload;

import com.florin.business.BusinessAccess;
import com.florin.business.Permission;
import com.florin.business.Requires;
import com.florin.ratelimit.RateLimit;
import com.florin.ratelimit.RateLimiter;
import com.florin.user.DemoAccountGuard;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    private final UploadService uploadService;
    private final RateLimiter rateLimiter;
    private final DemoAccountGuard demoAccountGuard;

    public UploadController(
            UploadService uploadService, RateLimiter rateLimiter, DemoAccountGuard demoAccountGuard) {
        this.uploadService = uploadService;
        this.rateLimiter = rateLimiter;
        this.demoAccountGuard = demoAccountGuard;
    }

    @PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String upload(
            @Requires(Permission.MANAGE_BUSINESS) BusinessAccess access, @RequestParam("logo") MultipartFile logo) {
        demoAccountGuard.requireNotDemo(access.user(), "uploading a logo");
        rateLimiter.consume(RateLimit.LOGO_UPLOAD, access.user().getId().toString());
        return uploadService.store(logo);
    }
}
