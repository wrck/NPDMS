package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import java.util.List;

public record FileReferenceSetExpectation(FileReferenceSetKey key, Long expectedScopeVersion,
                                          List<FileArtifactVersionFact> expectedActiveFacts) {
    public FileReferenceSetExpectation {
        FileReferenceSetFact validated = new FileReferenceSetFact(key, expectedScopeVersion,
                expectedActiveFacts);
        expectedActiveFacts = validated.activeFacts();
    }
}
