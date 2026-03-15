package com.docai.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docai.ai.dto.request.AddMessageRequest;
import com.docai.ai.dto.request.CreateConversationRequest;
import com.docai.ai.dto.request.UpdateConversationRequest;
import com.docai.ai.entity.ChatConversationEntity;
import com.docai.ai.entity.ChatMessageEntity;
import com.docai.ai.mapper.ChatConversationMapper;
import com.docai.ai.mapper.ChatMessageMapper;
import com.docai.common.util.JwtUtil;
import com.docai.common.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/ai/conversations")
public class ChatConversationController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ChatConversationMapper conversationMapper;

    @Autowired
    private ChatMessageMapper messageMapper;

    // 获取当前用户的所有对话
    @GetMapping
    public Result<List<ChatConversationEntity>> listConversations(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.error(Result.ResultCode.UNAUTHORIZED, "无效令牌");

        List<ChatConversationEntity> list = conversationMapper.selectList(
                new LambdaQueryWrapper<ChatConversationEntity>()
                        .eq(ChatConversationEntity::getUserId, userId)
                        .orderByDesc(ChatConversationEntity::getPinned)
                        .orderByDesc(ChatConversationEntity::getUpdatedAt)
        );
        return Result.success("success", list);
    }

    // 创建新对话
    @PostMapping
    public Result<ChatConversationEntity> createConversation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) CreateConversationRequest request) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.error(Result.ResultCode.UNAUTHORIZED, "无效令牌");

        try {
            ChatConversationEntity entity = new ChatConversationEntity();
            entity.setUserId(userId);
            entity.setTitle(request != null && request.getTitle() != null ? request.getTitle() : "新对话");
            entity.setLinkedDocId(request != null ? request.getLinkedDocId() : null);
            entity.setLinkedDocName(request != null ? request.getLinkedDocName() : null);
            entity.setPinned(false);
            conversationMapper.insert(entity);
            return Result.success("创建成功", entity);
        } catch (Exception e) {
            return Result.error(Result.ResultCode.SERVER_ERROR, "创建对话失败: " + e.getMessage());
        }
    }

    // 更新对话元信息
    @PutMapping("/{id}")
    public Result<Void> updateConversation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody(required = false) UpdateConversationRequest request) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.error(Result.ResultCode.UNAUTHORIZED, "无效令牌");

        ChatConversationEntity entity = conversationMapper.selectById(id);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return Result.error(Result.ResultCode.NOT_FOUND, "对话不存在");
        }

        if (request != null) {
            if (request.getTitle() != null) entity.setTitle(request.getTitle());
            if (request.getLinkedDocId() != null) entity.setLinkedDocId(request.getLinkedDocId());
            if (request.getLinkedDocName() != null) entity.setLinkedDocName(request.getLinkedDocName());
            if (request.getPinned() != null) entity.setPinned(request.getPinned());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(entity);
        return Result.success("更新成功", null);
    }

    // 删除对话
    @DeleteMapping("/{id}")
    public Result<Void> deleteConversation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.error(Result.ResultCode.UNAUTHORIZED, "无效令牌");

        ChatConversationEntity entity = conversationMapper.selectById(id);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return Result.error(Result.ResultCode.NOT_FOUND, "对话不存在");
        }

        // Messages are cascade-deleted by FK
        conversationMapper.deleteById(id);
        return Result.success("删除成功", null);
    }

    // 获取对话消息列表
    @GetMapping("/{id}/messages")
    public Result<List<ChatMessageEntity>> getMessages(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.error(Result.ResultCode.UNAUTHORIZED, "无效令牌");

        ChatConversationEntity entity = conversationMapper.selectById(id);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return Result.error(Result.ResultCode.NOT_FOUND, "对话不存在");
        }

        List<ChatMessageEntity> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getConversationId, id)
                        .orderByAsc(ChatMessageEntity::getCreatedAt)
        );
        return Result.success("success", messages);
    }

    // 添加消息到对话
    @PostMapping("/{id}/messages")
    public Result<ChatMessageEntity> addMessage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @Validated @RequestBody AddMessageRequest request) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.error(Result.ResultCode.UNAUTHORIZED, "无效令牌");

        ChatConversationEntity conv = conversationMapper.selectById(id);
        if (conv == null || !conv.getUserId().equals(userId)) {
            return Result.error(Result.ResultCode.NOT_FOUND, "对话不存在");
        }

        String role = request.getRole();
        if (!"user".equals(role) && !"ai".equals(role)) {
            return Result.error(Result.ResultCode.BAD_REQUEST, "角色必须为 user 或 ai");
        }

        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setConversationId(id);
        msg.setRole(role);
        msg.setContent(request.getContent());
        messageMapper.insert(msg);

        // 更新对话的 updatedAt 
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);

        return Result.success("success", msg);
    }
}
