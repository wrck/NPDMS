package cn.iocoder.yudao.module.system.service.permission;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.menu.MenuListReqVO;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.menu.MenuSaveVO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.MenuMapper;
import cn.iocoder.yudao.module.system.enums.permission.MenuTypeEnum;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MENU_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

/**
 * {@link MenuServiceImpl} 的单元测试类
 */
@Import(MenuServiceImpl.class)
public class MenuServiceImplTest extends BaseDbUnitTest {

    @Resource
    private MenuServiceImpl menuService;

    @Resource
    private MenuMapper menuMapper;

    @MockitoBean
    private PermissionService permissionService;

    @MockitoBean
    private TenantService tenantService;

    @Test
    public void testCreateMenu_success() {
        // 准备参数：parentId 为根节点
        MenuSaveVO reqVO = randomPojo(MenuSaveVO.class).setId(null)
                .setParentId(MenuDO.ID_ROOT)
                .setType(MenuTypeEnum.MENU.getType())
                .setStatus(CommonStatusEnum.ENABLE.getStatus());

        // 调用
        Long menuId = menuService.createMenu(reqVO);
        // 断言
        assertNotNull(menuId);
        // 校验记录属性
        MenuDO menu = menuMapper.selectById(menuId);
        assertPojoEquals(reqVO, menu, "id");
    }

    @Test
    public void testUpdateMenu_success() {
        // mock 数据
        MenuDO dbMenu = randomPojo(MenuDO.class, o -> {
            o.setParentId(MenuDO.ID_ROOT);
            o.setType(MenuTypeEnum.MENU.getType());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        menuMapper.insert(dbMenu);
        // 准备参数
        MenuSaveVO reqVO = randomPojo(MenuSaveVO.class, o -> {
            o.setId(dbMenu.getId());
            o.setParentId(MenuDO.ID_ROOT);
            o.setType(MenuTypeEnum.MENU.getType());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });

        // 调用
        menuService.updateMenu(reqVO);
        // 校验记录属性
        MenuDO menu = menuMapper.selectById(reqVO.getId());
        assertPojoEquals(reqVO, menu);
    }

    @Test
    public void testUpdateMenu_notExists() {
        // 准备参数
        MenuSaveVO reqVO = randomPojo(MenuSaveVO.class, o -> {
            o.setId(randomLongId());
            o.setParentId(MenuDO.ID_ROOT);
        });

        // 调用，并断言异常
        assertServiceException(() -> menuService.updateMenu(reqVO), MENU_NOT_EXISTS);
    }

    @Test
    public void testDeleteMenu_success() {
        // mock 数据：无子菜单
        MenuDO dbMenu = randomPojo(MenuDO.class, o -> {
            o.setParentId(MenuDO.ID_ROOT);
            o.setType(MenuTypeEnum.MENU.getType());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        menuMapper.insert(dbMenu);
        Long id = dbMenu.getId();

        // 调用
        menuService.deleteMenu(id);
        // 校验数据已删除
        assertNull(menuMapper.selectById(id));
        // 校验关联清理被调用
        verify(permissionService).processMenuDeleted(id);
    }

    @Test
    public void testDeleteMenu_notExists() {
        // 准备参数
        Long id = randomLongId();

        // 调用，并断言异常
        assertServiceException(() -> menuService.deleteMenu(id), MENU_NOT_EXISTS);
    }

    @Test
    public void testGetMenuList() {
        // mock 数据
        MenuDO menu1 = randomPojo(MenuDO.class, o -> o.setStatus(CommonStatusEnum.ENABLE.getStatus()));
        MenuDO menu2 = randomPojo(MenuDO.class, o -> o.setStatus(CommonStatusEnum.ENABLE.getStatus()));
        menuMapper.insert(menu1);
        menuMapper.insert(menu2);

        // 调用
        List<MenuDO> menuList = menuService.getMenuList();
        // 断言
        assertEquals(2, menuList.size());
    }

    @Test
    public void testGetMenuList_withFilter() {
        // mock 数据
        MenuDO menu1 = randomPojo(MenuDO.class, o -> {
            o.setName("NPMS");
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        MenuDO menu2 = randomPojo(MenuDO.class, o -> {
            o.setName("测试菜单");
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        menuMapper.insert(menu1);
        menuMapper.insert(menu2);
        // 准备参数：按名称过滤
        MenuListReqVO reqVO = new MenuListReqVO();
        reqVO.setName("NPMS");

        // 调用
        List<MenuDO> menuList = menuService.getMenuList(reqVO);
        // 断言
        assertEquals(1, menuList.size());
        assertPojoEquals(menu1, menuList.get(0));
    }

}
