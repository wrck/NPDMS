package cn.iocoder.yudao.module.pms.service.service.srvreport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvreport.vo.SrvReportPageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvreport.vo.SrvReportSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvreport.SrvReportDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.srvreport.SrvReportMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_REPORT_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_REPORT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_REPORT_STATUS_INVALID;

/**
 * 巡检报告 Service 实现类
 */
@Service
@Validated
public class SrvReportServiceImpl implements SrvReportService {

    /**
     * 报告状态：0草稿
     */
    private static final int STATUS_DRAFT = 0;
    /**
     * 报告状态：1已生成
     */
    private static final int STATUS_GENERATED = 1;
    /**
     * 报告状态：2已归档
     */
    private static final int STATUS_ARCHIVED = 2;

    @Resource
    private SrvReportMapper srvReportMapper;

    @Override
    public Long createSrvReport(SrvReportSaveReqVO createReqVO) {
        validateCodeUnique(null, createReqVO.getTaskId(), createReqVO.getCode());
        SrvReportDO report = BeanUtils.toBean(createReqVO, SrvReportDO.class);
        if (report.getStatus() == null) {
            report.setStatus(STATUS_DRAFT);
        }
        if (report.getReportType() == null) {
            report.setReportType("STANDARD");
        }
        srvReportMapper.insert(report);
        return report.getId();
    }

    @Override
    public void updateSrvReport(SrvReportSaveReqVO updateReqVO) {
        SrvReportDO existing = validateSrvReportExists(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getTaskId(), updateReqVO.getCode());
        // 已归档的报告不允许修改
        if (Objects.equals(existing.getStatus(), STATUS_ARCHIVED)) {
            throw exception(SRV_REPORT_STATUS_INVALID);
        }
        SrvReportDO updateObj = BeanUtils.toBean(updateReqVO, SrvReportDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        srvReportMapper.updateById(updateObj);
    }

    @Override
    public void deleteSrvReport(Long id) {
        SrvReportDO existing = validateSrvReportExists(id);
        // 已归档的报告不允许删除
        if (Objects.equals(existing.getStatus(), STATUS_ARCHIVED)) {
            throw exception(SRV_REPORT_STATUS_INVALID);
        }
        srvReportMapper.deleteById(id);
    }

    @Override
    public PageResult<SrvReportDO> getSrvReportPage(SrvReportPageReqVO pageReqVO) {
        return srvReportMapper.selectPage(pageReqVO);
    }

    @Override
    public SrvReportDO getSrvReport(Long id) {
        return srvReportMapper.selectById(id);
    }

    @Override
    public void generateSrvReport(Long id) {
        SrvReportDO report = validateSrvReportExists(id);
        if (!Objects.equals(report.getStatus(), STATUS_DRAFT)) {
            throw exception(SRV_REPORT_STATUS_INVALID);
        }
        SrvReportDO updateObj = new SrvReportDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_GENERATED);
        updateObj.setGeneratedTime(LocalDateTime.now());
        srvReportMapper.updateById(updateObj);
    }

    @Override
    public void archiveSrvReport(Long id) {
        SrvReportDO report = validateSrvReportExists(id);
        if (!Objects.equals(report.getStatus(), STATUS_GENERATED)) {
            throw exception(SRV_REPORT_STATUS_INVALID);
        }
        updateStatus(id, STATUS_ARCHIVED);
    }

    private void updateStatus(Long id, int status) {
        SrvReportDO updateObj = new SrvReportDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        srvReportMapper.updateById(updateObj);
    }

    private SrvReportDO validateSrvReportExists(Long id) {
        if (id == null) {
            throw exception(SRV_REPORT_NOT_EXISTS);
        }
        SrvReportDO report = srvReportMapper.selectById(id);
        if (report == null) {
            throw exception(SRV_REPORT_NOT_EXISTS);
        }
        return report;
    }

    private void validateCodeUnique(Long id, Long taskId, String code) {
        if (taskId == null || code == null) {
            return;
        }
        SrvReportDO existing = srvReportMapper.selectByTaskIdAndCode(taskId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(SRV_REPORT_CODE_DUPLICATE, code);
        }
    }

}
