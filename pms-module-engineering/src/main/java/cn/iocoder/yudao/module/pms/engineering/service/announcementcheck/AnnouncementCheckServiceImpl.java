package cn.iocoder.yudao.module.pms.engineering.service.announcementcheck;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo.AnnouncementCheckHandleReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo.AnnouncementCheckPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo.AnnouncementCheckSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.announcement.AnnouncementDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.announcementcheck.AnnouncementCheckDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.announcementcheck.AnnouncementCheckMapper;
import cn.iocoder.yudao.module.pms.engineering.service.announcement.AnnouncementService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 公告预检查 Service 实现（FR-ENG-009）。
 * <p>
 * 状态流转：0 待检查 → 1 已检查 → 2 已处置 / 3 已忽略。
 * 创建检查记录后系统自动执行匹配规则，输出命中结果和EOS/EOM状态。
 */
@Service
@Validated
@Slf4j
public class AnnouncementCheckServiceImpl implements AnnouncementCheckService {

    /**
     * 状态：0 待检查
     */
    public static final int STATUS_PENDING = 0;
    /**
     * 状态：1 已检查
     */
    public static final int STATUS_CHECKED = 1;
    /**
     * 状态：2 已处置
     */
    public static final int STATUS_HANDLED = 2;
    /**
     * 状态：3 已忽略
     */
    public static final int STATUS_IGNORED = 3;

    /**
     * 处置动作：处置
     */
    public static final String ACTION_HANDLE = "HANDLE";
    /**
     * 处置动作：忽略
     */
    public static final String ACTION_IGNORE = "IGNORE";

    /**
     * 匹配结果：命中
     */
    public static final String MATCH_HIT = "HIT";
    /**
     * 匹配结果：未命中
     */
    public static final String MATCH_MISS = "MISS";
    /**
     * 匹配结果：未知
     */
    public static final String MATCH_UNKNOWN = "UNKNOWN";

    @Resource
    private AnnouncementCheckMapper announcementCheckMapper;

