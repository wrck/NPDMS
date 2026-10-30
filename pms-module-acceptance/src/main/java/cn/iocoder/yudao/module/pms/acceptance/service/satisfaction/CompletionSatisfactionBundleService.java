package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionCertificateDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateDeviceMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificateDeliveryLockQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.*;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptance.NativeAcceptanceDeliveryAccess;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Additive external-customer channel; preserves the existing certificate state machine and history. */
@Service
@RequiredArgsConstructor
public class CompletionSatisfactionBundleService {
    private final cn.iocoder.yudao.module.pms.platform.api.file.BusinessGrantGeneratedFileApi generatedDocuments;
    private final SatisfactionQuestionnaireMapper documentQuestionnaires;
    private final SatisfactionResponseMapper documentResponses;
    private final CompletionSatisfactionBundleMapper bundles;
    private final CompletionSatisfactionSigningMapper signings;
    private final CompletionCertificateMapper certificates;
    private final CompletionCertificateDeviceMapper devices;
    private final NativeAcceptanceDeliveryAccess access;
    private final PlatformDeliveryMaterialApi deliveryMaterials;
    private final cn.iocoder.yudao.module.pms.engineering.api.arrival.SignedArrivalDateApi arrivalDates;

    public void requireStandaloneAllowed(Long tenantId, String collectionKey) {
        if (collectionKey != null && bundles.selectByCollection(new CompletionSatisfactionBundleQuery(tenantId, collectionKey)) != null)
            throw new IllegalStateException("COMPLETION_SATISFACTION_JOINT_LINK_REQUIRED");
    }

