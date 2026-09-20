package cn.iocoder.yudao.module.pms.engineering.service.briefing;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
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
    private final FileApi fileApi = mock(FileApi.class);
    private final BriefingServiceImpl service = new BriefingServiceImpl();
    private BriefingDO row;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "briefingMapper", mapper);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        row = new BriefingDO();
        row.setId(1L);
        row.setCode("BR-001");
        row.setName("核心交换机割接交底");
        row.setProjectId(7L);
        row.setBriefingType("STANDARD");
        row.setSourceSnapshot("WBS基线v3");
        row.setStatus(BriefingServiceImpl.STATUS_DRAFT);
        row.setVersion(2);
        when(mapper.selectById(1L)).thenReturn(row);
        when(mapper.updateById(any(BriefingDO.class))).thenReturn(1);
    }

    @Test
    void generateWritesRealFileMetadataInsteadOfForgedPlaceholder() throws Exception {
        when(fileApi.createFile(any(), anyString(), anyString(), anyString())).thenReturn("/file/briefing/BR-001.html");

        BriefingGenerateReqVO request = new BriefingGenerateReqVO();
        request.setId(1L);
        request.setVersion(2);
        service.generateBriefing(request);

        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileApi).createFile(contentCaptor.capture(), eq("BR-001.html"), eq("briefing"), eq("text/html"));
        byte[] document = contentCaptor.getValue();
        assertTrue(document.length > 0);
        String html = new String(document, StandardCharsets.UTF_8);
        assertTrue(html.contains("BR-001"));
        assertTrue(html.contains("核心交换机割接交底"));

        assertEquals("/file/briefing/BR-001.html", row.getFileUrl());
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
        when(fileApi.createFile(any(), anyString(), anyString(), anyString())).thenReturn("/file/BR-001.html");

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
        when(fileApi.createFile(any(), anyString(), anyString(), anyString())).thenReturn("/file/BR-001.html");

        BriefingGenerateReqVO request = new BriefingGenerateReqVO();
        request.setId(1L);
        request.setVersion(2);
        service.generateBriefing(request);

        assertEquals("人工编写内容", row.getContent());
    }
}
