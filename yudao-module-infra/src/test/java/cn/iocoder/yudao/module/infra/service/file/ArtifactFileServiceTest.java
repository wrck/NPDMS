package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.infra.api.file.dto.ArtifactFileCreateCommand;
import cn.iocoder.yudao.module.infra.api.file.dto.ArtifactFileVersionDTO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtifactFileServiceTest extends BaseMockitoUnitTest {

    @Mock
    private FileConfigService fileConfigService;
    @Mock
    private FileArtifactMapper fileArtifactMapper;
    @Mock
    private FileVersionMapper fileVersionMapper;
    @Mock
    private FileClient fileClient;
    @InjectMocks
    private ArtifactFileService service;

    @Test
    void shouldReplaySameFileVersionForSameDigest() {
        byte[] content = "complete log".getBytes(StandardCharsets.UTF_8);
        String digest = ArtifactFileService.sha256Hex(content);
        ArtifactFileCreateCommand command = command("callback-1", digest, content.length);
        FileArtifactDO artifact = new FileArtifactDO().setId(10L).setSourceSystem("DEVICE_OPS")
                .setSourceArtifactKey("callback-1");
        FileVersionDO version = new FileVersionDO().setId(20L).setArtifactId(10L)
                .setContentSha256(digest).setSize((long) content.length).setContentType("text/plain")
                .setStorageKey("device-ops/callback-1.log").setScanStatus("CLEAN");
        when(fileArtifactMapper.selectBySource("DEVICE_OPS", "callback-1")).thenReturn(artifact);
        when(fileVersionMapper.selectByArtifactAndDigest(10L, digest)).thenReturn(version);

        ArtifactFileVersionDTO replay = service.store(command, new ByteArrayInputStream(content));

        assertEquals(20L, replay.fileVersionId());
        assertEquals(digest, replay.contentSha256());
    }

    @Test
    void shouldStreamNewArtifactToFileClient() throws Exception {
        byte[] content = "complete log".getBytes(StandardCharsets.UTF_8);
        String digest = ArtifactFileService.sha256Hex(content);
        ArtifactFileCreateCommand command = command("callback-3", digest, content.length);
        when(fileArtifactMapper.selectBySource("DEVICE_OPS", "callback-3")).thenReturn(null);
        when(fileConfigService.getMasterFileClient()).thenReturn(fileClient);
        when(fileClient.getId()).thenReturn(1L);
        when(fileClient.upload(ArgumentMatchers.any(InputStream.class), ArgumentMatchers.anyLong(),
                ArgumentMatchers.anyString(), ArgumentMatchers.eq("text/plain"))).thenReturn("http://file");

        service.store(command, new ByteArrayInputStream(content));

        verify(fileClient).upload(ArgumentMatchers.any(InputStream.class),
                ArgumentMatchers.eq((long) content.length), ArgumentMatchers.anyString(),
                ArgumentMatchers.eq("text/plain"));
    }

    @Test
    void shouldRejectContentLargerThanLimit() {
        ArtifactFileCreateCommand command = command("callback-2", "digest", 52_428_801L);

        assertThrows(IllegalArgumentException.class,
                () -> service.store(command, new ByteArrayInputStream(new byte[0])));
    }

    private static ArtifactFileCreateCommand command(String key, String digest, long size) {
        return new ArtifactFileCreateCommand(1L, "DEVICE_OPS", key, key, "collection.log", "text/plain",
                size, digest, "device-ops", "INTERNAL");
    }
}
