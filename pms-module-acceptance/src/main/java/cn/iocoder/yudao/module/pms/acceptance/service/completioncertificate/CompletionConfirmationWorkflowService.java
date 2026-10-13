package cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.completioncertificate.vo.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.*;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptance.NativeAcceptanceDeliveryAccess;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.SatisfactionQuestionnaireTemplateApi;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.file.NativeGeneratedFileApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.NativeGeneratedFileCommand;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** ACC owns the complete customer confirmation workflow; PLT/PROJ are used only through public APIs. */
@Service @RequiredArgsConstructor
public class CompletionConfirmationWorkflowService {
    private final CompletionCertificateService legacy;
    private final CompletionCertificateMapper certificates;
    private final CompletionSatisfactionBundleMapper bundles;
    private final SatisfactionCollectionTaskMapper tasks;
    private final SatisfactionQuestionnaireMapper questionnaires;
    private final SatisfactionResponseMapper responses;
    private final CompletionSatisfactionSigningMapper signings;
    private final SatisfactionResultMapper results;
    private final IndependentSatisfactionService independent;
    private final SatisfactionQuestionnaireTemplateApi templates;
    private final SatisfactionAccessGrantService grants;
    private final CompletionConfirmationPrintService printLayouts;
    private final NativeAcceptanceDeliveryAccess access;
    private final ProjectAcceptanceContextApi projects;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    private final NativeGeneratedFileApi files;
    private final PlatformDeliveryMaterialApi deliveryMaterials;
    private final FileEvidenceApi fileEvidence;
    private final CompletionPdfBindingMapper pdfBindings;

    @Transactional(rollbackFor=Exception.class)
    public Long save(Long tenant,Long actor,CompletionCertificateSaveReqVO request){
        requirePermission(actor,request.getId()==null?"create":"update");
        lockProject(tenant,actor,request.getProjectId());
        if(request.getSatisfactionTemplateId()!=null)templatesForIssue(tenant,request.getSatisfactionTemplateId());
        if(request.getId()!=null){
            var existing=requireCertificate(tenant,actor,request.getId());
            if(!Objects.equals(existing.getProjectId(),request.getProjectId()) || !Integer.valueOf(0).equals(existing.getStatus()))
                throw new IllegalStateException("已外发的完工证明不能修改");
        }
        // Only capture print revisions on initial binding or explicit template change.
        var before=request.getId()==null?null:certificates.selectById(request.getId());
        var captured=new CompletionCertificateDO();
        if(request.getCompletionPrintTemplateId()!=null){
            if(before!=null && before.getCompletionPrintSnapshot()!=null && Objects.equals(request.getCompletionPrintTemplateId(),before.getCompletionPrintTemplateId())){
                captured.setCompletionPrintTemplateId(before.getCompletionPrintTemplateId());captured.setCompletionPrintRevisionId(before.getCompletionPrintRevisionId());captured.setCompletionPrintSnapshot(before.getCompletionPrintSnapshot());
            }else printLayouts.capture(tenant,actor,captured,request.getCompletionPrintTemplateId(),false);
        }
        if(request.getSatisfactionPrintTemplateId()!=null){
            if(before!=null && before.getSatisfactionPrintSnapshot()!=null && Objects.equals(request.getSatisfactionPrintTemplateId(),before.getSatisfactionPrintTemplateId())){
                captured.setSatisfactionPrintTemplateId(before.getSatisfactionPrintTemplateId());captured.setSatisfactionPrintRevisionId(before.getSatisfactionPrintRevisionId());captured.setSatisfactionPrintSnapshot(before.getSatisfactionPrintSnapshot());
            }else printLayouts.capture(tenant,actor,captured,request.getSatisfactionPrintTemplateId(),true);
        }
        Long id=request.getId();
        if(id==null)id=legacy.createCompletionCertificate(request);else legacy.updateCompletionCertificate(request);
        if(captured.getCompletionPrintSnapshot()!=null || captured.getSatisfactionPrintSnapshot()!=null
                || before!=null && (before.getCompletionPrintTemplateId()!=null || before.getSatisfactionPrintTemplateId()!=null)){
            var version=certificates.selectById(id).getVersion();
            if(certificates.replacePrintBindings(new CompletionPrintBindingUpdate(tenant,id,version,actor.toString(),
                    captured.getCompletionPrintTemplateId(),captured.getCompletionPrintRevisionId(),captured.getCompletionPrintSnapshot(),
                    captured.getSatisfactionPrintTemplateId(),captured.getSatisfactionPrintRevisionId(),captured.getSatisfactionPrintSnapshot()))!=1)
                throw new IllegalStateException("完工证明版本已变化");
        }
        return id;
    }

