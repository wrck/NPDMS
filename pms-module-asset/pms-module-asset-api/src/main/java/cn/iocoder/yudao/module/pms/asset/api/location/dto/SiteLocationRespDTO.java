package cn.iocoder.yudao.module.pms.asset.api.location.dto;

public record SiteLocationRespDTO(
        Long id,
        Long siteId,
        Long parentId,
        String code,
        String name,
        String locationType,
        String treePath,
        Integer treeDepth,
        Integer treeSort,
        Integer status,
        Integer version) {
}