    public CertificateSnapshot existing(Long tenantId, Long actorId, SatisfactionTaskManagementService.TaskView task) {
        var bundle = bundles.selectByCollection(new CompletionSatisfactionBundleQuery(tenantId, task.collectionKey()));
        if (bundle == null) return null;
        if (!Objects.equals(bundle.getProjectId(), task.projectId()))
            throw new IllegalStateException("COMPLETION_SATISFACTION_PAIRING_CONFLICT");
        var scope = new SatisfactionAccessGrantDO();
        scope.setCompletionBundleId(bundle.getId()); scope.setCreator(actorId.toString());
        return inspect(tenantId, scope);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long bind(Long tenantId, Long actorId, SatisfactionCollectionTaskDO task, Long certificateId) {
        if (certificateId == null || certificateId <= 0 || task == null
                || !Objects.equals(tenantId, task.getTenantId()) || task.getCollectionKey() == null)
            throw new IllegalArgumentException("COMPLETION_SATISFACTION_CERTIFICATE_REQUIRED");
        access.requireCommand(tenantId, actorId, "completionCertificate", task.getProjectId(), "submit");
        access.require(tenantId, actorId, "completionCertificate", certificateId.toString(),
                "COMPLETION_CERTIFICATE", false, false, null);
        var certificate = certificates.selectDeliveryOwnerForUpdate(new CompletionCertificateDeliveryLockQuery(tenantId, certificateId));
        requireIdentity(tenantId, task.getProjectId(), certificate);
        var existing = bundles.selectByCollectionForUpdate(new CompletionSatisfactionBundleQuery(tenantId, task.getCollectionKey()));
        if (existing != null) {
            if (!Objects.equals(existing.getCertificateId(), certificateId)
                    || !Objects.equals(existing.getProjectId(), task.getProjectId()))
                throw new IllegalStateException("COMPLETION_SATISFACTION_PAIRING_CONFLICT");
            requireSignable(existing, certificate);
            return existing.getId();
        }
        if (!Integer.valueOf(1).equals(certificate.getStatus()) || certificate.getVersion() == null)
            throw new IllegalStateException("COMPLETION_SATISFACTION_PENDING_CERTIFICATE_REQUIRED");
        var deviceSnapshot = devices.selectListByCertificateId(certificateId).stream()
                .map(device -> new Device(device.getDeviceType(), device.getDeviceModel(), device.getQuantity())).toList();
        var row = new CompletionSatisfactionBundleDO();
        row.setId(IdWorker.getId()); row.setTenantId(tenantId); row.setProjectId(task.getProjectId());
        row.setCertificateId(certificateId); row.setCertificateVersion(certificate.getVersion());
        row.setCollectionKey(task.getCollectionKey()); row.setVersion(0L);
        var automaticArrival = arrivalDates.latest(new cn.iocoder.yudao.module.pms.engineering.api.arrival.SignedArrivalDateApi.Query(tenantId,certificate.getProjectId()));
        row.setCertificateSnapshot(JsonUtils.toJsonString(new CertificateSnapshot(certificate.getId(), certificate.getVersion(),
                certificate.getProjectId(), certificate.getCode(), certificate.getName(), certificate.getCertificateNo(),
                certificate.getCustomerUnit(), certificate.getContractNo(), certificate.getServiceType(),
                certificate.getEngineerName(), certificate.getEngineerContact(), certificate.getItemArrival(),
                certificate.getItemInstall(), certificate.getItemCutover(), certificate.getItemTraining(),
                certificate.getItemDocs(), certificate.getCompletionDate(), deviceSnapshot,
                automaticArrival != null ? automaticArrival : certificate.getManualArrivalDate())));
        row.setCreator(actorId.toString()); row.setUpdater(actorId.toString());
        if (bundles.insert(row) != 1) throw new IllegalStateException("COMPLETION_SATISFACTION_BIND_FAILED");
        return row.getId();
    }

    public CertificateSnapshot inspect(Long tenantId, SatisfactionAccessGrantDO grant) {
        if (grant.getCompletionBundleId() == null) return null; // Previously issued standalone grants retain their scope.
        var bundle = requireBundle(tenantId, grant.getCompletionBundleId());
        Long actorId = issuer(grant);
        access.require(tenantId, actorId, "completionCertificate", bundle.getCertificateId().toString(),
                "COMPLETION_CERTIFICATE", false, false, null);
        var certificate = certificates.selectById(bundle.getCertificateId());
        requireIdentity(tenantId, bundle.getProjectId(), certificate);
        requireSignable(bundle, certificate);
        return JsonUtils.parseObject(bundle.getCertificateSnapshot(), CertificateSnapshot.class);
    }

    /** Runs inside Response submission, after PLT has locked and revalidated the canonical signature. */
    public record Confirmation(Long tenantId, SatisfactionAccessGrantDO grant, SatisfactionCollectionTaskDO task,
                               Long responseId, Boolean confirmed,
                               SatisfactionResponseSubmissionService.FileFact signature, String signatureImage,
                               cn.iocoder.yudao.module.pms.acceptance.domain.satisfaction.SatisfactionQuestionnaireDefinition.Evaluation evaluation) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public void confirm(Confirmation command) {
        Long tenantId = command.tenantId();
        var grant = command.grant();
        var task = command.task();
        Long responseId = command.responseId();
        Boolean confirmed = command.confirmed();
        var signature = command.signature();
        String signatureImage = command.signatureImage();
        if (grant.getCompletionBundleId() == null) return;
        if (!Objects.equals(tenantId, grant.getTenantId()) || !Objects.equals(tenantId, task.getTenantId())
                || !Objects.equals(grant.getQuestionnaireId(), task.getQuestionnaireId()))
            throw new IllegalStateException("COMPLETION_SATISFACTION_PAIRING_CONFLICT");
        if (!Boolean.TRUE.equals(confirmed) || signature == null || !"SIGNATURE".equals(signature.role()))
            throw new IllegalArgumentException("COMPLETION_SATISFACTION_CONFIRMATION_REQUIRED");
        if(signatureImage!=null){
            if(!signatureImage.startsWith("data:image/png;base64,") || signatureImage.length()>2_800_000)
                throw new IllegalArgumentException("COMPLETION_SIGNATURE_IMAGE_INVALID");
            byte[] content=java.util.Base64.getDecoder().decode(signatureImage.substring("data:image/png;base64,".length()));
            if(!cn.hutool.crypto.digest.DigestUtil.sha256Hex(content).equals(signature.sha256()))
                throw new IllegalArgumentException("COMPLETION_SIGNATURE_IMAGE_MISMATCH");
        }
        var bundle = requireBundle(tenantId, grant.getCompletionBundleId());
        if (!Objects.equals(bundle.getProjectId(), task.getProjectId())
                || !Objects.equals(bundle.getCollectionKey(), task.getCollectionKey()))
            throw new IllegalStateException("COMPLETION_SATISFACTION_PAIRING_CONFLICT");
        Long actorId = issuer(grant);
        access.requireCommand(tenantId, actorId, "completionCertificate", bundle.getProjectId(), "submit");
        access.require(tenantId, actorId, "completionCertificate", bundle.getCertificateId().toString(),
                "COMPLETION_CERTIFICATE", false, false, null);
        var certificate = certificates.selectDeliveryOwnerForUpdate(new CompletionCertificateDeliveryLockQuery(tenantId, bundle.getCertificateId()));
        requireIdentity(tenantId, task.getProjectId(), certificate);
        if(certificate.getSatisfactionTemplateId()!=null && signatureImage==null)
            throw new IllegalArgumentException("COMPLETION_SIGNATURE_IMAGE_REQUIRED");
        boolean pending = requireSignable(bundle, certificate);
        var evaluation = command.evaluation();
        if (evaluation == null || !evaluation.requiredComplete())
            throw new IllegalStateException("COMPLETION_SATISFACTION_EVALUATION_REQUIRED");
        boolean first = pending && evaluation.passed();
        LocalDateTime now = LocalDateTime.now();
        String actorRef = "BUSINESS_GRANT:" + grant.getId();
        if (first && bundles.confirmPendingCertificate(new CompletionSatisfactionConfirmUpdate(tenantId,
                certificate.getId(), bundle.getCertificateVersion(), now, actorRef)) != 1)
            throw new IllegalStateException("COMPLETION_SATISFACTION_CERTIFICATE_VERSION_CONFLICT");
        var signing = new CompletionSatisfactionSigningDO();
        signing.setId(IdWorker.getId()); signing.setTenantId(tenantId); signing.setBundleId(bundle.getId());
        signing.setQuestionnaireId(grant.getQuestionnaireId()); signing.setResponseId(responseId); signing.setGrantId(grant.getId());
        signing.setSignatureFileFact(JsonUtils.toJsonString(signature)); signing.setSignedAt(now); signing.setVersion(0L);
        signing.setSignatureImageDataUrl(signatureImage);
        signing.setCreator(actorRef); signing.setUpdater(actorRef);
        if (signings.insert(signing) != 1) throw new IllegalStateException("COMPLETION_SATISFACTION_SIGNING_FAILED");
        if (first) deliveryMaterials.registerBusinessResultMaterial("ACC", "completionCertificate", certificate.getId(),
                "COMPLETION_CERTIFICATE", "completionCertificate", certificate.getId().toString(), null,
                certificate.getName(), certificate.getProjectId());
        if(certificate.getSatisfactionTemplateId()!=null && evaluation.passed()){
            var question=documentQuestionnaires.selectById(grant.getQuestionnaireId());var response=documentResponses.selectById(responseId);
            if(question==null || response==null || !Objects.equals(tenantId,question.getTenantId()) || !Objects.equals(tenantId,response.getTenantId()))
                throw new IllegalStateException("COMPLETION_DOCUMENT_FACTS_REQUIRED");
            Long documentVersion=first?Math.addExact(bundle.getCertificateVersion(),1L):certificate.getVersion();
            var snapshot=JsonUtils.parseObject(bundle.getCertificateSnapshot(),CertificateSnapshot.class);
            for(String kind:first?java.util.List.of("COMPLETION","SATISFACTION"):java.util.List.of("SATISFACTION")){
                byte[] content=cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate.CompletionConfirmationDocumentRenderer.render(
                        kind,snapshot,question.getFrozenQuestionJson(),response.getAnswerSnapshot(),signatureImage,now);
                generatedDocuments.create(new cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantGeneratedFileCommand(tenantId,
                        "completion-confirmation:"+responseId+":"+kind,"ACC","COMPLETION_CONFIRMATION",certificate.getId(),documentVersion,
                        grant.getId(),grant.getGrantVersion().longValue(),kind+"_HTML/"+documentVersion,"COMPLETION_CERTIFICATE",
                        certificate.getCode()+"-"+kind+".html","text/html",content));
            }
        }
    }

    private CompletionSatisfactionBundleDO requireBundle(Long tenantId, Long id) {
        var bundle = bundles.selectById(id);
        if (bundle == null || !Objects.equals(tenantId, bundle.getTenantId()))
            throw new IllegalStateException("COMPLETION_SATISFACTION_BUNDLE_UNAVAILABLE");
        return bundle;
    }

    private void requireIdentity(Long tenantId, Long projectId, CompletionCertificateDO certificate) {
        if (certificate == null || !Objects.equals(tenantId, certificate.getTenantId())
                || !Objects.equals(projectId, certificate.getProjectId()))
            throw new IllegalStateException("COMPLETION_SATISFACTION_CERTIFICATE_IDENTITY_CONFLICT");
    }

    /** A pending bundle may confirm only after a passing signed submission; failed signatures remain historical. */
    private boolean requireSignable(CompletionSatisfactionBundleDO bundle, CompletionCertificateDO certificate) {
        var first = signings.selectFirstSigning(new CompletionSatisfactionSigningQuery(bundle.getTenantId(), bundle.getId()));
        if (Integer.valueOf(1).equals(certificate.getStatus())) {
            if (!Objects.equals(bundle.getCertificateVersion(), certificate.getVersion()))
                throw new IllegalStateException("COMPLETION_SATISFACTION_CERTIFICATE_VERSION_CONFLICT");
            return true;
        }
        // Failed signatures leave the certificate pending; successful remediation can confirm it later.
        // Previously confirmed historical certificates and their signatures remain immutable.
        if (first == null || !(Integer.valueOf(2).equals(certificate.getStatus()) || Integer.valueOf(3).equals(certificate.getStatus())))
            throw new IllegalStateException("COMPLETION_SATISFACTION_CERTIFICATE_STATE_CONFLICT");
        return false;
    }

    private Long issuer(SatisfactionAccessGrantDO grant) {
        try {
            long actorId = Long.parseLong(grant.getCreator());
            if (actorId > 0) return actorId;
        } catch (RuntimeException invalid) { /* Fail closed; no synthetic authenticated actor. */ }
        throw new IllegalStateException("COMPLETION_SATISFACTION_GRANT_ISSUER_INVALID");
    }

    public record Device(String deviceType, String deviceModel, Integer quantity) { }
    public record CertificateSnapshot(Long id, Long version, Long projectId, String code, String name,
                                      String certificateNo, String customerUnit, String contractNo, String serviceType,
                                      String engineerName, String engineerContact, String itemArrival, String itemInstall,
                                      String itemCutover, String itemTraining, String itemDocs, LocalDate completionDate,
                                      List<Device> devices, LocalDate arrivalDate) {
        public CertificateSnapshot(Long id, Long version, Long projectId, String code, String name,
                String certificateNo, String customerUnit, String contractNo, String serviceType,
                String engineerName, String engineerContact, String itemArrival, String itemInstall,
                String itemCutover, String itemTraining, String itemDocs, LocalDate completionDate, List<Device> devices) {
            this(id,version,projectId,code,name,certificateNo,customerUnit,contractNo,serviceType,engineerName,engineerContact,
                    itemArrival,itemInstall,itemCutover,itemTraining,itemDocs,completionDate,devices,null);
        }
    }
}
