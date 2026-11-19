package com.florin.settings.dto;

import java.util.List;

public record EmailTemplatesEnvelope(boolean success, List<EmailTemplateResponse> templates) {}