    public PageResult<CompletionCertificateRespVO> page(Long tenant,Long actor,CompletionCertificatePageReqVO request,boolean survey){
        requirePermission(actor,"query");
        var query=new CompletionWorkflowPageQuery();query.setTenantId(tenant);
        query.setVisibleProjectIds(scopes.resolveAllCurrent(new ProjectAllScopeQuery(tenant,actor,ProjectScopeApi.ACTION_VIEW)));
        query.setProjectId(request.getProjectId());query.setName(request.getName());query.setStatus(request.getStatus());
        query.setPageNo(request.getPageNo());query.setPageSize(request.getPageSize());query.setPairedOnly(survey);
        return BeanUtils.toBean(certificates.selectWorkflowPage(query),CompletionCertificateRespVO.class);
    }

    public Detail detail(Long tenant,Long actor,Long id){
        var row=requireCertificate(tenant,actor,id);
        var certificate=BeanUtils.toBean(row,CompletionCertificateRespVO.class);
        certificate.setDevices(BeanUtils.toBean(legacy.getCompletionCertificateDevices(id),CompletionCertificateDeviceRespVO.class));
        var bundle=bundles.selectByCertificate(new CompletionBundleCertificateQuery(tenant,id));
        if(bundle==null)return new Detail(certificate,null,List.of());
        var history=new ArrayList<Survey>();
        for(var task:tasks.selectByCollection(new CompletionSatisfactionBundleQuery(tenant,bundle.getCollectionKey()))){
            var questionnaire=questionnaires.selectById(task.getQuestionnaireId());
            var result=task.getResultId()==null?null:results.selectById(task.getResultId());
            var signed=signings.selectByQuestionnaire(new CompletionSigningQuestionnaireQuery(tenant,bundle.getId(),task.getQuestionnaireId()));
            var response=signed==null?null:responses.selectById(signed.getResponseId());
            history.add(new Survey(task.getId(),task.getCollectionKey(),task.getTaskRevisionNo(),task.getTaskStatus(),
                    questionnaire==null?null:questionnaire.getFrozenQuestionJson(),response==null?null:response.getAnswerSnapshot(),
                    response==null?null:response.getSubmittedAt(),signed==null?null:signed.getSignatureImageDataUrl(),
                    result==null?null:result.getScore(),result==null?null:result.getThreshold(),result==null?null:result.getPassed()));
        }
        return new Detail(certificate,history.isEmpty()?null:history.getFirst(),List.copyOf(history));
    }

