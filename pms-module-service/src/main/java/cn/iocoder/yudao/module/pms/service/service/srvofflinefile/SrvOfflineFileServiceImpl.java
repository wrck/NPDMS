package cn.iocoder.yudao.module.pms.service.service.srvofflinefile;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo.SrvOfflineFilePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo.SrvOfflineFileSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvofflinefile.SrvOfflineFileDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.srvofflinefile.SrvOfflineFileMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_OFFLINE_FILE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_OFFLINE_FILE_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_OFFLINE_FILE_STATUS_INVALID;

/**
 * 离线巡检文件 Service 实现类
 */
@Service
@Validated
public class SrvOfflineFileServiceImpl implements SrvOfflineFileService {

    /**
     * 解析状态：0待解析
     */
    private static final int PARSE_STATUS_PENDING = 0;
    /**
     * 解析状态：1解析中
     */
    private static final int PARSE_STATUS_PARSING = 1;
    /**
     * 解析状态：2解析成功
     */
    private static final int PARSE_STATUS_SUCCESS = 2;
    /**
     * 解析状态：3解析失败
     */
    private static final int PARSE_STATUS_FAILED = 3;

    @Resource
    private SrvOfflineFileMapper srvOfflineFileMapper;

    @Override
    public Long createSrvOfflineFile(SrvOfflineFileSaveReqVO createReqVO) {
        validateCodeUnique(null, createReqVO.getTaskId(), createReqVO.getCode());
        SrvOfflineFileDO offlineFile = BeanUtils.toBean(createReqVO, SrvOfflineFileDO.class);
        if (offlineFile.getParseStatus() == null) {
            offlineFile.setParseStatus(PARSE_STATUS_PENDING);
        }
        srvOfflineFileMapper.insert(offlineFile);
        return offlineFile.getId();
    }

    @Override
    public void updateSrvOfflineFile(SrvOfflineFileSaveReqVO updateReqVO) {
        SrvOfflineFileDO existing = validateSrvOfflineFileExists(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getTaskId(), updateReqVO.getCode());
        SrvOfflineFileDO updateObj = BeanUtils.toBean(updateReqVO, SrvOfflineFileDO.class);
        // 保持解析状态不被前端覆盖
        updateObj.setParseStatus(existing.getParseStatus());
        srvOfflineFileMapper.updateById(updateObj);
    }

    @Override
    public void deleteSrvOfflineFile(Long id) {
        validateSrvOfflineFileExists(id);
        srvOfflineFileMapper.deleteById(id);
    }

    @Override
    public PageResult<SrvOfflineFileDO> getSrvOfflineFilePage(SrvOfflineFilePageReqVO pageReqVO) {
        return srvOfflineFileMapper.selectPage(pageReqVO);
    }

    @Override
    public SrvOfflineFileDO getSrvOfflineFile(Long id) {
        return srvOfflineFileMapper.selectById(id);
    }

    @Override
    public void startParse(Long id) {
        SrvOfflineFileDO offlineFile = validateSrvOfflineFileExists(id);
        if (!Objects.equals(offlineFile.getParseStatus(), PARSE_STATUS_PENDING)) {
            throw exception(SRV_OFFLINE_FILE_STATUS_INVALID);
        }
        SrvOfflineFileDO updateObj = new SrvOfflineFileDO();
        updateObj.setId(id);
        updateObj.setParseStatus(PARSE_STATUS_PARSING);
        updateObj.setParsedTime(LocalDateTime.now());
        srvOfflineFileMapper.updateById(updateObj);
    }

    @Override
    public void parseSuccess(Long id) {
        SrvOfflineFileDO offlineFile = validateSrvOfflineFileExists(id);
        if (!Objects.equals(offlineFile.getParseStatus(), PARSE_STATUS_PARSING)) {
            throw exception(SRV_OFFLINE_FILE_STATUS_INVALID);
        }
        updateParseStatus(id, PARSE_STATUS_SUCCESS);
    }

    @Override
    public void parseFailed(Long id) {
        SrvOfflineFileDO offlineFile = validateSrvOfflineFileExists(id);
        if (!Objects.equals(offlineFile.getParseStatus(), PARSE_STATUS_PARSING)) {
            throw exception(SRV_OFFLINE_FILE_STATUS_INVALID);
        }
        updateParseStatus(id, PARSE_STATUS_FAILED);
    }

    private void updateParseStatus(Long id, int parseStatus) {
        SrvOfflineFileDO updateObj = new SrvOfflineFileDO();
        updateObj.setId(id);
        updateObj.setParseStatus(parseStatus);
        updateObj.setParsedTime(LocalDateTime.now());
        srvOfflineFileMapper.updateById(updateObj);
    }

    private SrvOfflineFileDO validateSrvOfflineFileExists(Long id) {
        if (id == null) {
            throw exception(SRV_OFFLINE_FILE_NOT_EXISTS);
        }
        SrvOfflineFileDO offlineFile = srvOfflineFileMapper.selectById(id);
        if (offlineFile == null) {
            throw exception(SRV_OFFLINE_FILE_NOT_EXISTS);
        }
        return offlineFile;
    }

    private void validateCodeUnique(Long id, Long taskId, String code) {
        if (taskId == null || code == null) {
            return;
        }
        SrvOfflineFileDO existing = srvOfflineFileMapper.selectByTaskIdAndCode(taskId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(SRV_OFFLINE_FILE_CODE_DUPLICATE, code);
        }
    }

}
