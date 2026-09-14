package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.FileArtifactController;
import cn.iocoder.yudao.module.pms.platform.service.file.*;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadCompleted;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitialized;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real MVC binding only: Owner authorization is covered separately, no server or database is started. */
class FileExecutionContextControllerTest {
    private static final String CONTEXT = "{\"task\":{\"projectId\":11,\"taskId\":12,\"executionId\":2099473011264401410}}";
    private final FileUploadApplicationService uploads = mock(FileUploadApplicationService.class);
    private final FileLifecycleApplicationService lifecycle = mock(FileLifecycleApplicationService.class);
    private MockMvc mvc;

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(0L);
        mvc = MockMvcBuilders.standaloneSetup(new FileArtifactController(uploads, mock(FileQueryService.class),
                mock(FileAccessTicketService.class), lifecycle, new MockEnvironment())).build();
    }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void multipartJsonReachesCompletionWithoutLosingLongExecutionId() throws Exception {
        when(uploads.complete(any())).thenReturn(new FileUploadCompleted(101L, 1, 301L, "slot-a", "a"));
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            mvc.perform(multipart("/api/v1/pms/files/101:complete-upload")
                    .file(new MockMultipartFile("file", "evidence.txt", "text/plain", new byte[]{1}))
                    .file(new MockMultipartFile("ownerExecutionContext", "", "application/json", CONTEXT.getBytes(StandardCharsets.UTF_8)))
                    .param("sessionId", "201").header("Idempotency-Key", "upload-node"))
                    .andExpect(status().isOk());
        }
        verify(uploads).complete(argThat(command -> command.tenantId().equals(0L) && command.actorUserId().equals(7L)
                && JsonUtils.parseTree(CONTEXT).equals(command.ownerExecutionContext())));
    }

    @Test void jsonBodiesPassSelectedContextToInitializationAndDetach() throws Exception {
        when(uploads.initialize(any())).thenReturn(new FileUploadInitialized(101L, 201L, LocalDateTime.now()));
        when(lifecycle.detach(any())).thenReturn(new FileLifecycleApplicationService.LifecycleResult(101L, 1, 301L, 2, "DETACHED"));
        String key = "\"ownerContext\":\"PLATFORM\",\"objectType\":\"DYNAMIC_FORM_INSTANCE\","
                + "\"objectId\":\"31\",\"purposeCode\":\"FORM_FIELD_ATTACHMENT/evidence\",\"referenceKey\":\"slot-a\"";
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            mvc.perform(post("/api/v1/pms/files:init-upload").contentType("application/json")
                    .header("Idempotency-Key", "initialize-node")
                    .content("{" + key + ",\"modeCode\":\"CREATE_ARTIFACT\",\"fileName\":\"evidence.txt\","
                            + "\"categoryCode\":\"DYNAMIC_FORM_ATTACHMENT\",\"declaredSizeBytes\":1,"
                            + "\"declaredMediaType\":\"text/plain\",\"ownerExecutionContext\":" + CONTEXT + "}"))
                    .andExpect(status().isOk());
            mvc.perform(delete("/api/v1/pms/file-references/301").contentType("application/json")
                    .header("Idempotency-Key", "detach-node").header("If-Match", "1")
                    .content("{" + key + ",\"reason\":\"材料重复\",\"ownerExecutionContext\":" + CONTEXT + "}"))
                    .andExpect(status().isOk());
        }
        verify(uploads).initialize(argThat(command -> JsonUtils.parseTree(CONTEXT).equals(command.ownerExecutionContext())));
        verify(lifecycle).detach(argThat(command -> JsonUtils.parseTree(CONTEXT).equals(command.ownerExecutionContext())));
    }

    @Test void malformedMultipartJsonNeverCallsTheUploadService() throws Exception {
        mvc.perform(multipart("/api/v1/pms/files/101:complete-upload")
                .file(new MockMultipartFile("file", "evidence.txt", "text/plain", new byte[]{1}))
                .file(new MockMultipartFile("ownerExecutionContext", "", "application/json", "{".getBytes(StandardCharsets.UTF_8)))
                .param("sessionId", "201").header("Idempotency-Key", "invalid-context"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(uploads);
    }
}