    @Transactional(rollbackFor=Exception.class)
    public Issue issue(Long tenant,Long actor,Long id){
        var observed=requireCertificate(tenant,actor,id);
        var project=lockProject(tenant,actor,observed.getProjectId());
        access.requireCommand(tenant,actor,"completionCertificate",observed.getProjectId(),"submit");
        var certificate=certificates.selectDeliveryOwnerForUpdate(new CompletionCertificateDeliveryLockQuery(tenant,id));
        if(certificate==null || !Set.of(0,1).contains(certificate.getStatus()))throw new IllegalStateException("当前完工证明不能外发");
        var bundle=bundles.selectByCertificate(new CompletionBundleCertificateQuery(tenant,id));
        Long taskId;
        if(bundle==null){
            Long selectedTemplate=certificate.getSatisfactionTemplateId();
            if(selectedTemplate==null){
                var candidates=templateMapper.selectCompletionPublished(new CompletionQuestionnaireTemplateQuery(tenant));
                if(candidates.size()!=1)throw new IllegalArgumentException("原表标准满意度问卷尚未发布，请在满意度问卷模板配置中发布后再推送");
                selectedTemplate=candidates.getFirst().getId();
            }
            var template=templatesForIssue(tenant,selectedTemplate);
            if(certificate.getSatisfactionTemplateId()==null){
                var selection=new CompletionCertificateDO();selection.setId(id);selection.setVersion(certificate.getVersion());selection.setSatisfactionTemplateId(selectedTemplate);
                if(certificates.updateById(selection)!=1)throw new IllegalStateException("完工证明版本已变化");
                certificate=certificates.selectDeliveryOwnerForUpdate(new CompletionCertificateDeliveryLockQuery(tenant,id));
            }
            var created=independent.create(tenant,actor,new IndependentSatisfactionService.Create(certificate.getProjectId(),
                    template.templateId(),template.templateRevisionId(),project.projectVersion(),project.treeVersion()),"completion-certificate:"+id);
            taskId=created.taskId();
            if(Integer.valueOf(0).equals(certificate.getStatus()))legacy.submitCompletionCertificate(id);
        }else{
            var chain=tasks.selectByCollection(new CompletionSatisfactionBundleQuery(tenant,bundle.getCollectionKey()));
            if(chain.isEmpty())throw new IllegalStateException("完工证明关联调查缺失");
            taskId=chain.getFirst().getId();
            bundles.revokeActiveGrants(new CompletionGrantRevocation(tenant,bundle.getId(),actor.toString()));
        }
        var grant=grants.create(tenant,actor,taskId,LocalDateTime.now().plusDays(7),id);
        return new Issue(id,taskId,grant.token(),"/satisfaction-questionnaires/"+grant.token(),grant.expiresAt());
    }

    private cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionTemplateFact templatesForIssue(Long tenant,Long templateId){
        // The saved business selection is resolved only to its current published revision at first issue.
        var template=templateMapper.selectById(templateId);
        if(template==null || !Objects.equals(tenant,template.getTenantId()) || !"PUBLISHED".equals(template.getStatus()))
            throw new IllegalArgumentException("所选满意度问卷没有已发布版本");
        return templates.inspectPublished(templateId,template.getCurrentRevisionId());
    }
    private final SatisfactionQuestionnaireTemplateMapper templateMapper;

    @Transactional(rollbackFor=Exception.class)
    public void voidRecord(Long tenant,Long actor,Long id){
        var before=requireCertificate(tenant,actor,id);lockProject(tenant,actor,before.getProjectId());requirePermission(actor,"update");
        var row=certificates.selectDeliveryOwnerForUpdate(new CompletionCertificateDeliveryLockQuery(tenant,id));
        if(Integer.valueOf(4).equals(row.getStatus()))return;
        if(!Set.of(0,1).contains(row.getStatus()))throw new IllegalStateException("已确认的完工证明不能作废");
        var update=new CompletionCertificateDO();update.setId(id);update.setStatus(4);update.setVersion(row.getVersion());
        if(certificates.updateById(update)!=1)throw new IllegalStateException("完工证明版本已变化");
        var bundle=bundles.selectByCertificate(new CompletionBundleCertificateQuery(tenant,id));
        if(bundle!=null)bundles.revokeActiveGrants(new CompletionGrantRevocation(tenant,bundle.getId(),actor.toString()));
    }

    @Transactional(rollbackFor=Exception.class)
    public void delete(Long tenant,Long actor,Long id){
        var row=requireCertificate(tenant,actor,id);lockProject(tenant,actor,row.getProjectId());requirePermission(actor,"delete");
        if(!Integer.valueOf(0).equals(row.getStatus()))throw new IllegalStateException("只能删除草稿完工证明");
        legacy.deleteCompletionCertificate(id);
    }

