package cn.iocoder.yudao.module.pms.asset.service.location;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteLocationInput;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteLocationDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.SiteLocationMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class SiteLocationTreeServiceImpl implements SiteLocationTreeService {

    private static final String ROOT_PATH = "/";

    private final SiteLocationMapper siteLocationMapper;
    private final EquipmentMapper equipmentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SiteLocationDO maintain(Long siteId, SiteLocationInput input) {
        if (siteId == null || input == null) {
            throw exception(AST_LOCATION_REFERENCE_INVALID);
        }
        if (input.id() == null) {
            validateCodeUnique(siteId, null, input.code());
            SiteLocationDO parent = validateParent(siteId, null, input.parentId());
            SiteLocationDO entity = buildEntity(siteId, input, parent);
            entity.setStatus(CommonStatusEnum.ENABLE.getStatus());
            entity.setVersion(0);
            siteLocationMapper.insert(entity);
            return entity;
        }

        SiteLocationDO existing = get(input.id(), input.expectedVersion());
        if (!Objects.equals(existing.getSiteId(), siteId)) {
            throw exception(AST_SITE_LOCATION_CROSS_SITE);
        }
        if (isReferenceOnly(input)) {
            return existing;
        }
        validateCodeUnique(siteId, existing.getId(), input.code());
        SiteLocationDO parent = validateParent(siteId, existing.getId(), input.parentId());
        validateNoCycle(existing, parent);

        String oldPath = existing.getTreePath();
        Integer oldDepth = existing.getTreeDepth();
        SiteLocationDO update = buildEntity(siteId, input, parent);
        update.setId(existing.getId());
        update.setVersion(existing.getVersion() + 1);
        if (siteLocationMapper.updateByIdAndVersion(update, existing.getVersion()) == 0) {
            throw exception(AST_LOCATION_VERSION_CONFLICT);
        }
        updateDescendantPaths(existing, update, oldPath, oldDepth);
        update.setStatus(existing.getStatus());
        return update;
    }

    @Override
    public SiteLocationDO get(Long locationId, Integer expectedVersion) {
        SiteLocationDO entity = siteLocationMapper.selectById(locationId);
        if (entity == null) {
            throw exception(AST_SITE_LOCATION_NOT_EXISTS);
        }
        if (expectedVersion != null && !Objects.equals(entity.getVersion(), expectedVersion)) {
            throw exception(AST_LOCATION_VERSION_CONFLICT);
        }
        return entity;
    }

    @Override
    public List<SiteLocationDO> getTree(Long siteId) {
        return siteLocationMapper.selectListBySiteId(siteId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long locationId, Integer expectedVersion) {
        SiteLocationDO entity = get(locationId, expectedVersion);
        String descendantPrefix = entity.getTreePath() + entity.getId() + "/";
        boolean hasActiveChildren = getTree(entity.getSiteId()).stream()
                .anyMatch(item -> !Objects.equals(item.getId(), entity.getId())
                        && item.getTreePath().startsWith(descendantPrefix)
                        && CommonStatusEnum.isEnable(item.getStatus()));
        if (hasActiveChildren) {
            throw exception(AST_SITE_LOCATION_HAS_ACTIVE_CHILDREN);
        }
        if (equipmentMapper.selectCountBySiteLocationId(locationId) > 0) {
            throw exception(AST_SITE_LOCATION_IN_USE);
        }
        SiteLocationDO update = new SiteLocationDO();
        update.setId(entity.getId());
        update.setStatus(CommonStatusEnum.DISABLE.getStatus());
        update.setVersion(entity.getVersion() + 1);
        if (siteLocationMapper.updateByIdAndVersion(update, entity.getVersion()) == 0) {
            throw exception(AST_LOCATION_VERSION_CONFLICT);
        }
    }

    private SiteLocationDO validateParent(Long siteId, Long currentId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        if (Objects.equals(currentId, parentId)) {
            throw exception(AST_SITE_LOCATION_CYCLE);
        }
        SiteLocationDO parent = get(parentId, null);
        if (!Objects.equals(parent.getSiteId(), siteId)) {
            throw exception(AST_SITE_LOCATION_CROSS_SITE);
        }
        return parent;
    }

    private void validateNoCycle(SiteLocationDO existing, SiteLocationDO parent) {
        if (parent == null) {
            return;
        }
        String descendantPrefix = existing.getTreePath() + existing.getId() + "/";
        if (parent.getTreePath().startsWith(descendantPrefix)) {
            throw exception(AST_SITE_LOCATION_CYCLE);
        }
    }

    private boolean isReferenceOnly(SiteLocationInput input) {
        return input.parentId() == null && input.code() == null && input.name() == null
                && input.locationType() == null && input.treeSort() == null;
    }

    private void validateCodeUnique(Long siteId, Long currentId, String code) {
        SiteLocationDO duplicate = siteLocationMapper.selectBySiteIdAndCode(siteId, code);
        if (duplicate != null && !Objects.equals(duplicate.getId(), currentId)) {
            throw exception(AST_SITE_LOCATION_CODE_DUPLICATE);
        }
    }

    private SiteLocationDO buildEntity(Long siteId, SiteLocationInput input, SiteLocationDO parent) {
        SiteLocationDO entity = new SiteLocationDO();
        entity.setSiteId(siteId);
        entity.setParentId(input.parentId());
        entity.setCode(input.code());
        entity.setName(input.name());
        entity.setLocationType(input.locationType());
        entity.setTreePath(parent == null ? ROOT_PATH : parent.getTreePath() + parent.getId() + "/");
        entity.setTreeDepth(parent == null ? 0 : parent.getTreeDepth() + 1);
        entity.setTreeSort(input.treeSort() == null ? 0 : input.treeSort());
        return entity;
    }

    private void updateDescendantPaths(SiteLocationDO oldNode, SiteLocationDO newNode,
                                       String oldPath, Integer oldDepth) {
        String oldPrefix = oldPath + oldNode.getId() + "/";
        String newPrefix = newNode.getTreePath() + newNode.getId() + "/";
        int depthDelta = newNode.getTreeDepth() - oldDepth;
        for (SiteLocationDO descendant : getTree(oldNode.getSiteId())) {
            if (!descendant.getTreePath().startsWith(oldPrefix)) {
                continue;
            }
            SiteLocationDO update = new SiteLocationDO();
            update.setId(descendant.getId());
            update.setTreePath(newPrefix + descendant.getTreePath().substring(oldPrefix.length()));
            update.setTreeDepth(descendant.getTreeDepth() + depthDelta);
            update.setVersion(descendant.getVersion() + 1);
            if (siteLocationMapper.updateByIdAndVersion(update, descendant.getVersion()) == 0) {
                throw exception(AST_LOCATION_VERSION_CONFLICT);
            }
        }
    }

}
