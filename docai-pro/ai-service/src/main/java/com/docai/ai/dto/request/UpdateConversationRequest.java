package com.docai.ai.dto.request;

import lombok.Data;

@Data
public class UpdateConversationRequest {
    private String title;
    private Long linkedDocId;
    private String linkedDocName;
    private Boolean pinned;
}
