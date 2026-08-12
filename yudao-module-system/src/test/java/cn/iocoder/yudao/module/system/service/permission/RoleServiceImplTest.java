package cn.iocoder.yudao.module.system.service.permission;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.role.RoleSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper;
import cn.iocoder.yudao.module.system.enums.permission.RoleTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ROLE_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

/**
 * {@link RoleServiceImpl} 的单元测试类
 */
@Import(RoleServiceImpl.class)
public class RoleServiceImplTest extends BaseDbUnitTest {

    @Resource
    private RoleServiceImpl roleService;

    @Resource
    private RoleMapper roleMapper;

    @MockitoBean
    private PermissionService permissionService;

    @Test
    public void testCreateRole_success() {
        // 准备参数
        RoleSaveReqVO reqVO = randomPojo(RoleSaveReqVO.class).setId(null)
                .setStatus(CommonStatusEnum.ENABLE.getStatus());

        // 调用
        Long roleId = roleService.createRole(reqVO, null);
        // 断言
        assertNotNull(roleId);
        // 校验记录属性
        RoleDO role = roleMapper.selectById(roleId);
        assertPojoEquals(reqVO, role, "id");
        assertEquals(RoleTypeEnum.CUSTOM.getType(), role.getType());
    }

    @Test
    public void testUpdateRole_success() {
        // mock 数据
        RoleDO dbRole = randomPojo(RoleDO.class, o -> {
            o.setType(RoleTypeEnum.CUSTOM.getType());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        roleMapper.insert(dbRole);
        // 准备参数
        RoleSaveReqVO reqVO = randomPojo(RoleSaveReqVO.class, o -> {
            o.setId(dbRole.getId());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });

        // 调用
        roleService.updateRole(reqVO);
        // 校验记录属性
        RoleDO role = roleMapper.selectById(reqVO.getId());
        assertPojoEquals(reqVO, role);
    }

    @Test
    public void testUpdateRole_notExists() {
        // 准备参数
        RoleSaveReqVO reqVO = randomPojo(RoleSaveReqVO.class, o -> o.setId(randomLongId()));

        // 调用，并断言异常
        assertServiceException(() -> roleService.updateRole(reqVO), ROLE_NOT_EXISTS);
    }

    @Test
    public void testDeleteRole_success() {
        // mock 数据
        RoleDO dbRole = randomPojo(RoleDO.class, o -> {
            o.setType(RoleTypeEnum.CUSTOM.getType());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        roleMapper.insert(dbRole);
        // 准备参数
        Long id = dbRole.getId();

        // 调用
        roleService.deleteRole(id);
        // 校验数据已删除
        assertNull(roleMapper.selectById(id));
        // 校验关联清理被调用
        verify(permissionService).processRoleDeleted(id);
    }

    @Test
    public void testDeleteRole_notExists() {
        // 准备参数
        Long id = randomLongId();

        // 调用，并断言异常
        assertServiceException(() -> roleService.deleteRole(id), ROLE_NOT_EXISTS);
    }

    @Test
    public void testGetRole() {
        // mock 数据
        RoleDO dbRole = randomPojo(RoleDO.class);
        roleMapper.insert(dbRole);

        // 调用
        RoleDO role = roleService.getRole(dbRole.getId());
        // 断言
        assertPojoEquals(dbRole, role);
    }

    @Test
    public void testGetRoleList() {
        // mock 数据
        RoleDO role1 = randomPojo(RoleDO.class);
        RoleDO role2 = randomPojo(RoleDO.class);
        roleMapper.insert(role1);
        roleMapper.insert(role2);

        // 调用
        List<RoleDO> roleList = roleService.getRoleList();
        // 断言
        assertEquals(2, roleList.size());
    }

    @Test
    public void testGetRoleFromCache() {
        // mock 数据
        RoleDO dbRole = randomPojo(RoleDO.class);
        roleMapper.insert(dbRole);

        // 调用
        RoleDO role = roleService.getRoleFromCache(dbRole.getId());
        // 断言
        assertPojoEquals(dbRole, role);
    }

}
