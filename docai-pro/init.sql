use docai;

-- 用户�?
CREATE TABLE IF NOT EXISTS users
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID，主键，自增长，�?0000001开�?,
    username      VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名，唯一标识，用于登录和显示',
    email         VARCHAR(100) UNIQUE COMMENT '邮箱地址，唯一，用于登录和通知',
    password_hash VARCHAR(255) COMMENT '密码哈希值，使用BCrypt加密存储',
    INDEX idx_email (email),
    INDEX idx_username (username)
) COMMENT '用户�? CHARSET = utf8mb4  AUTO_INCREMENT = 10000001;


-- 文件服务相关的sql
-- 创建文件�?
CREATE TABLE IF NOT EXISTS files
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '文件唯一标识，主键，自增长，�?0000001开�?,
    user_id       BIGINT       NOT NULL COMMENT '用户ID，关联用户表的id，表示文件所属用�?,
    file_name     VARCHAR(255) NOT NULL COMMENT '原始文件名，包含文件扩展�?,
    file_path     VARCHAR(500) COMMENT '文件存储路径，相对路径或绝对路径',
    file_size     BIGINT COMMENT '文件大小，单位：字节',
    oss_key       VARCHAR(300) COMMENT '对象存储服务中的文件唯一标识key',
    upload_status TINYINT   DEFAULT 1 COMMENT '上传状态：1-上传成功�?-上传失败�?-上传中，3-处理中，4-转换完成',
    INDEX idx_user_id (user_id) COMMENT '用户ID索引，加速按用户查询',
    INDEX idx_upload_status (upload_status) COMMENT '上传状态索�?
) COMMENT '文件表，用于存储用户上传的文件信�? CHARSET = utf8mb4 AUTO_INCREMENT = 10000001;

-- 创建文件与表名映射关系表（支持单文件多sheet场景�?
CREATE TABLE IF NOT EXISTS file_table_mappings
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    file_id     BIGINT       NOT NULL COMMENT '文件ID，关联files.id',
    table_name  VARCHAR(100) NOT NULL COMMENT 'MySQL表名',
    sheet_index INT          NOT NULL DEFAULT 0 COMMENT 'sheet顺序索引，从0开�?,
    sheet_name  VARCHAR(100)          DEFAULT NULL COMMENT '原始sheet名称',
    UNIQUE KEY uk_file_table (file_id, table_name),
    INDEX idx_file_id (file_id),
    INDEX idx_sheet_index (sheet_index)
) COMMENT '文件与MySQL表关系映射（多sheet�? CHARSET = utf8mb4 AUTO_INCREMENT = 10000001;

-- 创建字段映射关系�?
CREATE TABLE IF NOT EXISTS field_mappings
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '映射关系ID，主键，自增�?,
    file_id         BIGINT       NOT NULL COMMENT '文件ID，关联files表的id',
    table_name      VARCHAR(100) NOT NULL COMMENT 'MySQL表名',
    db_field_name   VARCHAR(100) NOT NULL COMMENT '数据库字段名',
    original_header VARCHAR(255) NOT NULL COMMENT '原始Excel表头',
    field_order     INT          NOT NULL DEFAULT 0 COMMENT '字段在Excel中的顺序',
    UNIQUE KEY uk_file_db_field (file_id, db_field_name),
    INDEX idx_file_id (file_id),
    INDEX idx_table_name (table_name)
) COMMENT 'Excel表头与MySQL字段映射关系�? CHARSET = utf8mb4 AUTO_INCREMENT = 10000001;

-- 创建AI服务请求记录�?
CREATE TABLE IF NOT EXISTS ai_requests
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，自增长，从10000001开�?,
    user_id      BIGINT   NOT NULL COMMENT '用户ID，关联用户表',
    file_id      BIGINT NOT NULL COMMENT '文件ID，关联文件表',
    user_input   TEXT     NOT NULL COMMENT '用户输入的请求内�?,
    ai_response  TEXT COMMENT 'AI返回的响应内�?,
    status       TINYINT  DEFAULT 0 COMMENT '请求状态：0-处理中�?-成功�?-失败�?-部分成功',
    INDEX idx_user_id (user_id) COMMENT '用户id索引'
) COMMENT 'AI服务请求记录�? CHARSET = utf8mb4 AUTO_INCREMENT = 10000001;

