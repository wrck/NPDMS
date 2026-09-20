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
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable.DeliverableDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.deliverable.DeliverableMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.TrainingMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.enums.EngStatusEnum;
import cn.iocoder.yudao.module.pms.engineering.enums.TrainingStatusEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class TrainingServiceImpl implements TrainingService {

    /**
     * Demo 6.1 培训类型固定选项
     */
    private static final Set<String> ALLOWED_TRAINING_TYPES =
            Set.of("TECHNICAL_PRINCIPLE", "PRODUCT_OPS", "OTHER");
    private static final Set<String> SKILL_RATING_OPTIONS = Set.of("很好", "良好", "一般", "差");
    private static final Set<String> SATISFACTION_RATING_OPTIONS = Set.of("非常满意", "较满意", "一般", "差");
    private static final long TOKEN_VALID_DAYS = 7;

    private static final String DELIVERABLE_SOURCE_TYPE = "TRAINING";

    @Resource
    private TrainingMapper trainingMapper;
    @Resource
    private DeliverableMapper deliverableMapper;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTraining(TrainingSaveReqVO createReqVO) {
        validateTrainingTypes(createReqVO.getTrainingTypes());
        TrainingDO entity = BeanUtils.toBean(createReqVO, TrainingDO.class);
        entity.setCode(recordCodeGenerator.next(createReqVO.getProjectId(),
                EngineeringRecordCodeGenerator.TRAINING, trainingMapper,
                TrainingDO::getProjectId, TrainingDO::getCode));
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
        String fileUrl = renderAndUpload(existing);
        update.setFileUrl(fileUrl);
        update.setFileName(existing.getFileName());
        update.setFileSize(existing.getFileSize());
        update.setFileChecksum(existing.getFileChecksum());
        if (trainingMapper.updateById(update) != 1) throw exception(TRAINING_STATUS_INVALID);
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
    public String generateRecordFile(Long id) {
        TrainingDO existing = validateTrainingExists(id);
        String fileUrl = renderAndUpload(existing);
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
        update.setFileUrl(renderAndUpload(entity));
        update.setFileName(entity.getFileName());
        update.setFileSize(entity.getFileSize());
        update.setFileChecksum(entity.getFileChecksum());
        if (trainingMapper.updateById(update) != 1) throw exception(TRAINING_STATUS_INVALID);

        archiveConfirmedDeliverable(entity, update.getFileUrl());
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
    private String renderAndUpload(TrainingDO entity) {
        byte[] document = renderRecordDocument(entity).getBytes(StandardCharsets.UTF_8);
        String fileName = entity.getCode() + ".html";
        String fileUrl = fileApi.createFile(document, fileName, "training", "text/html");
        // 文件元数据回写到传入实体，由调用方随主更新一并落库
        entity.setFileUrl(fileUrl);
        entity.setFileName(fileName);
        entity.setFileSize((long) document.length);
        entity.setFileChecksum(digestHex(document));
        return fileUrl;
    }

    private void archiveConfirmedDeliverable(TrainingDO entity, String fileUrl) {
        DeliverableDO existing = deliverableMapper.selectByProjectAndSource(
                entity.getProjectId(), DELIVERABLE_SOURCE_TYPE, entity.getId());
        if (existing != null) {
            // 交付件归集版本不可覆盖：同一来源重复确认（不应发生）时保持既有归集
            log.info("培训记录 {} 的交付件已归集（{}），不重复归档", entity.getId(), existing.getCode());
            return;
        }
        DeliverableDO deliverable = new DeliverableDO();
        deliverable.setProjectId(entity.getProjectId());
        deliverable.setCode(recordCodeGenerator.next(entity.getProjectId(),
                EngineeringRecordCodeGenerator.DELIVERABLE, deliverableMapper,
                DeliverableDO::getProjectId, DeliverableDO::getCode));
        deliverable.setName(entity.getName() + "（现场培训记录）");
        deliverable.setDeliverableType("TRAINING");
        deliverable.setSourceType(DELIVERABLE_SOURCE_TYPE);
        deliverable.setSourceId(entity.getId());
        deliverable.setFileUrl(fileUrl);
        deliverable.setFileSize(entity.getFileSize());
        deliverable.setFileChecksum(entity.getFileChecksum());
        deliverable.setStatus(EngStatusEnum.DELIVERABLE_ARCHIVED);
        deliverable.setArchivedTime(entity.getSignTime());
        deliverable.setArchivedBy(entity.getTrainerUserId());
        deliverable.setRemark("ACC-01 现场培训客户确认后自动归档，签字人：" + entity.getSignConfirmerName());
        deliverable.setVersion(0);
        deliverableMapper.insert(deliverable);
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
