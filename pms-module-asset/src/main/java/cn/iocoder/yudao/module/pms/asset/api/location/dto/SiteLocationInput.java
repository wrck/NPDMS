package cn.iocoder.yudao.module.pms.asset.api.location.dto;

public record SiteLocationInput(
        Long id,
        Integer expectedVersion,
        Long parentId,
        String code,
        String name,
        String locationType,
        Integer treeSort) {
}
