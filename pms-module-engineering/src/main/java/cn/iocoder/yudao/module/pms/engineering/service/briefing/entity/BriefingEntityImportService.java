package cn.iocoder.yudao.module.pms.engineering.service.briefing.entity;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.entity.BriefingEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.BriefingEntityImportMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.query.BriefingEntityLockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.BRIEFING_NOT_EXISTS;

/**
 * 一次事务承接一个旧交底对象，保留主键和全部内容；包括逻辑删除记录。
 * 重试逐字段比较，不覆盖已编辑目标；任何来源变化或目标主键占用都拒绝承接。
 */
@Service
@RequiredArgsConstructor
public class BriefingEntityImportService {
    private final BriefingEntityImportMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public Long importOne(Long sourceId) {
        if (sourceId == null || sourceId <= 0) throw exception(BRIEFING_NOT_EXISTS);
        var query = new BriefingEntityLockQuery(TenantContextHolder.getRequiredTenantId(), sourceId);
        var source = mapper.selectSourceForUpdate(query);
        if (source == null) throw exception(BRIEFING_NOT_EXISTS);
        var expected = BeanUtils.toBean(source, BriefingEntityDO.class);
        expected.setLegacySourceId(sourceId);
        var existing = mapper.selectTargetForUpdate(query);
        if (existing != null) {
            if (!expected.equals(existing))
                throw new IllegalStateException("交底承接冲突：目标或来源已变化，禁止覆盖；来源ID=" + sourceId);
            return existing.getId();
        }
        if (mapper.insertImported(source) != 1)
            throw new IllegalStateException("交底承接失败；来源ID=" + sourceId);
        // 对数据库实际落地内容回读比对，禁止把写入成功等同于内容承接正确。
        if (!expected.equals(mapper.selectTargetForUpdate(query)))
            throw new IllegalStateException("交底承接回读内容不一致；来源ID=" + sourceId);
        return expected.getId();
    }
}
