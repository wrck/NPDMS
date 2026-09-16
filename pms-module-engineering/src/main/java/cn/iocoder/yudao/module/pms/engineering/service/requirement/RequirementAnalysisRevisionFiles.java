package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** Current views resolve the effective revision's immutable file references. */
@Service
@RequiredArgsConstructor
public class RequirementAnalysisRevisionFiles {
    private final FileArtifactApi files;
    private final RequirementAnalysisAccess access;
    private final EntityFormApi forms;

    public List<FileReferenceSetFact> inspect(RevisionRef revision, EntityActor actor) {
        var row = access.read(revision.revisionId(), actor);
        if (!row.entityRef().equals(revision.entity())) throw new IllegalArgumentException("File revision identity mismatch");
        var layout = forms.layout(EntityDataRef.revision(revision), actor);
        if (layout == null) return List.of();
        var keys = layout.fields().stream().filter(field -> field.controlledFile()).map(field -> key(revision,
                FormAttachmentPolicy.PURPOSE_PREFIX + field.fieldKey())).toList();
        if (keys.isEmpty()) return List.of();
        return files.inspectReferenceSets(new FileReferenceSetCollectionQuery(keys, FileActionCodes.READ));
    }

    public void lockForFreeze(RevisionRef revision, EntityActor actor) {
        var facts = inspect(revision, actor);
        if (facts.isEmpty()) return;
        files.lockAndRevalidateReferenceSets(new FileReferenceSetCollectionRevalidationQuery(facts.stream()
                .map(fact -> new FileReferenceSetExpectation(fact.key(), fact.scopeVersion(), fact.activeFacts())).toList(), FileActionCodes.READ));
    }

    public void copy(RevisionRef source, RevisionRef target, EntityActor actor) {
        if (!source.entity().equals(target.entity())) throw new IllegalArgumentException("File copy crosses business entities");
        var sourceFacts = inspect(source, actor);
        var targetRow = access.read(target.revisionId(), actor);
        List<AttachExistingFileVersionItem> items = new ArrayList<>();
        sourceFacts.forEach(set -> set.activeFacts().forEach(file -> items.add(new AttachExistingFileVersionItem(
                new FileArtifactVersionRevalidationQuery(file.artifactId(), file.versionNo(), set.key().ownerContext(),
                        set.key().objectType(), set.key().objectId(), set.key().purposeCode(), file.referenceKey(),
                        FileActionCodes.READ, file.fileFactVersion(), file.scopeVersion()),
                new ExistingFileReferenceTarget(RequirementAnalysisRevisionFilePolicy.OWNER, RequirementAnalysisRevisionFilePolicy.TYPE,
                        target.revisionId().toString(), set.key().purposeCode(), file.referenceKey(), targetRow.getId())))));
        if (!items.isEmpty()) files.attachExistingVersions(new AttachExistingFileVersionsCommand("RA-REVISION-COPY-" + target.revisionId(), items));
    }

    private FileReferenceSetKey key(RevisionRef revision, String purpose) {
        return new FileReferenceSetKey(RequirementAnalysisRevisionFilePolicy.OWNER, RequirementAnalysisRevisionFilePolicy.TYPE,
                revision.revisionId().toString(), purpose);
    }
}
