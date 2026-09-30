package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingIssueRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingPublicConfirmReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.TrainingMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.BeforeEach;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionFact;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 现场培训（ACC-01）定向测试：外发真实文件、客户确认状态机与自动归档、令牌摘要不落明文。
 */
class TrainingServiceTest {

    private final TrainingMapper trainingMapper = mock(TrainingMapper.class);
    private final PlatformDeliveryMaterialApi deliveryMaterialApi = mock(PlatformDeliveryMaterialApi.class);
    private final AdminUserApi adminUserApi = mock(AdminUserApi.class);
    private final FileApi fileApi = mock(FileApi.class);
    private final EngineeringRecordCodeGenerator recordCodeGenerator = mock(EngineeringRecordCodeGenerator.class);
    private final TrainingServiceImpl service = new TrainingServiceImpl();
    private TrainingDO row;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "trainingMapper", trainingMapper);
        ReflectionTestUtils.setField(service, "deliveryMaterialApi", deliveryMaterialApi);
        ReflectionTestUtils.setField(service, "adminUserApi", adminUserApi);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        doReturn("PROJ-JF-001").when(recordCodeGenerator).next(any(), anyString(), any());
        ReflectionTestUtils.setField(service, "recordCodeGenerator", recordCodeGenerator);
        row = new TrainingDO();
        row.setId(1L);
        row.setCode("TR-001");
        row.setName("设备运维培训");
        row.setProjectId(7L);
        row.setTrainingTypes("TECHNICAL_PRINCIPLE,PRODUCT_OPS");
        row.setTrainingTime(LocalDate.of(2026, 9, 19));
        row.setTrainerUserId(9L);
        row.setTrainerName("王工");
        row.setStatus(0);
        row.setVersion(3L);
        row.setConfirmationRevisionId(100L);
        row.setConfirmationFormRules(TrainingConfirmationForms.safeSnapshot(TrainingConfirmationForms.defaults()));
        when(trainingMapper.selectById(1L)).thenReturn(row);
        when(trainingMapper.updateById(any(TrainingDO.class))).thenReturn(1);
    }

    @Test
    void issueGeneratesTokenDigestAndRealRecordFile() throws Exception {
        when(fileApi.createFile(any(), anyString(), anyString(), anyString()))
                .thenReturn("/file/training/TR-001.html");

        TrainingIssueRespVO resp = service.issueTraining(1L);

        assertNotNull(resp.getToken());
        // 原始令牌不落库，落库的是 SHA-256 摘要
        ArgumentCaptor<TrainingDO> captor = ArgumentCaptor.forClass(TrainingDO.class);
        verify(trainingMapper).updateById(captor.capture());
        TrainingDO updated = captor.getValue();
        String expectedDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(resp.getToken().getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedDigest, updated.getSignTokenDigest());
        assertEquals(1, updated.getStatus());
        assertNotNull(updated.getTokenExpiresAt());
        assertTrue(updated.getTokenExpiresAt().isAfter(LocalDateTime.now()));

        // 培训记录表真实生成并上传，客户区域留空待填
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileApi).createFile(contentCaptor.capture(), eq("TR-001.html"), eq("training"), eq("text/html"));
        String html = new String(contentCaptor.getValue(), StandardCharsets.UTF_8);
        assertTrue(html.contains("TR-001"));
        assertTrue(html.contains("设备运维培训"));
        assertTrue(html.contains("培训工程师技术水平及表达能力"));
        assertTrue(html.contains("客户填写区域（客户确认后回填）"));
        assertEquals("/file/training/TR-001.html", updated.getFileUrl());
        assertEquals((long) contentCaptor.getValue().length, updated.getFileSize());
    }

    @Test
    void confirmRecordsCustomerSectionArchivesDeliverableAndBlocksReplay() throws Exception {
        row.setStatus(1);
        String rawDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("raw-token".getBytes(StandardCharsets.UTF_8)));
        row.setSignTokenDigest(rawDigest);
        row.setTokenExpiresAt(LocalDateTime.now().plusDays(1));
        when(trainingMapper.selectByDigest(rawDigest)).thenReturn(row);
        when(fileApi.createFile(any(), anyString(), anyString(), anyString()))
                .thenReturn("/file/training/TR-001-signed.html");

        TrainingPublicConfirmReqVO reqVO = new TrainingPublicConfirmReqVO();
        reqVO.setSkillRating("很好");
        reqVO.setEffectRating("良好");
        reqVO.setSatisfactionRating("非常满意");
        reqVO.setSignOpinion("培训扎实");
        reqVO.setSignConfirmerName("张三");
        reqVO.setSignatureImageDataUrl(TrainingConfirmationFormsTest.png(true));
        service.confirmByToken("raw-token", reqVO);

        assertEquals(2, row.getStatus());
        assertEquals("张三", row.getSignConfirmerName());
        assertNotNull(row.getSignTime());

        // 确认后统一登记交付件（P06R：业务结果型，锚定培训业务对象）
        verify(deliveryMaterialApi).registerBusinessResultMaterial(eq("IMP"), eq("training"), eq(1L),
                eq("TRAINING_RECORD"), eq("training"), eq("1"), isNull(),
                eq("设备运维培训（现场培训记录）"), eq(7L));
        // 培训记录表仍归属培训本体，含客户填写区域
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileApi).createFile(contentCaptor.capture(), eq("TR-001.html"), eq("training"), eq("text/html"));
        String html = new String(contentCaptor.getValue(), StandardCharsets.UTF_8);
        assertTrue(html.contains("张三"));
        assertTrue(html.contains("培训扎实"));
        assertTrue(html.contains("data:image/png;base64,"));
        assertNotNull(row.getConfirmationValues());
        assertFalse(html.contains("□很好"));

        // 已确认后令牌不可再次确认（状态机防重放）
        when(trainingMapper.selectById(1L)).thenReturn(row);
        row.setStatus(2);
        assertThrows(Exception.class, () -> service.confirmByToken("raw-token", reqVO));
    }

    @Test
    void expiredOrVoidTokenIsRejected() {
        // 已作废记录令牌失效
        row.setStatus(3);
        row.setSignTokenDigest("digest-void");
        row.setTokenExpiresAt(LocalDateTime.now().plusDays(1));
        when(trainingMapper.selectByDigest("digest-void")).thenReturn(row);
        assertThrows(Exception.class, () -> service.inspectByToken("raw-token"));

        // 未知令牌摘要直接拒绝
        when(trainingMapper.selectByDigest("digest-unknown")).thenReturn(null);
        assertThrows(Exception.class, () -> service.inspectByToken("other-token"));
    }

    @Test
    void issueReissueReplacesTokenDigestSoOldLinkExpires() {
        row.setStatus(1);
        row.setSignTokenDigest("old-digest");
        when(fileApi.createFile(any(), anyString(), anyString(), anyString())).thenReturn("/file/training/TR-001.html");

        TrainingIssueRespVO first = service.issueTraining(1L);
        TrainingIssueRespVO second = service.issueTraining(1L);

        assertNotEquals(first.getToken(), second.getToken());
    }
    @Test
    void staleConfirmationDoesNotArchive() throws Exception {
        row.setStatus(1);
        row.setTokenExpiresAt(LocalDateTime.now().plusDays(1));
        when(trainingMapper.selectByDigest(anyString())).thenReturn(row);
        when(trainingMapper.updateById(any(TrainingDO.class))).thenReturn(0);
        TrainingPublicConfirmReqVO request = new TrainingPublicConfirmReqVO();
        request.setSkillRating("很好"); request.setEffectRating("良好");
        request.setSatisfactionRating("非常满意"); request.setSignConfirmerName("张三");
        request.setSignatureImageDataUrl(TrainingConfirmationFormsTest.png(true));
        assertThrows(RuntimeException.class, () -> service.confirmByToken("token", request));
        verify(deliveryMaterialApi, never()).registerBusinessResultMaterial(any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    void issueFreezesPublishedRevisionAndReissueDoesNotReadNewTemplate() {
        var api = mock(DynamicFormBusinessInstanceApi.class);
        ReflectionTestUtils.setField(service, "confirmationFormApi", api);
        row.setConfirmationFormRules(null);
        var fact = new DynamicFormRevisionFact(1L, TrainingConfirmationFormPolicy.KEY,
                TrainingConfirmationForms.DEFAULT_TEMPLATE, 101L, 1, 1,
                TrainingConfirmationFormPolicy.USAGE, null, "FORM_CREATE_ELEMENT_PLUS", "3.4.0", "3.2.38",
                "{}", TrainingConfirmationForms.defaults(), java.util.List.of(), null);
        when(api.inspectCurrentRevisionForUsage(any())).thenReturn(fact);
        when(api.lockAndRevalidateRevisionForUsage(any())).thenReturn(fact);
        TenantContextHolder.setTenantId(1L);
        try {
            service.issueTraining(1L);
            String frozen = row.getConfirmationFormRules();
            service.issueTraining(1L);
            assertEquals(101L, row.getConfirmationRevisionId());
            assertEquals(frozen, row.getConfirmationFormRules());
            verify(api, times(1)).inspectCurrentRevisionForUsage(any());
            verify(api, times(1)).lockAndRevalidateRevisionForUsage(any());
        } finally { TenantContextHolder.clear(); }
    }

}
