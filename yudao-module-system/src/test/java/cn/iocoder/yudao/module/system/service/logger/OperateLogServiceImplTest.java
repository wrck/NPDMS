package cn.iocoder.yudao.module.system.service.logger;

import cn.iocoder.yudao.framework.common.biz.system.logger.dto.OperateLogCreateReqDTO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.logger.vo.operatelog.OperateLogPageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.logger.OperateLogDO;
import cn.iocoder.yudao.module.system.dal.mysql.logger.OperateLogMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OperateLogServiceImpl} 的单元测试类
 */
@Import(OperateLogServiceImpl.class)
public class OperateLogServiceImplTest extends BaseDbUnitTest {

    @Resource
    private OperateLogServiceImpl operateLogService;

    @Resource
    private OperateLogMapper operateLogMapper;

    @Test
    public void testCreateOperateLog() {
        // 准备参数
        OperateLogCreateReqDTO reqDTO = randomPojo(OperateLogCreateReqDTO.class);

        // 调用
        operateLogService.createOperateLog(reqDTO);
        // 断言：校验记录已插入且属性一致
        List<OperateLogDO> list = operateLogMapper.selectList(null);
        assertEquals(1, list.size());
        assertPojoEquals(reqDTO, list.get(0), "id");
    }

    @Test
    public void testGetOperateLog() {
        // mock 数据
        OperateLogDO dbLog = randomPojo(OperateLogDO.class);
        operateLogMapper.insert(dbLog);

        // 调用
        OperateLogDO log = operateLogService.getOperateLog(dbLog.getId());
        // 断言
        assertNotNull(log);
        assertPojoEquals(dbLog, log);
    }

    @Test
    public void testGetOperateLogPage() {
        // mock 数据
        OperateLogDO log1 = randomPojo(OperateLogDO.class, o -> {
            o.setType("订单");
            o.setSubType("创建订单");
            o.setUserId(1L);
            o.setBizId(100L);
        });
        OperateLogDO log2 = randomPojo(OperateLogDO.class, o -> {
            o.setType("用户");
            o.setSubType("创建用户");
            o.setUserId(2L);
            o.setBizId(200L);
        });
        operateLogMapper.insert(log1);
        operateLogMapper.insert(log2);
        // 准备参数：按 type 模糊过滤
        OperateLogPageReqVO reqVO = new OperateLogPageReqVO();
        reqVO.setType("订单");

        // 调用
        PageResult<OperateLogDO> pageResult = operateLogService.getOperateLogPage(reqVO);
        // 断言：仅查询到 type 包含 "订单" 的记录
        assertEquals(1, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        assertPojoEquals(log1, pageResult.getList().get(0));
    }

    @Test
    public void testGetOperateLogPage_byUserId() {
        // mock 数据
        OperateLogDO log1 = randomPojo(OperateLogDO.class, o -> {
            o.setType("订单");
            o.setUserId(1L);
            o.setBizId(100L);
        });
        OperateLogDO log2 = randomPojo(OperateLogDO.class, o -> {
            o.setType("订单");
            o.setUserId(2L);
            o.setBizId(200L);
        });
        operateLogMapper.insert(log1);
        operateLogMapper.insert(log2);
        // 准备参数：按 userId 精确过滤
        OperateLogPageReqVO reqVO = new OperateLogPageReqVO();
        reqVO.setUserId(1L);

        // 调用
        PageResult<OperateLogDO> pageResult = operateLogService.getOperateLogPage(reqVO);
        // 断言：仅查询到 userId=1 的记录
        assertEquals(1, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        assertPojoEquals(log1, pageResult.getList().get(0));
    }

}