-- =====================================================
-- 非结构化信息提取与自动填表相关表
-- =====================================================

-- 源文档表
CREATE TABLE IF NOT EXISTS source_documents
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '文档ID',
    user_id       BIGINT       NOT NULL COMMENT '用户ID',
    file_name     VARCHAR(255) NOT NULL COMMENT '文件�?,
    file_type     VARCHAR(20)  NOT NULL COMMENT '文件类型：docx/md/txt/xlsx/pdf',
    storage_path  VARCHAR(500) COMMENT '存储路径',
    oss_key       VARCHAR(300) COMMENT 'OSS存储key',
    file_size     BIGINT COMMENT '文件大小（字节）',
    upload_status VARCHAR(20)  DEFAULT 'uploaded' COMMENT '状态：uploaded/parsing/parsed/failed',
    doc_summary   TEXT COMMENT '文档摘要',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_upload_status (upload_status)
) COMMENT '源文档表' CHARSET = utf8mb4 AUTO_INCREMENT = 10000001;

-- 抽取字段�?
CREATE TABLE IF NOT EXISTS extracted_fields
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '字段ID',
    doc_id          BIGINT       NOT NULL COMMENT '源文档ID',
    field_key       VARCHAR(100) NOT NULL COMMENT '标准化字段键',
    field_name      VARCHAR(200) NOT NULL COMMENT '原始字段�?,
    field_value     TEXT COMMENT '字段�?,
    field_type      VARCHAR(20)  DEFAULT 'text' COMMENT '字段类型：text/date/number/phone/org/person/enum',
    aliases         JSON COMMENT '别名列表',
    source_text     TEXT COMMENT '原文证据',
    source_location VARCHAR(200) COMMENT '原文位置',
    confidence      DECIMAL(5,4) DEFAULT 0.0 COMMENT '置信�?-1',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc_id (doc_id),
    INDEX idx_field_key (field_key),
    INDEX idx_field_type (field_type)
) COMMENT '抽取字段�? CHARSET = utf8mb4 AUTO_INCREMENT = 10000001;

-- 字段别名词典�?
CREATE TABLE IF NOT EXISTS field_alias_dict
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    standard_key    VARCHAR(100) NOT NULL COMMENT '标准化字段键',
    alias_name      VARCHAR(200) NOT NULL COMMENT '别名',
    field_type      VARCHAR(20)  DEFAULT 'text' COMMENT '字段类型',
    UNIQUE KEY uk_key_alias (standard_key, alias_name),
    INDEX idx_standard_key (standard_key),
    INDEX idx_alias_name (alias_name)
) COMMENT '字段别名词典�? CHARSET = utf8mb4 AUTO_INCREMENT = 10000001;

-- 模板文件�?
CREATE TABLE IF NOT EXISTS template_files
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '模板ID',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    file_name       VARCHAR(255) NOT NULL COMMENT '文件�?,
    template_type   VARCHAR(20)  NOT NULL COMMENT '模板类型：docx/xlsx',
    storage_path    VARCHAR(500) COMMENT '存储路径',
    oss_key         VARCHAR(300) COMMENT 'OSS存储key',
    file_size       BIGINT COMMENT '文件大小',
    parse_status    VARCHAR(20)  DEFAULT 'uploaded' COMMENT '状态：uploaded/parsing/parsed/failed',
    slot_count      INT          DEFAULT 0 COMMENT '槽位数量',
    output_path     VARCHAR(500) COMMENT '填写结果文件路径',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) COMMENT '模板文件�? CHARSET = utf8mb4 AUTO_INCREMENT = 10000001;

