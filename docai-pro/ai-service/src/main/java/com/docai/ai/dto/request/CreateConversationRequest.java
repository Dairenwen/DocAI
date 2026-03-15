package com.docai.ai.dto.request;

import lombok.Data;

@Data
public class CreateConversationRequest {
    private String title;
    private Long linkedDocId;
    private String linkedDocName;
}