    @Transactional(rollbackFor=Exception.class)
    public NativeGeneratedFileApi.RegisteredFile savePdf(Long tenant,Long actor,Long id,Long version,String kind,String key,byte[] content){
        var row=requireCertificate(tenant,actor,id);lockProject(tenant,actor,row.getProjectId());
        requirePermission(actor,"update");
        row=certificates.selectDeliveryOwnerForUpdate(new CompletionCertificateDeliveryLockQuery(tenant,id));
        if(row==null)throw new IllegalStateException("完工证明不存在");
        if(!Set.of("COMPLETION","SATISFACTION").contains(kind) || !Objects.equals(version,row.getVersion()) || Integer.valueOf(4).equals(row.getStatus()))
            throw new IllegalStateException("打印记录已变化或已作废，请重新打开预览");
        if(content==null || content.length<5 || content.length>52_428_800 || !new String(content,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
            throw new IllegalArgumentException("PDF文件内容无效");
        var idMatcher=java.util.regex.Pattern.compile("/ID\\s*\\[\\s*<([a-fA-F0-9]{32})>").matcher(
                new String(content,Math.max(0,content.length-8192),Math.min(8192,content.length),java.nio.charset.StandardCharsets.ISO_8859_1));
        String layoutKey=idMatcher.find()?idMatcher.group(1).toLowerCase(java.util.Locale.ROOT):null;
        String fileName=row.getCode()+"-"+kind+(layoutKey!=null?"-"+layoutKey:"")+".pdf";
        var existing=layoutKey==null?findPdf(new PdfMatch(tenant,row,kind,org.apache.commons.codec.digest.DigestUtils.sha256Hex(content),fileName,null,false,null)):
                resolvePdfBinding(new PdfMatch(tenant,row,kind,null,fileName,layoutKey,false,null));
        if(existing!=null)return existing;
        var created=files.create(new NativeGeneratedFileCommand(tenant,actor,key,"ACC","COMPLETION_CONFIRMATION",id,version,
                kind+"_PDF/"+version,"COMPLETION_CERTIFICATE",fileName,"application/pdf",content));
        if(layoutKey!=null)rememberPdf(new PdfMatch(tenant,row,kind,null,fileName,layoutKey,false,null),created);
        return created;
    }

    @Transactional(rollbackFor=Exception.class)
    public NativeGeneratedFileApi.RegisteredFile findRegisteredPdf(PdfLookup query) {
        var row=requireCertificate(query.tenant(),query.actor(),query.id());
        if(!Set.of("COMPLETION","SATISFACTION").contains(query.kind())
                || query.layoutKey()==null || !query.layoutKey().matches("[a-f0-9]{32}")
                || !Objects.equals(query.version(),row.getVersion()) || Integer.valueOf(4).equals(row.getStatus()))
            throw new IllegalStateException("打印记录已变化或已作废，请重新打开预览");
        var locked=certificates.selectDeliveryOwnerForUpdate(new CompletionCertificateDeliveryLockQuery(query.tenant(),row.getId()));
        if(locked==null || !Objects.equals(locked.getVersion(),query.version()) || Integer.valueOf(4).equals(locked.getStatus()))
            throw new IllegalStateException("打印记录已变化或已作废，请重新打开预览");
        return resolvePdfBinding(new PdfMatch(query.tenant(),locked,query.kind(),null,
                locked.getCode()+"-"+query.kind()+"-"+query.layoutKey()+".pdf",query.layoutKey(),false,null));
    }
    public record PdfLookup(Long tenant,Long actor,Long id,Long version,String kind,String layoutKey) {}

    private NativeGeneratedFileApi.RegisteredFile resolvePdfBinding(PdfMatch query) {
        var row=query.certificate();
        var scope=new CompletionPdfBindingQuery(query.tenant(),row.getId(),row.getVersion(),query.kind(),query.layoutKey());
        var bound=pdfBindings.selectBinding(scope);
        if(bound!=null) {
            var matched=findPdf(new PdfMatch(query.tenant(),row,query.kind(),null,query.fileName(),query.layoutKey(),false,bound.getMaterialId()));
            if(matched==null)throw new IllegalStateException("已登记PDF不可用，请处理交付件状态后重试");
            return matched;
        }
        var existing=findPdf(query);
        if(existing==null && !pdfBindings.hasBaseline(scope))
            existing=findPdf(new PdfMatch(query.tenant(),row,query.kind(),null,row.getCode()+"-"+query.kind()+".pdf",query.layoutKey(),true,null));
        if(existing!=null)rememberPdf(query,existing);
        return existing;
    }
    private void rememberPdf(PdfMatch query,NativeGeneratedFileApi.RegisteredFile file) {
        var binding=new CompletionPdfBindingDO();binding.setTenantId(query.tenant());
        binding.setCertificateId(query.certificate().getId());binding.setCertificateVersion(query.certificate().getVersion());
        binding.setDocumentKind(query.kind());binding.setLayoutKey(query.layoutKey());binding.setMaterialId(file.materialId());binding.setVersion(0L);
        if(pdfBindings.insert(binding)!=1)throw new IllegalStateException("PDF模板基准保存失败");
    }
    private NativeGeneratedFileApi.RegisteredFile findPdf(PdfMatch query) {
        var row=query.certificate();
        var materials=new ArrayList<>(deliveryMaterials.listByEntity("ACC","completionCertificate",row.getId()));
        materials.sort(Comparator.comparing(PlatformDeliveryMaterialApi.DeliveryMaterialView::createTime,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(PlatformDeliveryMaterialApi.DeliveryMaterialView::id,Comparator.reverseOrder()));
        for(var material:materials) {
            if(!"ACTIVE".equals(material.status()) || !"GENERATED".equals(material.sourceKind())
                    || !"FILE".equals(material.materialKind())
                    || !(query.materialId()!=null?query.materialId().equals(material.id()):
                    query.sha256()!=null && query.sha256().equals(material.fileSha256())
                    || query.fileName().equals(material.fileName()) && (query.legacy() || query.fileName().matches(".*-[a-f0-9]{32}\\.pdf"))))continue;
            var document=fileEvidence.inspectDocumentByArtifact(query.tenant(),material.fileArtifactId(),material.fileVersionNo());
            if(document==null || !document.available() || !Objects.equals(material.fileSha256(),document.sha256())
                    || !"ACC".equals(document.ownerContext()) || !"COMPLETION_CONFIRMATION".equals(document.objectType())
                    || !row.getId().toString().equals(document.objectId())
                    || !(query.kind()+"_PDF/"+row.getVersion()).equals(document.purposeCode()))continue;
            return new NativeGeneratedFileApi.RegisteredFile(material.id(),document.referenceId(),document.artifactId(),
                    document.versionNo(),document.sha256(),document.name());
        }
        return null;
    }
    private record PdfMatch(Long tenant,CompletionCertificateDO certificate,String kind,String sha256,String fileName,String layoutKey,boolean legacy,Long materialId) {}

    CompletionCertificateDO requireCertificate(Long tenant,Long actor,Long id){
        access.require(tenant,actor,"completionCertificate",id.toString(),"COMPLETION_CERTIFICATE",false,false,null);
        var row=certificates.selectById(id);
        if(row==null || !Objects.equals(tenant,row.getTenantId()))throw new IllegalStateException("完工证明不存在");
        return row;
    }
    private ProjectAcceptanceContextApi.Context lockProject(Long tenant,Long actor,Long projectId){
        var query=new ProjectAcceptanceContextApi.Query(tenant,projectId,actor);var current=projects.inspect(query);
        var locked=projects.lock(query,current.projectVersion(),current.treeVersion());
        if(!"ACTIVE".equals(locked.lifecycleStatus()))throw new IllegalStateException("当前项目不可编辑");return locked;
    }
    private void requirePermission(Long actor,String action){
        if(actor==null || !permissions.hasAnyPermissions(actor,"pms:acc-completion-certificate:"+action))throw new IllegalStateException("完工证明操作未授权");
    }
    public record Issue(Long id,Long taskId,String token,String signPath,LocalDateTime tokenExpiresAt){}
    public record Detail(CompletionCertificateRespVO certificate,Survey survey,List<Survey> history){}
    public record Survey(Long taskId,String collectionKey,Integer revisionNo,String status,String questionnaireJson,
                         String answerSnapshot,LocalDateTime submittedAt,String signatureImageDataUrl,
                         java.math.BigDecimal score,java.math.BigDecimal threshold,Boolean passed){}
}