    /**
     * 延迟注入避免循环依赖。
     */
    @Resource
    @Lazy
    private AnnouncementService announcementService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAnnouncementCheck(AnnouncementCheckSaveReqVO createReqVO) {
        // 1. 校验编号全局唯一
        validateCodeUnique(createReqVO.getCode(), null);
        // 2. 校验项目存在
        validateProjectExists(createReqVO.getProjectId());
        // 3. 校验关联技术公告存在
        AnnouncementDO announcement = announcementService.validateAnnouncementExists(createReqVO.getAnnouncementId());
        // 4. 转换并写入，初始状态为待检查
        AnnouncementCheckDO entity = BeanUtils.toBean(createReqVO, AnnouncementCheckDO.class);
        entity.setStatus(STATUS_PENDING);
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        // 默认匹配结果为未知，待执行检查后更新
        if (StringUtils.isBlank(entity.getMatchResult())) {
            entity.setMatchResult(MATCH_UNKNOWN);
        }
        // 若未填写设备型号，自动从公告中带入适用设备型号
        if (StringUtils.isBlank(entity.getDeviceModel()) && StringUtils.isNotBlank(announcement.getProductModel())) {
            entity.setDeviceModel(announcement.getProductModel());
        }
        announcementCheckMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAnnouncementCheck(AnnouncementCheckSaveReqVO updateReqVO) {
        // 1. 校验存在
        AnnouncementCheckDO existing = validateAnnouncementCheckExists(updateReqVO.getId());
        // 2. 状态校验：仅 0 待检查 可编辑
        validateStatus(existing, STATUS_PENDING);
        // 3. 乐观锁版本校验
        validateVersion(existing, updateReqVO.getVersion());
        // 4. 编号不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(ANN_CHECK_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 5. 更新
        AnnouncementCheckDO update = BeanUtils.toBean(updateReqVO, AnnouncementCheckDO.class);
        announcementCheckMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAnnouncementCheck(Long id) {
        // 1. 校验存在
        AnnouncementCheckDO existing = validateAnnouncementCheckExists(id);
        // 2. 状态校验：仅 0 待检查 可删除
        validateStatus(existing, STATUS_PENDING);
        // 3. 删除
        announcementCheckMapper.deleteById(id);
    }

    @Override
    public AnnouncementCheckDO getAnnouncementCheck(Long id) {
        return announcementCheckMapper.selectById(id);
    }

    @Override
    public AnnouncementCheckDO validateAnnouncementCheckExists(Long id) {
        AnnouncementCheckDO entity = announcementCheckMapper.selectById(id);
        if (entity == null) {
            throw exception(ANN_CHECK_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<AnnouncementCheckDO> getAnnouncementCheckPage(AnnouncementCheckPageReqVO pageReqVO) {
        return announcementCheckMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void performCheck(Long id) {
        // 1. 校验存在
        AnnouncementCheckDO entity = validateAnnouncementCheckExists(id);
        // 2. 状态校验：0 待检查 → 1 已检查
        validateStatus(entity, STATUS_PENDING);
        // 3. 获取关联技术公告，执行匹配规则
        AnnouncementDO announcement = announcementService.validateAnnouncementExists(entity.getAnnouncementId());
        // 4. 执行匹配规则：按公告适用设备型号与设备版本匹配影响版本范围
        String matchResult = doMatch(announcement, entity);
        entity.setMatchResult(matchResult);
        // 5. 公告类型为 EOS/EOM 时，命中即同步 EOS/EOM 状态
        if (MATCH_HIT.equals(matchResult)) {
            String annType = announcement.getAnnouncementType();
            if ("EOS".equalsIgnoreCase(annType) || "EOM".equalsIgnoreCase(annType)) {
                entity.setEomStatus(annType.toUpperCase());
            } else {
                entity.setEomStatus("NONE");
            }
        } else {
            entity.setEomStatus("NONE");
        }
        // 6. 带入公告处置建议（若检查记录未填写）
        if (StringUtils.isBlank(entity.getHandlingSuggestion())
                && StringUtils.isNotBlank(announcement.getHandlingSuggestion())) {
            entity.setHandlingSuggestion(announcement.getHandlingSuggestion());
        }
        // 7. 更新状态为已检查，记录检查时间
        entity.setStatus(STATUS_CHECKED);
        entity.setVersion(entity.getVersion() + 1);
        entity.setCheckTime(LocalDateTime.now());
        announcementCheckMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleCheck(AnnouncementCheckHandleReqVO reqVO) {
        // 1. 校验存在
        AnnouncementCheckDO entity = validateAnnouncementCheckExists(reqVO.getId());
        // 2. 状态校验：1 已检查 → 2 已处置 / 3 已忽略
        validateStatus(entity, STATUS_CHECKED);
        // 3. 乐观锁版本校验
        validateVersion(entity, reqVO.getVersion());
        // 4. 根据处置动作决定目标状态
        int newStatus;
        String action = StringUtils.defaultIfBlank(reqVO.getHandleAction(), ACTION_HANDLE);
        switch (action) {
            case ACTION_HANDLE:
                newStatus = STATUS_HANDLED;
                break;
            case ACTION_IGNORE:
                newStatus = STATUS_IGNORED;
                break;
            default:
                throw exception(ANN_CHECK_STATUS_INVALID);
        }
        // 5. 更新状态、处理意见、处理时间
        entity.setStatus(newStatus);
        entity.setVersion(entity.getVersion() + 1);
        if (reqVO.getHandleOpinion() != null) {
            entity.setHandleOpinion(reqVO.getHandleOpinion());
        }
        entity.setHandleTime(LocalDateTime.now());
        announcementCheckMapper.updateById(entity);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 执行公告与设备的匹配规则。
     * <p>
     * 匹配逻辑：
     * <ul>
     *   <li>若公告未指定适用设备型号，则视为未命中（不限制型号范围时由人工判断）；</li>
     *   <li>若公告指定型号与检查记录设备型号一致，则命中；</li>
     *   <li>若公告指定型号与检查记录设备型号不一致，则未命中；</li>
     *   <li>若检查记录未填写设备型号，则视为未知。</li>
     * </ul>
     * 影响版本范围JSON格式：["v1.0","v1.1"]，命中后可进一步校验设备版本是否在影响范围内。
     */
    private String doMatch(AnnouncementDO announcement, AnnouncementCheckDO entity) {
        String productModel = announcement.getProductModel();
        // 公告未指定型号：视为未知，由人工判断
        if (StringUtils.isBlank(productModel)) {
            return MATCH_UNKNOWN;
        }
        // 检查记录未填写设备型号：视为未知
        if (StringUtils.isBlank(entity.getDeviceModel())) {
            return MATCH_UNKNOWN;
        }
        // 型号不匹配：未命中
        if (!Objects.equals(productModel, entity.getDeviceModel())) {
            return MATCH_MISS;
        }
        // 型号匹配：若公告进一步指定了影响版本范围，校验设备版本是否在范围内
        String affectedVersions = announcement.getAffectedVersions();
        if (StringUtils.isNotBlank(affectedVersions) && StringUtils.isNotBlank(entity.getDeviceVersion())) {
            // 简化匹配：影响版本范围JSON中包含设备版本字符串即视为命中
            if (!affectedVersions.contains(entity.getDeviceVersion())) {
                return MATCH_MISS;
            }
        }
        return MATCH_HIT;
    }

    private void validateCodeUnique(String code, Long excludeId) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        AnnouncementCheckDO existing = announcementCheckMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (excludeId == null || !Objects.equals(existing.getId(), excludeId)) {
            throw exception(ANN_CHECK_CODE_DUPLICATE, code);
        }
    }

    /**
     * 校验项目存在。
     * <p>
     * 【待确认】遵循 AGENTS.md 模块边界规则暂不直接注入 ProjectMapper。
     */
    private void validateProjectExists(Long projectId) {
        // 预留扩展点
    }

    private void validateVersion(AnnouncementCheckDO entity, Integer version) {
        if (version != null && !Objects.equals(entity.getVersion(), version)) {
            throw exception(ANN_CHECK_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(AnnouncementCheckDO entity, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(entity.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(ANN_CHECK_STATUS_INVALID);
    }
}