-- 模板槽位�?
CREATE TABLE IF NOT EXISTS template_slots
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '槽位ID',
    template_id     BIGINT       NOT NULL COMMENT '模板ID',
    slot_id         VARCHAR(100) COMMENT '槽位标识',
    label           VARCHAR(200) NOT NULL COMMENT '槽位标签',
    context         VARCHAR(500) COMMENT '上下文信�?,
    position        JSON COMMENT '位置信息',
    expected_type   VARCHAR(20)  DEFAULT 'text' COMMENT '期望类型',
    required_flag   TINYINT      DEFAULT 1 COMMENT '是否必填',
    slot_type       VARCHAR(30)  DEFAULT 'adjacent_blank' COMMENT '槽位类型：adjacent_blank/right_blank/below_blank/inline',
    INDEX idx_template_id (template_id)
) COMMENT '模板槽位�? CHARSET = utf8mb4 AUTO_INCREMENT = 10000001;

-- 填写决策�?
CREATE TABLE IF NOT EXISTS fill_decisions
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    audit_id         VARCHAR(100) COMMENT '审计批次ID',
    template_id      BIGINT COMMENT '模板ID',
    slot_id          VARCHAR(100) NOT NULL COMMENT '槽位标识',
    slot_label       VARCHAR(200) COMMENT '槽位标签',
    final_value      TEXT COMMENT '最终填写�?,
    final_field_id   VARCHAR(100) COMMENT '最终字段ID',
    final_confidence DECIMAL(5,4) DEFAULT 0.0 COMMENT '最终置信度',
    decision_mode    VARCHAR(30)  DEFAULT 'rule_only' COMMENT '决策模式：rule_only/rule_plus_llm/fallback_blank',
    reason           TEXT COMMENT '决策原因',
    INDEX idx_audit_id (audit_id),
    INDEX idx_template_id (template_id),
    INDEX idx_slot_id (slot_id)
) COMMENT '填写决策�? CHARSET = utf8mb4 AUTO_INCREMENT = 10000001;

-- 填写审计日志表（每个槽位一条记录）
CREATE TABLE IF NOT EXISTS fill_audit_logs
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    audit_id            VARCHAR(100) COMMENT '审计批次ID',
    template_id         BIGINT NOT NULL COMMENT '模板ID',
    user_id             BIGINT COMMENT '用户ID',
    slot_id             VARCHAR(100) COMMENT '槽位标识',
    slot_label          VARCHAR(200) COMMENT '槽位标签',
    final_value         TEXT COMMENT '最终填写�?,
    final_confidence    DECIMAL(5,4) DEFAULT 0.0 COMMENT '最终置信度',
    decision_mode       VARCHAR(30) COMMENT '决策模式',
    source_doc_name     VARCHAR(255) COMMENT '来源文档�?,
    source_text         TEXT COMMENT '原文证据',
    reason              TEXT COMMENT '决策原因',
    candidates_summary  TEXT COMMENT '候选摘�?,
    created_at          DATETIME    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_id (audit_id),
    INDEX idx_template_id (template_id)
) COMMENT '填写审计日志表' CHARSET = utf8mb4 AUTO_INCREMENT = 10000001;

-- =====================================================
-- 聊天会话与消息表
-- =====================================================

-- 聊天会话表
CREATE TABLE IF NOT EXISTS chat_conversations
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    title           VARCHAR(255) DEFAULT '新对话' COMMENT '会话标题',
    linked_doc_id   BIGINT       DEFAULT NULL COMMENT '关联文档ID',
    linked_doc_name VARCHAR(255) DEFAULT NULL COMMENT '关联文档名称',
    pinned          TINYINT(1)   DEFAULT 0 COMMENT '是否置顶',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_updated_at (updated_at)
) COMMENT '聊天会话表' CHARSET = utf8mb4 AUTO_INCREMENT = 10000001;

-- 聊天消息表
CREATE TABLE IF NOT EXISTS chat_messages
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    conversation_id BIGINT       NOT NULL COMMENT '会话ID',
    role            VARCHAR(20)  NOT NULL COMMENT '角色：user/ai',
    content         LONGTEXT COMMENT '消息内容',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_conversation_id (conversation_id),
    CONSTRAINT fk_chat_msg_conv FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE
) COMMENT '聊天消息表' CHARSET = utf8mb4 AUTO_INCREMENT = 10000001;