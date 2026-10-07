package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.*;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

/** Imports evidence only. Never assigns members, changes lifecycle, emits completion, or registers files. */
@Service
@RequiredArgsConstructor
public class ProjectLegacyDetailService {
    private final ProjectManualCreationService projects;
    private final ProjectContractQueryApi contracts;
    private final ProjectLegacyDetailMapper mapper;
    private final LegacyDetailNormalizer normalizer;
    private final LegacyDetailCatalog catalog;

    public record ImportResult(String decision,Long sourceId,Long snapshotId,String checksum,int records) {}
    public record SourceView(Long id,String sourceSystem,String sourceProjectKey,String sourceContractNo,
                             Long snapshotId,String checksum,String sourceReadAt,String capturedAt,
                             Map<String,Integer> counts,List<LegacyDetailCatalog.Domain> domains,
                             List<String> deferredDomains) {}
    public record RecordView(Long id,String domain,String sourceTable,String sourceKey,String parentDomain,
                             String parentSourceKey,String sourceUpdatedAt,String checksum,
                             Map<String,String> values,List<String> redactedFields) {}

    @Transactional(rollbackFor=Exception.class)
    public ImportResult ingest(Long projectId,LegacyDetailInput input,ProjectAccessActor actor) {
        validateActor(actor);
        projects.getProjectForManage(projectId,actor);
        var bundle=normalizer.normalize(input);
        var contractNumbers=contracts.getCurrentContractNumbers(projectId);
        if (contractNumbers==null || !contractNumbers.contains(input.sourceContractNo())) fail("LEGACY_CONTRACT_NOT_ASSOCIATED");
        var source=mapper.selectSourceForUpdate(new SourceIdentityQuery(actor.tenantId(),input.sourceProjectKey()));
        if (source!=null && (!Objects.equals(source.getProjectId(),projectId)
                || !Objects.equals(source.getSourceContractNo(),input.sourceContractNo()))) fail("LEGACY_SOURCE_ALREADY_BOUND");
        ProjectLegacySnapshotDO current=null;
        if (source!=null) {
            var prior=mapper.selectBatch(new SnapshotIdentityQuery(actor.tenantId(),source.getId(),input.batchKey()));
            if (prior!=null) {
                if (!Objects.equals(prior.getChecksum(),bundle.checksum())) fail("LEGACY_BATCH_PAYLOAD_CONFLICT");
                return result("REPLAYED",source,prior);
            }
            current=mapper.selectSnapshot(new SnapshotQuery(actor.tenantId(),source.getCurrentSnapshotId()));
            if (current==null) fail("LEGACY_HEAD_UNAVAILABLE");
            if (Objects.equals(current.getChecksum(),bundle.checksum())) return result("UNCHANGED",source,current);
            if (!Objects.equals(input.expectedSnapshotChecksum(),current.getChecksum())) fail("LEGACY_EXPECTED_HEAD_CONFLICT");
            if (input.sourceReadAt().isBefore(current.getSourceReadAt())) fail("LEGACY_SOURCE_READ_STALE");
        } else {
            if (input.expectedSnapshotChecksum()!=null) fail("LEGACY_EXPECTED_HEAD_CONFLICT");
            source=new ProjectLegacySourceDO();source.setId(IdWorker.getId());source.setTenantId(actor.tenantId());
            source.setProjectId(projectId);source.setSourceProjectKey(input.sourceProjectKey());
            source.setSourceContractNo(input.sourceContractNo());source.setVersion(0L);
            try { mapper.insertSource(source); }
            catch (DuplicateKeyException e) { throw invalidParamException("LEGACY_SOURCE_CONCURRENT_IMPORT"); }
        }
        var snapshot=new ProjectLegacySnapshotDO();snapshot.setId(IdWorker.getId());snapshot.setTenantId(actor.tenantId());
        snapshot.setSourceId(source.getId());snapshot.setBatchKey(input.batchKey());snapshot.setChecksum(bundle.checksum());
        snapshot.setSourceReadAt(input.sourceReadAt());snapshot.setCapturedAt(LocalDateTime.now());
        snapshot.setOperatorUserId(actor.actorId());snapshot.setDomainCountsJson(JsonUtils.toJsonString(bundle.counts()));
        snapshot.setRecordCount(bundle.records().size());mapper.appendSnapshot(snapshot);
        for (var fact:bundle.records()) {
            var row=new ProjectLegacyRecordDO();row.setId(IdWorker.getId());row.setTenantId(actor.tenantId());
            row.setSnapshotId(snapshot.getId());row.setDomainCode(fact.domain());row.setSourceTable(fact.sourceTable());
            row.setSourceKey(fact.sourceKey());row.setParentDomain(fact.parentDomain());row.setParentSourceKey(fact.parentSourceKey());
            row.setSourceUpdatedAt(fact.sourceUpdatedAt());row.setChecksum(fact.checksum());
            row.setPayloadJson(JsonUtils.toJsonString(fact.values()));row.setRedactedFieldsJson(JsonUtils.toJsonString(fact.redactedFields()));
            mapper.appendRecord(row);
        }
        if (mapper.updateHead(new SourceHeadUpdate(actor.tenantId(),source.getId(),source.getVersion(),snapshot.getId()))!=1)
            fail("LEGACY_HEAD_VERSION_CONFLICT");
        return result("IMPORTED",source,snapshot);
    }

