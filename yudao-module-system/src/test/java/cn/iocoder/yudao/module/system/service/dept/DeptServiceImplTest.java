package cn.iocoder.yudao.module.system.service.dept;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.DEPT_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DeptServiceImpl} 的单元测试类
 */
@Import(DeptServiceImpl.class)
public class DeptServiceImplTest extends BaseDbUnitTest {

    @Resource
    private DeptServiceImpl deptService;

    @Resource
    private DeptMapper deptMapper;

    @Test
    public void testCreateDept_success() {
        // 准备参数：parentId 为根节点
        DeptSaveReqVO reqVO = randomPojo(DeptSaveReqVO.class).setId(null)
                .setParentId(DeptDO.PARENT_ID_ROOT)
                .setStatus(CommonStatusEnum.ENABLE.getStatus());

        // 调用
        Long deptId = deptService.createDept(reqVO);
        // 断言
        assertNotNull(deptId);
        // 校验记录属性
        DeptDO dept = deptMapper.selectById(deptId);
        assertPojoEquals(reqVO, dept, "id");
    }

    @Test
    public void testUpdateDept_success() {
        // mock 数据
        DeptDO dbDept = randomPojo(DeptDO.class, o -> {
            o.setParentId(DeptDO.PARENT_ID_ROOT);
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        deptMapper.insert(dbDept);
        // 准备参数
        DeptSaveReqVO reqVO = randomPojo(DeptSaveReqVO.class, o -> {
            o.setId(dbDept.getId());
            o.setParentId(DeptDO.PARENT_ID_ROOT);
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });

        // 调用
        deptService.updateDept(reqVO);
        // 校验记录属性
        DeptDO dept = deptMapper.selectById(reqVO.getId());
        assertPojoEquals(reqVO, dept);
    }

    @Test
    public void testUpdateDept_notExists() {
        // 准备参数
        DeptSaveReqVO reqVO = randomPojo(DeptSaveReqVO.class, o -> {
            o.setId(randomLongId());
            o.setParentId(DeptDO.PARENT_ID_ROOT);
        });

        // 调用，并断言异常
        assertServiceException(() -> deptService.updateDept(reqVO), DEPT_NOT_FOUND);
    }

    @Test
    public void testDeleteDept_success() {
        // mock 数据：无子部门
        DeptDO dbDept = randomPojo(DeptDO.class, o -> {
            o.setParentId(DeptDO.PARENT_ID_ROOT);
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        deptMapper.insert(dbDept);
        Long id = dbDept.getId();

        // 调用
        deptService.deleteDept(id);
        // 校验数据已删除
        assertNull(deptMapper.selectById(id));
    }

    @Test
    public void testDeleteDept_notExists() {
        // 准备参数
        Long id = randomLongId();

        // 调用，并断言异常
        assertServiceException(() -> deptService.deleteDept(id), DEPT_NOT_FOUND);
    }

    @Test
    public void testGetDeptList() {
        // mock 数据
        DeptDO dept1 = randomPojo(DeptDO.class, o -> {
            o.setParentId(DeptDO.PARENT_ID_ROOT);
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        DeptDO dept2 = randomPojo(DeptDO.class, o -> {
            o.setParentId(DeptDO.PARENT_ID_ROOT);
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        deptMapper.insert(dept1);
        deptMapper.insert(dept2);

        // 调用
        DeptListReqVO reqVO = new DeptListReqVO();
        List<DeptDO> deptList = deptService.getDeptList(reqVO);
        // 断言
        assertEquals(2, deptList.size());
    }

    @Test
    public void testGetDeptList_withFilter() {
        // mock 数据
        DeptDO dept1 = randomPojo(DeptDO.class, o -> {
            o.setName("NPMS");
            o.setParentId(DeptDO.PARENT_ID_ROOT);
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        DeptDO dept2 = randomPojo(DeptDO.class, o -> {
            o.setName("测试部门");
            o.setParentId(DeptDO.PARENT_ID_ROOT);
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        deptMapper.insert(dept1);
        deptMapper.insert(dept2);
        // 准备参数：按名称过滤
        DeptListReqVO reqVO = new DeptListReqVO();
        reqVO.setName("NPMS");

        // 调用
        List<DeptDO> deptList = deptService.getDeptList(reqVO);
        // 断言
        assertEquals(1, deptList.size());
        assertPojoEquals(dept1, deptList.get(0));
    }

    @Test
    public void testGetDeptByCode() {
        DeptDO dept = randomPojo(DeptDO.class, o -> {
            o.setCode("DEPT-HZ-01");
            o.setParentId(DeptDO.PARENT_ID_ROOT);
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
            o.setVersion(0);
        });
        deptMapper.insert(dept);

        DeptDO result = deptService.getDeptByCode("DEPT-HZ-01");

        assertEquals(dept.getId(), result.getId());
        assertEquals("DEPT-HZ-01", result.getCode());
        assertEquals(0, result.getVersion());
    }

    @Test
    public void testGetChildDeptList() {
        // mock 数据：父部门
        DeptDO parentDept = randomPojo(DeptDO.class, o -> {
            o.setParentId(DeptDO.PARENT_ID_ROOT);
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        deptMapper.insert(parentDept);
        // mock 数据：子部门
        DeptDO childDept = randomPojo(DeptDO.class, o -> {
            o.setParentId(parentDept.getId());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        deptMapper.insert(childDept);
        // mock 数据：孙部门
        DeptDO grandChildDept = randomPojo(DeptDO.class, o -> {
            o.setParentId(childDept.getId());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        deptMapper.insert(grandChildDept);

        // 调用
        List<DeptDO> childDeptList = deptService.getChildDeptList(parentDept.getId());
        // 断言：应包含子部门和孙部门
        assertEquals(2, childDeptList.size());
    }

}
