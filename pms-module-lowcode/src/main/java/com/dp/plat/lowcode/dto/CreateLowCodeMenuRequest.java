package com.dp.plat.lowcode.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建低代码菜单请求。
 *
 * <p>用于 {@code POST /api/lowcode/permission/menu} 接口，在 {@code sys_menu} 表中
 * 创建一条 {@code menu_type = 'L'} 的低代码菜单记录。{@code path} 由后端按
 * {@code /lowcode/{pageType}/{pageCode}} 自动生成，调用方无需提供。</p>
 */
@Data
public class CreateLowCodeMenuRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 菜单名称（sys_menu.menu_name） */
    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称长度不能超过 50 个字符")
    private String menuName;

    /** 页面类型：form / list / tab / related-page */
    @NotBlank(message = "页面类型不能为空")
    @Pattern(regexp = "^(form|list|tab|related-page)$",
            message = "页面类型必须为 form / list / tab / related-page 之一")
    private String pageType;

    /** 低代码配置编码 */
    @NotBlank(message = "页面编码不能为空")
    @Size(max = 64, message = "页面编码长度不能超过 64 个字符")
    private String pageCode;

    /**
     * 自定义权限标识。留空时后端按 {@code lowcode:page:{pageType}:{pageCode}} 自动生成。
     * 需保证在 sys_menu.perms 中唯一。
     */
    @Size(max = 100, message = "权限标识长度不能超过 100 个字符")
    private String permission;

    /** 上级菜单 ID（sys_menu.parent_id，0 表示顶级） */
    private Long parentId;

    /** 图标（sys_menu.icon） */
    @Size(max = 100, message = "图标长度不能超过 100 个字符")
    private String icon;

    /** 排序号（sys_menu.order_num） */
    private Integer sortOrder;
}
