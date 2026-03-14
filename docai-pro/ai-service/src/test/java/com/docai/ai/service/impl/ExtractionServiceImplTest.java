package com.docai.ai.service.impl;

import com.docai.ai.entity.ExtractedFieldEntity;
import com.docai.ai.mapper.ExtractedFieldMapper;
import com.docai.ai.mapper.FieldAliasDictMapper;
import com.docai.ai.mapper.SourceDocumentMapper;
import com.docai.ai.service.LlmService;
import com.docai.common.service.OssService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ExtractionServiceImplTest {

    @InjectMocks
    private ExtractionServiceImpl extractionService;

    @Mock private SourceDocumentMapper sourceDocumentMapper;
    @Mock private ExtractedFieldMapper extractedFieldMapper;
    @Mock private FieldAliasDictMapper fieldAliasDictMapper;
    @Mock private LlmService llmService;
    @Mock private OssService ossService;

    // 反射调用私有方法的辅助
    private Object invoke(String methodName, Object... args) throws Exception {
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
        }
        Method method = ExtractionServiceImpl.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(extractionService, args);
    }

    @Nested
    @DisplayName("normalizeKey - 中文字段名到标准英文key的映射")
    class NormalizeKeyTest {
        @Test
        void shouldNormalizeProjectName() throws Exception {
            assertEquals("project_name", invoke("normalizeKey", "项目名称"));
            assertEquals("project_name", invoke("normalizeKey", "课题名称"));
            assertEquals("project_name", invoke("normalizeKey", "申报项目名称"));
        }

        @Test
        void shouldNormalizeOwner() throws Exception {
            assertEquals("owner", invoke("normalizeKey", "负责人"));
            assertEquals("owner", invoke("normalizeKey", "项目负责人"));
        }

        @Test
        void shouldNormalizeOrgName() throws Exception {
            assertEquals("org_name", invoke("normalizeKey", "单位名称"));
            assertEquals("org_name", invoke("normalizeKey", "承担单位"));
        }

        @Test
        void shouldNormalizePhone() throws Exception {
            assertEquals("phone", invoke("normalizeKey", "联系电话"));
            assertEquals("phone", invoke("normalizeKey", "手机号码"));
            assertEquals("phone", invoke("normalizeKey", "手机号"));
        }

        @Test
        void shouldNormalizeEmail() throws Exception {
            assertEquals("email", invoke("normalizeKey", "电子邮箱"));
            assertEquals("email", invoke("normalizeKey", "邮箱"));
        }

        @Test
        void shouldReturnLowercaseForUnknown() throws Exception {
            assertEquals("unknown_field", invoke("normalizeKey", "unknown_field"));
        }
    }

    @Nested
    @DisplayName("detectFieldType - 自动检测字段类型")
    class DetectFieldTypeTest {
        @Test
        void shouldDetectPhone() throws Exception {
            assertEquals("phone", invoke("detectFieldType", "联系电话", "13800138000"));
            assertEquals("phone", invoke("detectFieldType", "手机号", "18612345678"));
        }

        @Test
        void shouldDetectPhoneByValuePattern() throws Exception {
            assertEquals("phone", invoke("detectFieldType", "联系方式", "13912345678"));
        }

        @Test
        void shouldDetectDate() throws Exception {
            assertEquals("date", invoke("detectFieldType", "开始日期", "2024-01-01"));
            assertEquals("date", invoke("detectFieldType", "截止时间", "2024年12月31日"));
        }

        @Test
        void shouldDetectOrg() throws Exception {
            assertEquals("org", invoke("detectFieldType", "申报单位", "清华大学"));
            assertEquals("org", invoke("detectFieldType", "承担机构", "中国科学院"));
        }

        @Test
        void shouldDetectNumber() throws Exception {
            assertEquals("number", invoke("detectFieldType", "经费", "500000"));
            assertEquals("number", invoke("detectFieldType", "金额", "123.45"));
        }

        @Test
        void shouldDefaultToText() throws Exception {
            assertEquals("text", invoke("detectFieldType", "备注", "这是一段普通文本"));
        }
    }

    @Nested
    @DisplayName("extractJsonFromResponse - 从LLM响应中提取JSON")
    class ExtractJsonTest {
        @Test
        void shouldExtractFromMarkdownCodeBlock() throws Exception {
            String response = "```json\n{\"fields\":[]}\n```";
            assertEquals("{\"fields\":[]}", invoke("extractJsonFromResponse", response));
        }

        @Test
        void shouldExtractFromPlainCodeBlock() throws Exception {
            String response = "```\n{\"data\":true}\n```";
            assertEquals("{\"data\":true}", invoke("extractJsonFromResponse", response));
        }

        @Test
        void shouldReturnPlainJsonAsIs() throws Exception {
            String response = "{\"test\":1}";
            assertEquals("{\"test\":1}", invoke("extractJsonFromResponse", response));
        }
    }

    @Nested
    @DisplayName("getFileType - 文件扩展名提取")
    class GetFileTypeTest {
        @Test
        void shouldExtractDocx() throws Exception {
            assertEquals("docx", invoke("getFileType", "report.docx"));
        }

        @Test
        void shouldExtractXlsx() throws Exception {
            assertEquals("xlsx", invoke("getFileType", "data.xlsx"));
        }

        @Test
        void shouldExtractTxt() throws Exception {
            assertEquals("txt", invoke("getFileType", "notes.txt"));
        }

        @Test
        void shouldExtractMd() throws Exception {
            assertEquals("md", invoke("getFileType", "readme.md"));
        }

        @Test
        void shouldDefaultToTxtForNoExtension() throws Exception {
            assertEquals("txt", invoke("getFileType", "noext"));
        }

        @Test
        void shouldHandleNull() throws Exception {
            Method method = ExtractionServiceImpl.class.getDeclaredMethod("getFileType", String.class);
            method.setAccessible(true);
            assertEquals("txt", method.invoke(extractionService, (String) null));
        }
    }


    @Nested
    @DisplayName("ruleBasedExtract - 规则兜底提取")
    class RuleBasedExtractTest {
        @SuppressWarnings("unchecked")
        private List<ExtractedFieldEntity> callRuleBasedExtract(Long docId, String text) throws Exception {
            Method method = ExtractionServiceImpl.class.getDeclaredMethod("ruleBasedExtract", Long.class, String.class);
            method.setAccessible(true);
            return (List<ExtractedFieldEntity>) method.invoke(extractionService, docId, text);
        }

        @Test
        void shouldExtractKeyValuePairs() throws Exception {
            String text = "项目名称：智能文档系统\n负责人：张三\n联系电话：13800138000";
            List<ExtractedFieldEntity> fields = callRuleBasedExtract(1L, text);

            assertEquals(3, fields.size());
            assertEquals("智能文档系统", fields.get(0).getFieldValue());
            assertEquals("张三", fields.get(1).getFieldValue());
            assertEquals("13800138000", fields.get(2).getFieldValue());
        }

        @Test
        void shouldSetCorrectDocId() throws Exception {
            String text = "项目名称：测试项目";
            List<ExtractedFieldEntity> fields = callRuleBasedExtract(42L, text);

            assertEquals(1, fields.size());
            assertEquals(42L, fields.get(0).getDocId());
        }

        @Test
        void shouldSetConfidenceTo078() throws Exception {
            String text = "负责人：李四";
            List<ExtractedFieldEntity> fields = callRuleBasedExtract(1L, text);

            assertEquals(new BigDecimal("0.7800"), fields.get(0).getConfidence());
        }

        @Test
        void shouldSkipEmptyValues() throws Exception {
            String text = "项目名称：\n负责人：张三";
            List<ExtractedFieldEntity> fields = callRuleBasedExtract(1L, text);

            assertEquals(1, fields.size());
            assertEquals("张三", fields.get(0).getFieldValue());
        }

        @Test
        void shouldSkipCommentsAndEmpty() throws Exception {
            String text = "# 这是注释\n\n负责人：张三";
            List<ExtractedFieldEntity> fields = callRuleBasedExtract(1L, text);

            assertEquals(1, fields.size());
        }

        @Test
        void shouldHandleNullInput() throws Exception {
            Method method = ExtractionServiceImpl.class.getDeclaredMethod("ruleBasedExtract", Long.class, String.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<ExtractedFieldEntity> fields = (List<ExtractedFieldEntity>) method.invoke(extractionService, 1L, null);
            assertTrue(fields.isEmpty());
        }

        @Test
        void shouldSupportColonVariants() throws Exception {
            String text = "项目名称:英文冒号项目\n负责人：中文冒号负责人";
            List<ExtractedFieldEntity> fields = callRuleBasedExtract(1L, text);

            assertEquals(2, fields.size());
        }
    }

    @Nested
    @DisplayName("standardizeField - 字段标准化")
    class StandardizeFieldTest {
        private void callStandardize(ExtractedFieldEntity field) throws Exception {
            Method method = ExtractionServiceImpl.class.getDeclaredMethod("standardizeField", ExtractedFieldEntity.class);
            method.setAccessible(true);
            method.invoke(extractionService, field);
        }

        @Test
        void shouldCleanFieldName() throws Exception {
            ExtractedFieldEntity field = new ExtractedFieldEntity();
            field.setFieldName("项目名称：");
            field.setFieldValue("  测试项目  ");
            field.setFieldType("text");

            callStandardize(field);

            assertEquals("项目名称", field.getFieldName());
            assertEquals("测试项目", field.getFieldValue());
        }

        @Test
        void shouldNormalizeDateField() throws Exception {
            ExtractedFieldEntity field = new ExtractedFieldEntity();
            field.setFieldName("开始日期");
            field.setFieldValue("2024年03月15日");
            field.setFieldType("date");

            callStandardize(field);

            assertEquals("2024-03-15", field.getFieldValue());
        }

        @Test
        void shouldCleanPhoneField() throws Exception {
            ExtractedFieldEntity field = new ExtractedFieldEntity();
            field.setFieldName("联系电话");
            field.setFieldValue("138-0013-8000");
            field.setFieldType("phone");

            callStandardize(field);

            assertEquals("13800138000", field.getFieldValue());
        }

        @Test
        void shouldSetStandardizedKey() throws Exception {
            ExtractedFieldEntity field = new ExtractedFieldEntity();
            field.setFieldName("项目名称");
            field.setFieldValue("测试");
            field.setFieldType("text");

            callStandardize(field);

            assertEquals("project_name", field.getFieldKey());
        }
    }

    @Nested
    @DisplayName("normalizeDateValue - 日期格式标准化")
    class NormalizeDateTest {
        @Test
        void shouldNormalizeChineseDate() throws Exception {
            assertEquals("2024-03-15", invoke("normalizeDateValue", "2024年03月15日"));
        }

        @Test
        void shouldNormalizeSlashDate() throws Exception {
            assertEquals("2024-03-15", invoke("normalizeDateValue", "2024/03/15"));
        }

        @Test
        void shouldKeepDashDateUnchanged() throws Exception {
            assertEquals("2024-03-15", invoke("normalizeDateValue", "2024-03-15"));
        }
    }
}
