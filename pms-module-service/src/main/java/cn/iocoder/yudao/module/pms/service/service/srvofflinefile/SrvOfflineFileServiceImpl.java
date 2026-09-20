package cn.iocoder.yudao.module.pms.service.service.srvofflinefile;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo.SrvOfflineFilePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo.SrvOfflineFileSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvofflinefile.SrvOfflineFileRetiredDO;
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
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Service
@Validated
@Deprecated
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
    public Long createSrvOfflineFileRetired(SrvOfflineFileSaveReqVO createReqVO) {
        validateCodeUnique(null, createReqVO.getTaskId(), createReqVO.getCode());
        SrvOfflineFileRetiredDO offlineFile = BeanUtils.toBean(createReqVO, SrvOfflineFileRetiredDO.class);
        if (offlineFile.getParseStatus() == null) {
            offlineFile.setParseStatus(PARSE_STATUS_PENDING);
        }
        srvOfflineFileMapper.insert(offlineFile);
        return offlineFile.getId();
    }

    @Override
    public void updateSrvOfflineFileRetired(SrvOfflineFileSaveReqVO updateReqVO) {
        SrvOfflineFileRetiredDO existing = validateSrvOfflineFileExists(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getTaskId(), updateReqVO.getCode());
        SrvOfflineFileRetiredDO updateObj = BeanUtils.toBean(updateReqVO, SrvOfflineFileRetiredDO.class);
        // 保持解析状态不被前端覆盖
        updateObj.setParseStatus(existing.getParseStatus());
        srvOfflineFileMapper.updateById(updateObj);
    }

    @Override
    public void deleteSrvOfflineFileRetired(Long id) {
        validateSrvOfflineFileExists(id);
        srvOfflineFileMapper.deleteById(id);
    }

    @Override
    public PageResult<SrvOfflineFileRetiredDO> getSrvOfflineFilePageRetired(SrvOfflineFilePageReqVO pageReqVO) {
        return srvOfflineFileMapper.selectPageRetired(pageReqVO);
    }

    @Override
    public SrvOfflineFileRetiredDO getSrvOfflineFileRetired(Long id) {
        return srvOfflineFileMapper.selectById(id);
    }

    @Override
    public void startParseRetired(Long id) {
        SrvOfflineFileRetiredDO offlineFile = validateSrvOfflineFileExists(id);
        if (!Objects.equals(offlineFile.getParseStatus(), PARSE_STATUS_PENDING)) {
            throw exception(SRV_OFFLINE_FILE_STATUS_INVALID);
        }
        SrvOfflineFileRetiredDO updateObj = new SrvOfflineFileRetiredDO();
        updateObj.setId(id);
        updateObj.setParseStatus(PARSE_STATUS_PARSING);
        updateObj.setParsedTime(LocalDateTime.now());
        srvOfflineFileMapper.updateById(updateObj);
    }

    @Override
    public void parseSuccessRetired(Long id) {
        SrvOfflineFileRetiredDO offlineFile = validateSrvOfflineFileExists(id);
        if (!Objects.equals(offlineFile.getParseStatus(), PARSE_STATUS_PARSING)) {
            throw exception(SRV_OFFLINE_FILE_STATUS_INVALID);
        }
        updateParseStatus(id, PARSE_STATUS_SUCCESS);
    }

    @Override
    public void parseFailedRetired(Long id) {
        SrvOfflineFileRetiredDO offlineFile = validateSrvOfflineFileExists(id);
        if (!Objects.equals(offlineFile.getParseStatus(), PARSE_STATUS_PARSING)) {
            throw exception(SRV_OFFLINE_FILE_STATUS_INVALID);
        }
        updateParseStatus(id, PARSE_STATUS_FAILED);
    }

    private void updateParseStatus(Long id, int parseStatus) {
        SrvOfflineFileRetiredDO updateObj = new SrvOfflineFileRetiredDO();
        updateObj.setId(id);
        updateObj.setParseStatus(parseStatus);
        updateObj.setParsedTime(LocalDateTime.now());
        srvOfflineFileMapper.updateById(updateObj);
    }

    private SrvOfflineFileRetiredDO validateSrvOfflineFileExists(Long id) {
        if (id == null) {
            throw exception(SRV_OFFLINE_FILE_NOT_EXISTS);
        }
        SrvOfflineFileRetiredDO offlineFile = srvOfflineFileMapper.selectById(id);
        if (offlineFile == null) {
            throw exception(SRV_OFFLINE_FILE_NOT_EXISTS);
        }
        return offlineFile;
    }

    private void validateCodeUnique(Long id, Long taskId, String code) {
        if (taskId == null || code == null) {
            return;
        }
        SrvOfflineFileRetiredDO existing = srvOfflineFileMapper.selectByTaskIdAndCodeRetired(taskId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(SRV_OFFLINE_FILE_CODE_DUPLICATE, code);
        }
    }

}
