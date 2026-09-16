package com.dp.plat.lowcode.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 低代码页面视图对象。
 *
 * <p>用于 {@code /api/lowcode/permission/pages} 接口返回当前用户可访问的低代码页面列表。
 * 数据来源于 {@code sys_menu} 表中 {@code menu_type = 'L'} 的菜单记录，
 * 其中 {@code path} 形如 {@code /lowcode/{pageType}/{pageCode}}，
 * {@code permission} 对应 {@code sys_menu.perms} 字段。</p>
 */
@Data
@Builder
public class LowCodePageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 菜单 ID（sys_menu.id） */
    private Long menuId;

    /** 菜单名称（sys_menu.menu_name） */
    private String menuName;

    /** 页面类型：form / list / tab / related-page */
    private String pageType;

    /** 低代码配置编码 */
    private String pageCode;

    /** 权限标识（sys_menu.perms） */
    private String permission;

    /** 路由路径（sys_menu.path，如 /lowcode/form/myCode） */
    private String path;

    /** 图标（sys_menu.icon） */
    private String icon;

    /** 排序号（sys_menu.order_num） */
    private Integer sortOrder;
}
