package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormCurrentRevisionQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionRevalidationQuery;
import java.util.LinkedHashMap;
import java.util.Map;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingIssueRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingPublicConfirmReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingPublicRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.TrainingMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.enums.TrainingStatusEnum;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 现场培训记录 Service 实现（ACC-01，Demo 6.1）。
 * <p>
 * 状态机：0 草稿 → 1 已外发 → 2 客户已确认；0/1 可作废为 3。
 * 确认动作由客户通过令牌完成，确认时生成含客户填写区域的培训记录表并自动归档交付件。
 */
@Service
@Validated
public class TrainingServiceImpl implements TrainingService {

    /**
     * Demo 6.1 培训类型固定选项
     */
    private static final Set<String> ALLOWED_TRAINING_TYPES =
            Set.of("TECHNICAL_PRINCIPLE", "PRODUCT_OPS", "OTHER");
    private static final Set<String> SKILL_RATING_OPTIONS = Set.of("很好", "良好", "一般", "差");
    private static final Set<String> SATISFACTION_RATING_OPTIONS = Set.of("非常满意", "较满意", "一般", "差");
    private static final long TOKEN_VALID_DAYS = 7;

    @Resource
    private TrainingMapper trainingMapper;
    @Resource
    private PlatformDeliveryMaterialApi deliveryMaterialApi;
    @Resource
    private EngineeringRecordCodeGenerator recordCodeGenerator;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private FileApi fileApi;
    @Resource
    private DynamicFormBusinessInstanceApi confirmationFormApi;
    @Resource
    private TrainingPrintService trainingPrintService;
    @Resource private cn.iocoder.yudao.module.pms.platform.api.file.NativeGeneratedFileApi generatedFiles;
    @Resource private TrainingConfirmationGrantService confirmationGrants;
    @Resource private cn.iocoder.yudao.module.pms.platform.api.file.BusinessGrantGeneratedFileApi grantFiles;
    @Resource private cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi operationAudit;
    @Resource private cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi fileEvidence;
    @Resource private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;
    @Resource private cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi projectContexts;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTraining(TrainingSaveReqVO createReqVO) {
        validateTrainingTypes(createReqVO.getTrainingTypes());
        TrainingDO entity = BeanUtils.toBean(createReqVO, TrainingDO.class);
        entity.setCode(recordCodeGenerator.next(createReqVO.getProjectId(),
                EngineeringRecordCodeGenerator.TRAINING, trainingMapper));
        entity.setTrainingTypes(String.join(",", createReqVO.getTrainingTypes()));
        Long trainerUserId = createReqVO.getTrainerUserId() != null
                ? createReqVO.getTrainerUserId() : SecurityFrameworkUtils.getLoginUserId();
        if (trainerUserId == null) {
            throw exception(TRAINING_ARGUMENT_INVALID, "培训工程师不能为空");
        }
        entity.setTrainerUserId(trainerUserId);
        entity.setTrainerName(resolveUserNickname(trainerUserId));
        entity.setStatus(TrainingStatusEnum.DRAFT.getStatus());
        if (entity.getConfirmationTemplateId() == null) entity.setConfirmationTemplateId(TrainingConfirmationForms.DEFAULT_TEMPLATE);
        if (createReqVO.getPrintTemplateId() != null) trainingPrintService.capture(entity, createReqVO.getPrintTemplateId());
        trainingMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTraining(TrainingSaveReqVO updateReqVO) {
        TrainingDO existing = validateTrainingExists(updateReqVO.getId());
        // 仅草稿可修改（编码由系统生成，不可变更）
        if (!Objects.equals(TrainingStatusEnum.DRAFT.getStatus(), existing.getStatus())) {
            throw exception(TRAINING_STATUS_INVALID);
        }
        validateTrainingTypes(updateReqVO.getTrainingTypes());
        TrainingDO update = BeanUtils.toBean(updateReqVO, TrainingDO.class);
        if (updateReqVO.getPrintTemplateId() != null && (!Objects.equals(updateReqVO.getPrintTemplateId(), existing.getPrintTemplateId())
                || existing.getPrintLayoutSnapshot() == null)) trainingPrintService.capture(update, updateReqVO.getPrintTemplateId());
        if (update.getVersion() == null) update.setVersion(existing.getVersion());
        if (updateReqVO.getTrainingTypes() != null) {
            update.setTrainingTypes(String.join(",", updateReqVO.getTrainingTypes()));
        }
        if (updateReqVO.getTrainerUserId() != null
                && !Objects.equals(updateReqVO.getTrainerUserId(), existing.getTrainerUserId())) {
            update.setTrainerName(resolveUserNickname(updateReqVO.getTrainerUserId()));
        }
        if (trainingMapper.updateById(update) != 1) throw exception(TRAINING_STATUS_INVALID);
    }

    @Override
    public void deleteTraining(Long id) {
        TrainingDO existing = validateTrainingExists(id);
        // 仅草稿可删除；已外发/已确认的记录保留以维持交付追溯
        if (!Objects.equals(TrainingStatusEnum.DRAFT.getStatus(), existing.getStatus())) {
            throw exception(TRAINING_STATUS_INVALID);
        }
        trainingMapper.deleteById(id);
    }

    @Override
    public TrainingDO getTraining(Long id) {
        return trainingMapper.selectById(id);
    }

    @Override
    public PageResult<TrainingDO> getTrainingPage(TrainingPageReqVO pageReqVO) {
        return trainingMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrainingIssueRespVO issueTraining(Long id) {
        TrainingDO existing = validateTrainingExists(id);
        if (Objects.equals(TrainingStatusEnum.CONFIRMED.getStatus(), existing.getStatus())
                || Objects.equals(TrainingStatusEnum.VOID.getStatus(), existing.getStatus())) {
            throw exception(TRAINING_STATUS_INVALID);
        }
        // Freeze once: reissuing a link must retain the original published questionnaire.
        if (StringUtils.isBlank(existing.getConfirmationFormRules())) {
            Long templateId = existing.getConfirmationTemplateId() == null
                    ? TrainingConfirmationForms.DEFAULT_TEMPLATE : existing.getConfirmationTemplateId();
            var fact = confirmationFormApi.inspectCurrentRevisionForUsage(new DynamicFormCurrentRevisionQuery(
                    TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                    TrainingConfirmationFormPolicy.KEY, templateId, TrainingConfirmationFormPolicy.USAGE));
            fact = confirmationFormApi.lockAndRevalidateRevisionForUsage(new DynamicFormRevisionRevalidationQuery(
                    SecurityFrameworkUtils.getLoginUserId(), fact));
            existing.setConfirmationTemplateId(templateId);
            existing.setConfirmationRevisionId(fact.templateRevisionId());
            existing.setConfirmationFormRules(TrainingConfirmationForms.safeSnapshot(fact.formRulesJson()));
        }
        // 重新外发生成新令牌，原令牌因摘要替换自然失效
        String token = generateToken();
        TrainingDO update = new TrainingDO();
        update.setId(id);
        update.setConfirmationTemplateId(existing.getConfirmationTemplateId());
        update.setConfirmationRevisionId(existing.getConfirmationRevisionId());
        update.setConfirmationFormRules(existing.getConfirmationFormRules());
        update.setSignTokenDigest(digest(token));
        update.setTokenExpiresAt(LocalDateTime.now().plusDays(TOKEN_VALID_DAYS));
        update.setStatus(TrainingStatusEnum.ISSUED.getStatus());
        update.setVersion(existing.getVersion());
        String fileUrl = renderAndUpload(existing, true);
        update.setFileUrl(fileUrl);
        update.setFileName(existing.getFileName());
        update.setFileSize(existing.getFileSize());
        update.setFileChecksum(existing.getFileChecksum());
        if (trainingMapper.updateById(update) != 1) throw exception(TRAINING_STATUS_INVALID);
        confirmationGrants.createForIssue(id,update.getSignTokenDigest());
        return new TrainingIssueRespVO(id, token, "/training-records/" + token,
                update.getTokenExpiresAt(), fileUrl,
                "外部推送通道（短信/钉钉）未接入，请复制确认链接线下发送给客户，有效期 7 天。");
    }

    @Override
    public void voidTraining(Long id) {
        TrainingDO existing = validateTrainingExists(id);
        if (Objects.equals(TrainingStatusEnum.CONFIRMED.getStatus(), existing.getStatus())) {
            throw exception(TRAINING_STATUS_INVALID);
        }
        if (Objects.equals(TrainingStatusEnum.VOID.getStatus(), existing.getStatus())) {
            return;
        }
        TrainingDO update = new TrainingDO();
        update.setId(id);
        update.setStatus(TrainingStatusEnum.VOID.getStatus());
        // 作废同时清除令牌摘要，外发链接即刻失效
        update.setSignTokenDigest("");
        update.setVersion(existing.getVersion());
        if (trainingMapper.updateById(update) != 1) throw exception(TRAINING_STATUS_INVALID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateRecordFile(Long id) {
        TrainingDO existing = validateTrainingExists(id);
        String fileUrl = renderAndUpload(existing, true);
        TrainingDO update = new TrainingDO();
        update.setId(id);
        update.setFileUrl(fileUrl);
        update.setFileName(existing.getFileName());
        update.setFileSize(existing.getFileSize());
        update.setFileChecksum(existing.getFileChecksum());
        update.setVersion(existing.getVersion());
        if (trainingMapper.updateById(update) != 1) throw exception(TRAINING_STATUS_INVALID);
        return fileUrl;
    }

    @Override
    public TrainingPublicRespVO inspectByToken(String token) {
        TrainingDO entity = validateTokenUsable(token);
        TrainingPublicRespVO respVO = new TrainingPublicRespVO();
        respVO.setCode(entity.getCode());
        respVO.setName(entity.getName());
        respVO.setTrainingTypeLabels(translateTrainingTypes(entity.getTrainingTypes()));
        respVO.setTrainingTime(entity.getTrainingTime());
        respVO.setTrainerName(entity.getTrainerName());
        respVO.setContent(entity.getContent());
        respVO.setTokenExpiresAt(entity.getTokenExpiresAt());
        respVO.setStatus(entity.getStatus());
        respVO.setConfirmationTemplateId(entity.getConfirmationTemplateId());
        respVO.setConfirmationRevisionId(entity.getConfirmationRevisionId());
        respVO.setConfirmationFormRules(confirmationRules(entity));
        if (Objects.equals(TrainingStatusEnum.CONFIRMED.getStatus(), entity.getStatus())) {
            respVO.setSignConfirmerName(entity.getSignConfirmerName());
            respVO.setSignTime(entity.getSignTime());
            respVO.setSkillRating(entity.getSkillRating());
            respVO.setEffectRating(entity.getEffectRating());
            respVO.setSatisfactionRating(entity.getSatisfactionRating());
            respVO.setSignOpinion(entity.getSignOpinion());
            respVO.setConfirmationValues(entity.getConfirmationValues());
            respVO.setSignatureImageDataUrl(entity.getSignatureImageDataUrl());
        }
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmByToken(String token, TrainingPublicConfirmReqVO reqVO) {
        TrainingDO entity = validateTokenUsable(token);
        if (!Objects.equals(TrainingStatusEnum.ISSUED.getStatus(), entity.getStatus())) {
            throw exception(TRAINING_TOKEN_INVALID);
        }
        validateRating(reqVO.getSkillRating(), SKILL_RATING_OPTIONS, "培训工程师技术水平及表达能力");
        validateRating(reqVO.getEffectRating(), SKILL_RATING_OPTIONS, "培训内容及讲解效果");
        validateRating(reqVO.getSatisfactionRating(), SATISFACTION_RATING_OPTIONS, "培训满意度");

        String signature = TrainingSignatureImage.normalize(reqVO.getSignatureImageDataUrl());
        String rules = confirmationRules(entity);
        Map<String, Object> values = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(reqVO.getConfirmationValues())) {
            var node = JsonUtils.parseTree(reqVO.getConfirmationValues());
            if (node == null || !node.isObject()) throw exception(TRAINING_ARGUMENT_INVALID, "确认内容格式不正确");
            values.putAll(JsonUtils.parseObject(reqVO.getConfirmationValues(), Map.class));
        }
        values.put("skillRating", reqVO.getSkillRating());
        values.put("effectRating", reqVO.getEffectRating());
        values.put("satisfactionRating", reqVO.getSatisfactionRating());
        values.put("signConfirmerName", reqVO.getSignConfirmerName());
        values.put("signOpinion", reqVO.getSignOpinion());
        String acceptedValues = TrainingConfirmationForms.validateValues(rules, values);
        // Persist only validated PNG and values from the frozen schema.
        entity.setSignatureImageDataUrl(signature);
        entity.setConfirmationFormRules(rules);
        entity.setConfirmationValues(acceptedValues);
        // New issuance has explicit business-grant evidence; legacy links retain their existing behavior.
        var confirmationGrant=confirmationGrants.findForConfirmation(entity);
        cn.iocoder.yudao.module.pms.platform.api.file.BusinessGrantGeneratedFileApi.RegisteredFile confirmedFile=null;
        // 先落客户确认信息，再把含客户填写区域的培训记录表上传文件服务
        TrainingDO update = new TrainingDO();
        update.setId(entity.getId());
        update.setStatus(TrainingStatusEnum.CONFIRMED.getStatus());
        update.setSignatureImageDataUrl(signature);
        update.setConfirmationFormRules(rules);
        update.setConfirmationValues(acceptedValues);
        update.setSkillRating(reqVO.getSkillRating());
        update.setEffectRating(reqVO.getEffectRating());
        update.setSatisfactionRating(reqVO.getSatisfactionRating());
        update.setSignOpinion(reqVO.getSignOpinion());
        update.setSignConfirmerName(reqVO.getSignConfirmerName());
        update.setSignTime(LocalDateTime.now());
        update.setVersion(entity.getVersion());

        entity.setStatus(TrainingStatusEnum.CONFIRMED.getStatus());
        entity.setSkillRating(reqVO.getSkillRating());
        entity.setEffectRating(reqVO.getEffectRating());
        entity.setSatisfactionRating(reqVO.getSatisfactionRating());
        entity.setSignOpinion(reqVO.getSignOpinion());
        entity.setSignConfirmerName(reqVO.getSignConfirmerName());
        entity.setSignTime(update.getSignTime());
        if(confirmationGrant==null) {
            update.setFileUrl(renderAndUpload(entity,false));
        } else {
            byte[] content=renderRecordDocument(entity).getBytes(StandardCharsets.UTF_8);
            String fileName=entity.getCode()+".html";
            confirmedFile=grantFiles.create(new cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantGeneratedFileCommand(
                    TenantContextHolder.getRequiredTenantId(),"training-grant:"+confirmationGrant.getId()+":"+digestHex(content),
                    "IMP","TRAINING_RECORD",entity.getId(),entity.getVersion(),confirmationGrant.getId(),confirmationGrant.getIssuanceVersion(),
                    "TRAINING_RECORD_HTML/"+entity.getVersion(),"TRAINING_RECORD",fileName,"text/html",content));
            String fileUrl="/api/v1/pms/training-records/"+entity.getId()+"/files/"+confirmedFile.materialId();
            entity.setFileUrl(fileUrl);entity.setFileName(fileName);entity.setFileSize((long)content.length);entity.setFileChecksum(confirmedFile.sha256());
            update.setFileUrl(fileUrl);
        }
        update.setFileName(entity.getFileName());
        update.setFileSize(entity.getFileSize());
        update.setFileChecksum(entity.getFileChecksum());
        if (trainingMapper.updateById(update) != 1) throw exception(TRAINING_STATUS_INVALID);

        archiveConfirmedDeliverable(entity);
        if(confirmedFile!=null) operationAudit.record(TenantContextHolder.getRequiredTenantId(),confirmedFile.executionUserId(),
                "training-confirm:"+confirmedFile.grantId(),"TRAINING_CUSTOMER_CONFIRMED","Training",String.valueOf(entity.getId()),"CONFIRMED",
                java.util.Map.of("subjectType","BUSINESS_GRANT","channel","PUBLIC_LINK","grantId",confirmedFile.grantId(),
                        "issuanceVersion",confirmedFile.issuanceVersion(),"executionUserId",confirmedFile.executionUserId(),
                        "trainingId",entity.getId(),"materialId",confirmedFile.materialId(),"artifactId",confirmedFile.artifactId(),"fileVersion",confirmedFile.versionNo()));
    }

    private String confirmationRules(TrainingDO entity) {
        return StringUtils.isBlank(entity.getConfirmationFormRules())
                ? TrainingConfirmationForms.safeSnapshot(TrainingConfirmationForms.defaults()) : entity.getConfirmationFormRules();
    }

    private TrainingDO validateTrainingExists(Long id) {
        TrainingDO entity = trainingMapper.selectById(id);
        if (entity == null) {
            throw exception(TRAINING_NOT_EXISTS);
        }
        return entity;
    }

    private void validateTrainingTypes(List<String> trainingTypes) {
        if (trainingTypes == null || trainingTypes.isEmpty()
                || trainingTypes.stream().anyMatch(type -> !ALLOWED_TRAINING_TYPES.contains(type))) {
            throw exception(TRAINING_ARGUMENT_INVALID, "培训类型取值无效");
        }
    }

    private void validateRating(String rating, Set<String> options, String label) {
        if (rating == null || !options.contains(rating)) {
            throw exception(TRAINING_ARGUMENT_INVALID, label + "评价取值无效");
        }
    }

    /**
     * 令牌可用性：存在、未作废、未过期；已确认的令牌仍可查看（回显确认结果）。
     */
    private TrainingDO validateTokenUsable(String token) {
        if (StringUtils.isBlank(token)) {
            throw exception(TRAINING_TOKEN_INVALID);
        }
        TrainingDO entity = trainingMapper.selectByDigest(digest(token));
        if (entity == null || Objects.equals(TrainingStatusEnum.VOID.getStatus(), entity.getStatus())) {
            throw exception(TRAINING_TOKEN_INVALID);
        }
        if (entity.getTokenExpiresAt() != null
                && entity.getTokenExpiresAt().isBefore(LocalDateTime.now())
                && !Objects.equals(TrainingStatusEnum.CONFIRMED.getStatus(), entity.getStatus())) {
            throw exception(TRAINING_TOKEN_INVALID);
        }
        return entity;
    }

    private String resolveUserNickname(Long userId) {
        adminUserApi.validateUser(userId);
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null ? user.getNickname() : null;
    }

    private String translateTrainingTypes(String trainingTypes) {
        if (StringUtils.isBlank(trainingTypes)) {
            return "";
        }
        return java.util.Arrays.stream(trainingTypes.split(","))
                .map(type -> switch (type) {
                    case "TECHNICAL_PRINCIPLE" -> "技术原理类";
                    case "PRODUCT_OPS" -> "产品运维类";
                    case "OTHER" -> "其它";
                    default -> type;
                })
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * 生成培训记录表（HTML）：实施方填写内容 + 客户填写区域（未确认时留空），真实上传文件服务。
     */
    private String renderAndUpload(TrainingDO entity, boolean employeeGeneration) {
        byte[] document = renderRecordDocument(entity).getBytes(StandardCharsets.UTF_8);
        String fileName = entity.getCode() + ".html";
        String fileUrl;
        if(employeeGeneration) {
            var registered=saveNativeDocument(entity,"HTML",fileName,"text/html",document);
            fileUrl="/api/v1/pms/training-records/"+entity.getId()+"/files/"+registered.materialId();
        } else {
            // Existing customer-token path retains its own principal; never impersonate an employee.
            fileUrl=fileApi.createFile(document,fileName,"training","text/html");
        }
        // 文件元数据回写到传入实体，由调用方随主更新一并落库
        entity.setFileUrl(fileUrl);
        entity.setFileName(fileName);
        entity.setFileSize((long) document.length);
        entity.setFileChecksum(digestHex(document));
        return fileUrl;
    }

    /**
     * 培训客户确认后自动登记交付件（ACC-01 → P06R 统一交付件）：业务结果型，锚定培训业务对象，
     * 培训记录文件仍归属 imp_eng_training 本体；同一培训幂等，不覆盖既有登记。
     */
    private void archiveConfirmedDeliverable(TrainingDO entity) {
        deliveryMaterialApi.registerBusinessResultMaterial("IMP", "training", entity.getId(),
                "TRAINING_RECORD", "training", String.valueOf(entity.getId()),
                null,
                entity.getName() + "（现场培训记录）",
                entity.getProjectId());
    }

    private cn.iocoder.yudao.module.pms.platform.api.file.NativeGeneratedFileApi.RegisteredFile saveNativeDocument(
            TrainingDO record,String format,String fileName,String media,byte[] content) {
        String digest=org.apache.commons.codec.digest.DigestUtils.sha256Hex(content);
        return generatedFiles.create(new cn.iocoder.yudao.module.pms.platform.api.file.dto.NativeGeneratedFileCommand(
                TenantContextHolder.getRequiredTenantId(),SecurityFrameworkUtils.getLoginUserId(),
                "training-"+format.toLowerCase(java.util.Locale.ROOT)+":"+record.getId()+":"+record.getVersion()+":"+digest,
                "IMP","TRAINING_RECORD",record.getId(),record.getVersion(),"TRAINING_RECORD_"+format+"/"+record.getVersion(),
                "TRAINING_RECORD",fileName,media,content));
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public String requestGeneratedFileDownload(Long trainingId,Long materialId) {
        validateTrainingExists(trainingId);
        return generatedFiles.requestDownload("IMP","training",trainingId,materialId);
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public String requestPdfDownload(Long trainingId) throws java.io.IOException {
        TrainingDO record=validateTrainingExists(trainingId);
        var candidates=deliveryMaterialApi.listByEntityAndType("IMP","training",trainingId,"TRAINING_RECORD").stream()
                .filter(m->"FILE".equals(m.materialKind()) && m.fileName()!=null && m.fileName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))
                .sorted(java.util.Comparator.comparing(cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi.DeliveryMaterialView::id).reversed()).toList();
        Long historical=null;
        for(var candidate:candidates) {
            var document=fileEvidence.inspectDocumentByArtifact(TenantContextHolder.getRequiredTenantId(),candidate.fileArtifactId(),candidate.fileVersionNo());
            if(document==null || !document.available())continue;
            if(historical==null)historical=candidate.id();
            if(("TRAINING_RECORD_PDF/"+record.getVersion()).equals(document.purposeCode()))
                return requestGeneratedFileDownload(trainingId,candidate.id());
        }
        Long actor=SecurityFrameworkUtils.getLoginUserId();
        boolean writable=actor!=null && permissions.hasAnyPermissions(actor,"pms:file:upload")
                && permissions.hasAnyPermissions(actor,"pms:imp-training:update","pms:imp-training:issue")
                && !TrainingStatusEnum.VOID.getStatus().equals(record.getStatus());
        if(writable) {
            var project=projectContexts.inspect(new cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.Query(
                    TenantContextHolder.getRequiredTenantId(),record.getProjectId(),actor));
            writable=project!=null && "ACTIVE".equals(project.lifecycleStatus());
        }
        if(!writable) {
            if(historical!=null)return requestGeneratedFileDownload(trainingId,historical);
            throw exception(TRAINING_ARGUMENT_INVALID,"No registered PDF is available for read-only download");
        }
        if(record.getPrintLayoutSnapshot()!=null && JsonUtils.parseTree(record.getPrintLayoutSnapshot()).has("engine"))
            throw exception(TRAINING_ARGUMENT_INVALID,"Use the bound print preview to save this dynamic-form PDF");
        var registered=saveNativeDocument(record,"PDF",record.getCode()+".pdf","application/pdf",TrainingPdfRenderer.render(record));
        return requestGeneratedFileDownload(trainingId,registered.materialId());
    }

    private String renderRecordDocument(TrainingDO entity) {
        boolean confirmed = Objects.equals(TrainingStatusEnum.CONFIRMED.getStatus(), entity.getStatus());
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
                .append("<title>现场培训记录 ").append(htmlEscape(entity.getCode())).append("</title></head><body>")
                .append("<h1>现场培训记录</h1>")
                .append("<p>培训记录编号：").append(htmlEscape(entity.getCode()))
                .append("　培训名称：").append(htmlEscape(entity.getName())).append("</p>")
                .append("<p>培训类型：").append(htmlEscape(translateTrainingTypes(entity.getTrainingTypes())))
                .append("　培训时间：").append(entity.getTrainingTime())
                .append("　培训工程师：").append(htmlEscape(entity.getTrainerName()))
                .append("　参训人数：").append(entity.getTraineeCount() == null ? "" : entity.getTraineeCount())
                .append("</p>")
                .append("<p>客户联系人：").append(htmlEscape(entity.getContactName()))
                .append("　联系电话：").append(htmlEscape(entity.getContactPhone())).append("</p>")
                .append("<h2>培训内容</h2><pre>")
                .append(htmlEscape(StringUtils.defaultString(entity.getContent())))
                .append("</pre>");
        if (confirmed) {
            html.append("<h2>客户填写区域</h2><table border=\"1\" cellspacing=\"0\" cellpadding=\"4\">")
                    .append("<tr><td>培训工程师技术水平及表达能力</td><td>").append(htmlEscape(entity.getSkillRating())).append("</td></tr>")
                    .append("<tr><td>培训内容及讲解效果</td><td>").append(htmlEscape(entity.getEffectRating())).append("</td></tr>")
                    .append("<tr><td>培训满意度</td><td>").append(htmlEscape(entity.getSatisfactionRating())).append("</td></tr>")
                    .append("<tr><td>综合意见</td><td>").append(htmlEscape(StringUtils.defaultString(entity.getSignOpinion()))).append("</td></tr>")
                    .append("<tr><td>签字人</td><td>").append(htmlEscape(entity.getSignConfirmerName()))
                    .append("　签字时间：").append(entity.getSignTime()).append("</td></tr>")
                    .append("</table>");
        } else {
            html.append("<h2>客户填写区域（客户确认后回填）</h2>")
                    .append("<p>培训工程师技术水平及表达能力：□很好 □良好 □一般 □差</p>")
                    .append("<p>培训内容及讲解效果：□很好 □良好 □一般 □差</p>")
                    .append("<p>培训满意度：□非常满意 □较满意 □一般 □差</p>")
                    .append("<p>综合意见：</p><p>签字：______________　日期：____年__月__日</p>");
        }
        if (confirmed && StringUtils.isNotBlank(entity.getSignatureImageDataUrl())) {
            html.append("<h2>客户手写签字</h2><img alt=\"客户手写签字\" style=\"max-width:100%;width:600px\" src=\"")
                    .append(htmlEscape(entity.getSignatureImageDataUrl())).append("\">");
        }
        if (confirmed && StringUtils.isNotBlank(entity.getConfirmationValues())) {
            var values = JsonUtils.parseTree(entity.getConfirmationValues());
            Set<String> core = Set.of("skillRating", "effectRating", "satisfactionRating", "signOpinion", "signConfirmerName", "signatureImageDataUrl");
            for (var rule : JsonUtils.parseTree(confirmationRules(entity))) {
                String field = rule.path("field").asText();
                if (!core.contains(field) && values.has(field)) html.append("<p>")
                        .append(htmlEscape(rule.path("title").asText())).append("：")
                        .append(htmlEscape(values.get(field).isTextual() ? values.get(field).asText() : values.get(field).toString())).append("</p>");
            }
        }
        html.append("</body></html>");
        return html.toString();
    }

    private String htmlEscape(String text) {
        return HtmlUtils.htmlEscape(text == null ? "" : text);
    }

    private String digestHex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

}
