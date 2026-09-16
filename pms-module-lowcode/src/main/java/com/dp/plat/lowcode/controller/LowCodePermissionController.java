package com.dp.plat.lowcode.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.constant.CommonConstants;
import com.dp.plat.common.result.Result;
import com.dp.plat.common.util.SecurityUtils;
import com.dp.plat.lowcode.dto.CreateLowCodeMenuRequest;
import com.dp.plat.lowcode.dto.LowCodePageVO;
import com.dp.plat.lowcode.entity.LowCodeForm;
import com.dp.plat.lowcode.entity.LowCodeList;
import com.dp.plat.lowcode.entity.LowCodeRelatedPage;
import com.dp.plat.lowcode.entity.LowCodeTab;
import com.dp.plat.lowcode.service.LowCodeFormService;
import com.dp.plat.lowcode.service.LowCodeListService;
import com.dp.plat.lowcode.service.LowCodeRelatedPageService;
import com.dp.plat.lowcode.service.LowCodeTabService;
import com.dp.plat.system.entity.SysMenu;
import com.dp.plat.system.service.ISysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 低代码页面权限校验 Controller。
 *
 * <p>提供低代码页面的访问权限校验、可访问页面列表查询，以及为低代码页面创建菜单
 * （sys_menu 中 menu_type = 'L' 的记录）的能力。</p>
 *
 * <p>权限模型：</p>
 * <ul>
 *   <li>低代码菜单在 sys_menu 中以 {@code menu_type = 'L'} 标识，{@code path} 字段
 *       统一为 {@code /lowcode/{pageType}/{pageCode}}</li>
 *   <li>{@code sys_menu.perms} 存储该低代码页面的权限标识，形如
 *       {@code lowcode:page:{pageType}:{pageCode}} 或自定义值</li>
 *   <li>校验逻辑：超级管理员（admin 角色）直接放行；否则按 sys_menu.perms 校验
 *       当前用户是否持有该权限；菜单未配置 perms 时视为公开页面放行；
 *       未注册菜单的低代码页面拒绝访问（安全默认）</li>
 * </ul>
 */
@Slf4j
@Tag(name = "低代码页面权限", description = "LowCode page permission APIs")
@RestController
@RequestMapping("/api/lowcode/permission")
@RequiredArgsConstructor
public class LowCodePermissionController {

    /** sys_menu.menu_type 低代码页面类型标识 */
    private static final String MENU_TYPE_LOWCODE = "L";

    /** 低代码路由前缀 */
    private static final String LOWCODE_PATH_PREFIX = "/lowcode/";

