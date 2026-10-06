package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingIssueRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingPublicConfirmReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingPublicRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;

/**
 * PMS 现场培训记录 Service（ACC-01，Demo 6.1）。
 * <p>
 * 外发令牌仅存 SHA-256 摘要，原始令牌只在外发响应返回一次；
 * 客户确认后自动归档交付件（sourceType=TRAINING）。
 * 外部推送通道（短信/钉钉）未接入，外发动作返回链接由实施人员线下送达。
 */
public interface TrainingService {

    Long createTraining(TrainingSaveReqVO createReqVO);

    void updateTraining(TrainingSaveReqVO updateReqVO);

    void deleteTraining(Long id);

    TrainingDO getTraining(Long id);

    PageResult<TrainingDO> getTrainingPage(TrainingPageReqVO pageReqVO);

    /**
     * 外发培训记录：生成新令牌（原令牌失效）、生成培训记录表文件，状态置为已外发。
     */
    TrainingIssueRespVO issueTraining(Long id);

    /**
     * 作废培训记录：草稿/已外发可作废，客户已确认不可作废。
     */
    void voidTraining(Long id);

    /**
     * （重新）生成培训记录表文件并上传文件服务，返回文件URL。
     */
    String generateRecordFile(Long id);
    String requestGeneratedFileDownload(Long trainingId,Long materialId);
    String requestPdfDownload(Long trainingId) throws java.io.IOException;

    /**
     * 公开端：按令牌查看培训记录（客户移动端）。
     */
    TrainingPublicRespVO inspectByToken(String token);

    /**
     * 公开端：客户签字确认，随后自动归档交付件。
     */
    void confirmByToken(String token, TrainingPublicConfirmReqVO reqVO);
}