    public List<SourceView> sources(Long projectId,ProjectAccessActor actor) {
        validateActor(actor);projects.getProject(projectId,actor);
        return mapper.selectProjectSources(new ProjectSourcesQuery(actor.tenantId(),projectId)).stream().map(source->{
            var snap=mapper.selectSnapshot(new SnapshotQuery(actor.tenantId(),source.getCurrentSnapshotId()));
            if(snap==null) throw invalidParamException("LEGACY_HEAD_UNAVAILABLE");
            return new SourceView(source.getId(),"DPPMS",source.getSourceProjectKey(),source.getSourceContractNo(),snap.getId(),
                    snap.getChecksum(),snap.getSourceReadAt().toString(),snap.getCapturedAt().toString(),
                    JsonUtils.parseObject(snap.getDomainCountsJson(),new TypeReference<Map<String,Integer>>(){}),
                    catalog.domains(),List.of("旧交付件及附件：用户明确暂不同步"));
        }).toList();
    }

    public PageResult<RecordView> records(Long projectId,Long sourceId,Long snapshotId,String domain,
                                          String sourceKey,String parentDomain,String parentKey,
                                          int pageNo,int pageSize,ProjectAccessActor actor) {
        validateActor(actor);projects.getProject(projectId,actor);catalog.require(domain);
        if (pageNo<1 || pageSize<1 || pageSize>100) fail("LEGACY_PAGE_INVALID");
        var source=mapper.selectProjectSources(new ProjectSourcesQuery(actor.tenantId(),projectId)).stream()
                .filter(x->Objects.equals(x.getId(),sourceId)).findFirst().orElseThrow(()->invalidParamException("LEGACY_SOURCE_INACCESSIBLE"));
        var snapshot=mapper.selectSnapshot(new SnapshotQuery(actor.tenantId(),snapshotId==null?source.getCurrentSnapshotId():snapshotId));
        if (snapshot==null || !Objects.equals(snapshot.getSourceId(),source.getId())) fail("LEGACY_SNAPSHOT_INACCESSIBLE");
        if ((parentDomain==null)!=(parentKey==null)) fail("LEGACY_PARENT_INVALID");
        if(parentDomain!=null) { catalog.require(parentDomain);if(parentKey.isBlank()) fail("LEGACY_PARENT_INVALID"); }
        if(sourceKey!=null && sourceKey.isBlank()) fail("LEGACY_SOURCE_KEY_INVALID");
        var query=new RecordsPageQuery(actor.tenantId(),snapshot.getId(),domain,sourceKey,parentDomain,parentKey,
                Math.multiplyExact(pageNo-1,pageSize),pageSize);
        long total=mapper.countRecords(query);
        if(total==0) return PageResult.empty();
        return new PageResult<>(mapper.selectRecords(query).stream().map(r->new RecordView(r.getId(),r.getDomainCode(),
                r.getSourceTable(),r.getSourceKey(),r.getParentDomain(),r.getParentSourceKey(),r.getSourceUpdatedAt(),r.getChecksum(),
                JsonUtils.parseObject(r.getPayloadJson(),new TypeReference<Map<String,String>>(){}),
                JsonUtils.parseObject(r.getRedactedFieldsJson(),new TypeReference<List<String>>(){}))).toList(),total);
    }
    private static ImportResult result(String decision,ProjectLegacySourceDO source,ProjectLegacySnapshotDO snapshot) {
        return new ImportResult(decision,source.getId(),snapshot.getId(),snapshot.getChecksum(),snapshot.getRecordCount());
    }
    private static void validateActor(ProjectAccessActor actor) {
        if(actor==null || actor.actorId()==null || actor.actorId()<=0 || actor.tenantId()==null
                || !Objects.equals(actor.tenantId(),TenantContextHolder.getRequiredTenantId())) fail("LEGACY_ACTOR_INVALID");
    }
    private static void fail(String code) { throw invalidParamException(code); }
}