    /** 支持的页面类型 */
    private static final Set<String> SUPPORTED_PAGE_TYPES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("form", "list", "tab", "related-page")));

    private final ISysMenuService sysMenuService;
    private final LowCodeFormService lowCodeFormService;
    private final LowCodeListService lowCodeListService;
    private final LowCodeTabService lowCodeTabService;
    private final LowCodeRelatedPageService lowCodeRelatedPageService;

    /**
     * 校验当前用户是否有权访问指定低代码页面。
     *
     * @param pageType 页面类型 form/list/tab/related-page
     * @param pageCode 低代码配置编码
     * @return true=有权限，false=无权限或页面未注册
     */
    @Operation(summary = "校验低代码页面访问权限")
    @GetMapping("/check")
    public Result<Boolean> checkPermission(
            @RequestParam String pageType,
            @RequestParam String pageCode) {
        if (!StringUtils.hasText(pageType) || !StringUtils.hasText(pageCode)
                || !SUPPORTED_PAGE_TYPES.contains(pageType)) {
            return Result.ok(false);
        }
        // 1. 按 path 查找低代码菜单
        String path = buildPath(pageType, pageCode);
        SysMenu menu = findLowCodeMenuByPath(path);
        if (menu == null) {
            // 未注册菜单的低代码页面，安全默认拒绝
            return Result.ok(false);
        }
        // 2. 超级管理员放行
        if (isSuperAdmin()) {
            return Result.ok(true);
        }
        // 3. 菜单未配置权限标识视为公开页面
        if (!StringUtils.hasText(menu.getPerms())) {
            return Result.ok(true);
        }
        // 4. 校验当前用户是否持有该权限
        return Result.ok(hasAuthority(menu.getPerms()));
    }

    /**
     * 获取当前用户可访问的低代码页面列表。
     *
     * <p>查询当前用户授权的菜单（已按 user-role-menu 关联过滤），筛选 menu_type='L'
     * 的记录，解析 path 提取 pageType / pageCode 后返回。</p>
     *
     * @return 低代码页面列表
     */
    @Operation(summary = "获取当前用户可访问的低代码页面列表")
    @GetMapping("/pages")
    public Result<List<LowCodePageVO>> getAccessiblePages() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.ok(Collections.emptyList());
        }
        List<SysMenu> menus;
        if (isSuperAdmin()) {
            // 超级管理员：查询全部低代码菜单
            menus = sysMenuService.list(new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getMenuType, MENU_TYPE_LOWCODE)
                    .orderByAsc(SysMenu::getOrderNum));
        } else {
            // 普通用户：仅返回授权菜单中的低代码页面
            List<SysMenu> userMenus = sysMenuService.listMenusByUserId(userId);
            menus = new ArrayList<>();
            for (SysMenu m : userMenus) {
                if (MENU_TYPE_LOWCODE.equals(m.getMenuType())) {
                    menus.add(m);
                }
            }
        }
        List<LowCodePageVO> pages = new ArrayList<>(menus.size());
        for (SysMenu m : menus) {
            String[] parsed = parsePath(m.getPath());
            if (parsed == null) {
                continue;
            }
            pages.add(LowCodePageVO.builder()
                    .menuId(m.getId())
                    .menuName(m.getMenuName())
                    .pageType(parsed[0])
                    .pageCode(parsed[1])
                    .permission(m.getPerms())
                    .path(m.getPath())
                    .icon(m.getIcon())
                    .sortOrder(m.getOrderNum())
                    .build());
        }
        return Result.ok(pages);
    }

    /**
     * 为低代码页面创建菜单。
     *
     * <p>在 sys_menu 中创建 menu_type='L' 的记录，path 自动生成为
     * {@code /lowcode/{pageType}/{pageCode}}，permission 未提供时按
     * {@code lowcode:page:{pageType}:{pageCode}} 生成。需保证 path 与 permission 唯一。</p>
     *
     * @param request 创建请求
     * @return 新建菜单 ID
     */
    @Operation(summary = "为低代码页面创建菜单")
    @PostMapping("/menu")
    @PreAuthorize("@ss.hasPermission('lowcode:menu:create')")
    @OperLog(title = "低代码菜单管理", businessType = 1)
    public Result<Long> createMenu(@RequestBody @Valid CreateLowCodeMenuRequest request) {
        // 1. 校验低代码配置存在且已发布
        if (!configExists(request.getPageType(), request.getPageCode())) {
            return Result.fail("低代码配置不存在或未发布：" + request.getPageType() + "/" + request.getPageCode());
        }
        // 2. 构建 path 与 permission
        String path = buildPath(request.getPageType(), request.getPageCode());
        String permission = StringUtils.hasText(request.getPermission())
                ? request.getPermission()
                : buildDefaultPermission(request.getPageType(), request.getPageCode());
        // 3. 校验 path 唯一
        long pathExists = sysMenuService.count(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPath, path)
                .eq(SysMenu::getMenuType, MENU_TYPE_LOWCODE));
        if (pathExists > 0) {
            return Result.fail("低代码菜单已存在：" + path);
        }
        // 4. 校验 permission 唯一
        long permExists = sysMenuService.count(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPerms, permission));
        if (permExists > 0) {
            return Result.fail("权限标识已存在：" + permission);
        }
        // 5. 创建菜单
        SysMenu menu = SysMenu.builder()
                .parentId(request.getParentId() == null ? 0L : request.getParentId())
                .menuName(request.getMenuName())
                .menuType(MENU_TYPE_LOWCODE)
                .path(path)
                .component(null)
                .perms(permission)
                .icon(StringUtils.hasText(request.getIcon()) ? request.getIcon() : "")
                .orderNum(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .visible("0")
                .build();
        boolean ok = sysMenuService.save(menu);
        if (!ok || menu.getId() == null) {
            return Result.fail("创建低代码菜单失败");
        }
        return Result.ok(menu.getId());
    }

    // ===================== 内部工具方法 =====================

    /** 构建低代码路由 path：/lowcode/{pageType}/{pageCode} */
    private String buildPath(String pageType, String pageCode) {
        return LOWCODE_PATH_PREFIX + pageType + "/" + pageCode;
    }

    /** 构建默认权限标识：lowcode:page:{pageType}:{pageCode} */
    private String buildDefaultPermission(String pageType, String pageCode) {
        return "lowcode:page:" + pageType + ":" + pageCode;
    }

    /** 按 path 查找低代码菜单 */
    private SysMenu findLowCodeMenuByPath(String path) {
        return sysMenuService.getOne(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPath, path)
                .eq(SysMenu::getMenuType, MENU_TYPE_LOWCODE)
                .last("LIMIT 1"));
    }

    /** 解析 path（/lowcode/{pageType}/{pageCode}），返回 [pageType, pageCode] 或 null */
    private String[] parsePath(String path) {
        if (!StringUtils.hasText(path) || !path.startsWith(LOWCODE_PATH_PREFIX)) {
            return null;
        }
        String rest = path.substring(LOWCODE_PATH_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash >= rest.length() - 1) {
            return null;
        }
        String pageType = rest.substring(0, slash);
        String pageCode = rest.substring(slash + 1);
        if (!SUPPORTED_PAGE_TYPES.contains(pageType)) {
            return null;
        }
        return new String[]{pageType, pageCode};
    }

    /** 校验指定低代码配置是否存在且已发布 */
    private boolean configExists(String pageType, String pageCode) {
        switch (pageType) {
            case "form": {
                LowCodeForm cfg = lowCodeFormService.getByCode(pageCode);
                return cfg != null;
            }
            case "list": {
                LowCodeList cfg = lowCodeListService.getByCode(pageCode);
                return cfg != null;
            }
            case "tab": {
                LowCodeTab cfg = lowCodeTabService.getByCode(pageCode);
                return cfg != null;
            }
            case "related-page": {
                LowCodeRelatedPage cfg = lowCodeRelatedPageService.getByCode(pageCode);
                return cfg != null;
            }
            default:
                return false;
        }
    }

    /** 判断当前用户是否为超级管理员（持有 admin 角色权限） */
    private boolean isSuperAdmin() {
        Authentication auth = SecurityUtils.getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (CommonConstants.SUPER_ADMIN_ROLE.equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /** 判断当前用户是否持有指定权限标识 */
    private boolean hasAuthority(String perm) {
        Authentication auth = SecurityUtils.getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (perm.equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
