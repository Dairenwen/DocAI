package com.docai.ai.service.impl;

import com.docai.ai.entity.*;
import com.docai.ai.mapper.*;
import com.docai.ai.service.LlmService;
import com.docai.common.service.OssService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TemplateFillServiceImplTest {

    @InjectMocks
    private TemplateFillServiceImpl templateFillService;

    @Mock private TemplateFileMapper templateFileMapper;
    @Mock private TemplateSlotMapper templateSlotMapper;
    @Mock private FillDecisionMapper fillDecisionMapper;
    @Mock private FillAuditLogMapper fillAuditLogMapper;
    @Mock private ExtractedFieldMapper extractedFieldMapper;
    @Mock private SourceDocumentMapper sourceDocumentMapper;
    @Mock private FieldAliasDictMapper fieldAliasDictMapper;
    @Mock private LlmService llmService;
    @Mock private OssService ossService;

    private Object invoke(String methodName, Object... args) throws Exception {
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i] == null ? String.class : args[i].getClass();
        }
        Method method = TemplateFillServiceImpl.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(templateFillService, args);
    }

    @Nested
    @DisplayName("isLabelCell - 判断是否为标签单元格")
    class IsLabelCellTest {
        @Test
        void shouldRejectNull() throws Exception {
            assertFalse((Boolean) invoke("isLabelCell", (Object) null));
        }

        @Test
        void shouldRejectEmpty() throws Exception {
            assertFalse((Boolean) invoke("isLabelCell", ""));
        }

        @Test
        void shouldRejectTooShort() throws Exception {
            assertFalse((Boolean) invoke("isLabelCell", "名"));
        }

        @Test
        void shouldRejectTooLong() throws Exception {
            assertFalse((Boolean) invoke("isLabelCell", "这是一个非常长的说明文字超过二十个字符的内容不应该被当作标签"));
        }

        @Test
        void shouldRejectPureNumber() throws Exception {
            assertFalse((Boolean) invoke("isLabelCell", "12345"));
            assertFalse((Boolean) invoke("isLabelCell", "3.14"));
        }

        @Test
        void shouldRejectInstructionText() throws Exception {
            assertFalse((Boolean) invoke("isLabelCell", "填写要求如下"));
            assertFalse((Boolean) invoke("isLabelCell", "说明文字"));
            assertFalse((Boolean) invoke("isLabelCell", "请注意事项"));
        }

        @Test
        void shouldAcceptNormalLabel() throws Exception {
            assertTrue((Boolean) invoke("isLabelCell", "项目名称"));
            assertTrue((Boolean) invoke("isLabelCell", "负责人"));
            assertTrue((Boolean) invoke("isLabelCell", "联系电话"));
            assertTrue((Boolean) invoke("isLabelCell", "申报单位"));
        }
    }

    @Nested
    @DisplayName("guessType - 根据标签猜测字段类型")
    class GuessTypeTest {
        @Test
        void shouldGuessDate() throws Exception {
            assertEquals("date", invoke("guessType", "开始日期"));
            assertEquals("date", invoke("guessType", "起始时间"));
            assertEquals("date", invoke("guessType", "截止日期"));
        }

        @Test
        void shouldGuessPhone() throws Exception {
            assertEquals("phone", invoke("guessType", "联系电话"));
            assertEquals("phone", invoke("guessType", "手机号"));
            assertEquals("phone", invoke("guessType", "联系方式"));
        }

        @Test
        void shouldGuessNumber() throws Exception {
            assertEquals("number", invoke("guessType", "项目经费"));
            assertEquals("number", invoke("guessType", "预算金额"));
            assertEquals("number", invoke("guessType", "资助经费"));
        }

        @Test
        void shouldGuessOrg() throws Exception {
            assertEquals("org", invoke("guessType", "申报单位"));
            assertEquals("org", invoke("guessType", "所在机构"));
            assertEquals("org", invoke("guessType", "学院"));
            assertEquals("org", invoke("guessType", "部门"));
        }

        @Test
        void shouldGuessPerson() throws Exception {
            assertEquals("person", invoke("guessType", "项目负责人"));
            assertEquals("person", invoke("guessType", "课题主持人"));
            assertEquals("person", invoke("guessType", "姓名"));
        }

        @Test
        void shouldDefaultToText() throws Exception {
            assertEquals("text", invoke("guessType", "备注信息"));
            assertEquals("text", invoke("guessType", "邮箱地址"));
        }

        @Test
        void shouldHandleNull() throws Exception {
            Method method = TemplateFillServiceImpl.class.getDeclaredMethod("guessType", String.class);
            method.setAccessible(true);
            assertEquals("text", method.invoke(templateFillService, (String) null));
        }
    }

    @Nested
    @DisplayName("normalizeLabel - 标签标准化映射")
    class NormalizeLabelTest {
        @Test
        void shouldNormalizeProjectName() throws Exception {
            assertEquals("project_name", invoke("normalizeLabel", "项目名称"));
            assertEquals("project_name", invoke("normalizeLabel", "课题名称"));
        }

        @Test
        void shouldNormalizeOwner() throws Exception {
            assertEquals("owner", invoke("normalizeLabel", "负责人"));
            assertEquals("owner", invoke("normalizeLabel", "项目负责人"));
            assertEquals("owner", invoke("normalizeLabel", "主持人"));
        }

        @Test
        void shouldNormalizeOrgName() throws Exception {
            assertEquals("org_name", invoke("normalizeLabel", "单位名称"));
            assertEquals("org_name", invoke("normalizeLabel", "所在单位"));
        }

        @Test
        void shouldNormalizeContactFields() throws Exception {
            assertEquals("phone", invoke("normalizeLabel", "联系电话"));
            assertEquals("email", invoke("normalizeLabel", "电子邮箱"));
        }

        @Test
        void shouldNormalizeDateFields() throws Exception {
            assertEquals("start_date", invoke("normalizeLabel", "开始日期"));
            assertEquals("end_date", invoke("normalizeLabel", "截止日期"));
        }

        @Test
        void shouldNormalizeBudget() throws Exception {
            assertEquals("budget", invoke("normalizeLabel", "项目经费"));
            assertEquals("budget", invoke("normalizeLabel", "资助金额"));
        }

        @Test
        void shouldReturnLowercaseForUnknown() throws Exception {
            assertEquals("custom_field", invoke("normalizeLabel", "custom_field"));
        }

        @Test
        void shouldHandleNull() throws Exception {
            Method method = TemplateFillServiceImpl.class.getDeclaredMethod("normalizeLabel", String.class);
            method.setAccessible(true);
            assertEquals("", method.invoke(templateFillService, (String) null));
        }
    }

    @Nested
    @DisplayName("getFileType - 文件类型检测")
    class GetFileTypeTest {
        @Test
        void shouldDetectXlsx() throws Exception {
            assertEquals("xlsx", invoke("getFileType", "template.xlsx"));
        }

        @Test
        void shouldDetectDocx() throws Exception {
            assertEquals("docx", invoke("getFileType", "template.docx"));
        }

        @Test
        void shouldHandleNoExtension() throws Exception {
            assertEquals("unknown", invoke("getFileType", "noext"));
        }

        @Test
        void shouldHandleNull() throws Exception {
            Method method = TemplateFillServiceImpl.class.getDeclaredMethod("getFileType", String.class);
            method.setAccessible(true);
            assertEquals("unknown", method.invoke(templateFillService, (String) null));
        }
    }

    @Nested
    @DisplayName("getCellRef - 单元格引用")
    class GetCellRefTest {
        @Test
        void shouldReturnCorrectRef() throws Exception {
            Method method = TemplateFillServiceImpl.class.getDeclaredMethod("getCellRef", int.class, int.class);
            method.setAccessible(true);
            assertEquals("A1", method.invoke(templateFillService, 0, 0));
            assertEquals("B2", method.invoke(templateFillService, 1, 1));
            assertEquals("C3", method.invoke(templateFillService, 2, 2));
        }
    }
}
