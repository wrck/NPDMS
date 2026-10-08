package cn.iocoder.yudao.module.pms.engineering.service.briefing;

import cn.iocoder.yudao.module.pms.platform.api.file.NativeGeneratedFileApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.NativeGeneratedFileCommand;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingGenerateReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.BriefingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.BriefingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BriefingGenerateDocumentTest {

    private final BriefingMapper mapper = mock(BriefingMapper.class);
    private final NativeGeneratedFileApi generatedFiles = mock(NativeGeneratedFileApi.class);
    private final BriefingServiceImpl service = new BriefingServiceImpl();
    private BriefingDO row;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "briefingMapper", mapper);
        ReflectionTestUtils.setField(service, "generatedFiles", generatedFiles);
        ReflectionTestUtils.setField(service,"attachments",mock(cn.iocoder.yudao.module.pms.engineering.service.attachment.supplemental.SupplementalAttachmentRegistration.class));
        TenantContextHolder.setTenantId(1L);
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(
                new cn.iocoder.yudao.framework.security.core.LoginUser().setId(9L).setTenantId(1L),new org.springframework.mock.web.MockHttpServletRequest());
        when(generatedFiles.create(any())).thenReturn(new NativeGeneratedFileApi.RegisteredFile(210L,220L,230L,1,"a".repeat(64),"BR-001.html"));
        row = new BriefingDO();
        row.setId(1L);
        row.setCode("BR-001");
        row.setName("核心交换机割接交底");
        row.setProjectId(7L);
        row.setBriefingType("STANDARD");
        row.setSourceSnapshot("WBS基线v3");
        row.setStatus(BriefingServiceImpl.STATUS_DRAFT);
        row.setVersion(2L);
        when(mapper.selectFileOwnerForUpdate(any())).thenReturn(row);
        when(mapper.updateById(any(BriefingDO.class))).thenReturn(1);
    }

    @AfterEach void clear(){TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}

    @Test
    void generateWritesRealFileMetadataInsteadOfForgedPlaceholder() throws Exception {

        BriefingGenerateReqVO request = new BriefingGenerateReqVO();
        request.setId(1L);
        request.setVersion(2);
        service.generateBriefing(request);

        ArgumentCaptor<NativeGeneratedFileCommand> contentCaptor = ArgumentCaptor.forClass(NativeGeneratedFileCommand.class);
        verify(generatedFiles).create(contentCaptor.capture());
        var command=contentCaptor.getValue();
        assertEquals(9L,command.actorUserId());assertEquals("BRIEFING_DOCUMENT_HTML/2",command.purposeCode());
        byte[] document = command.content();
        assertTrue(document.length > 0);
        String html = new String(document, StandardCharsets.UTF_8);
        assertTrue(html.contains("BR-001"));
        assertTrue(html.contains("核心交换机割接交底"));

        assertEquals("/api/v1/pms/briefings/1/files/210", row.getFileUrl());
        assertEquals("BR-001.html", row.getFileName());
        assertEquals((long) document.length, row.getFileSize());
        String expectedChecksum = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(document));
        assertEquals(expectedChecksum, row.getFileChecksum());
        assertEquals(BriefingServiceImpl.STATUS_GENERATED, row.getStatus());
        assertNotNull(row.getGenerateTime());
    }

    @Test
    void blankContentAssemblesFromRealBriefingDataNotPlaceholder() {

        BriefingGenerateReqVO request = new BriefingGenerateReqVO();
        request.setId(1L);
        request.setVersion(2);
        service.generateBriefing(request);

        assertNotNull(row.getContent());
        assertFalse(row.getContent().contains("自动生成的交底书内容"));
        assertTrue(row.getContent().contains("BR-001"));
        assertTrue(row.getContent().contains("WBS基线v3"));
    }

    @Test
    void manualContentIsPreservedVerbatim() {
        row.setContent("人工编写内容");

        BriefingGenerateReqVO request = new BriefingGenerateReqVO();
        request.setId(1L);
        request.setVersion(2);
        service.generateBriefing(request);

        assertEquals("人工编写内容", row.getContent());
    }
}
