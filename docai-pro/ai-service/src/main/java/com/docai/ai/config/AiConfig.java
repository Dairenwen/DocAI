package com.docai.ai.config;

import com.docai.ai.function.EmailSendTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI相关配置信息
 */
@Configuration
public class AiConfig {

    // 初始化出一个全局的bean对象
    @Bean("defaultChatClient")
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }


    // 用于处理调用工具的chatClient
    @Bean("toolCallingChatClient")
    public ChatClient toolCallingChatClient(ChatModel chatModel, EmailSendTool emailSendTool) {
        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem("你是一个专业的AI助手，可以使用一下工具:\n" +
                        "1. sendEmail - 发送邮件\n" +
                        "   当用户要求发送邮件时，请使用sendEmail工具"
                )
                .defaultTools(emailSendTool)
                .build();
        return client;
    }

}
