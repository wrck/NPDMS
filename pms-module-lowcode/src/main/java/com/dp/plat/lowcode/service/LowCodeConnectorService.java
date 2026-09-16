package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.engine.connector.ConnectorResult;
import com.dp.plat.lowcode.entity.LowCodeConnector;

import java.util.Map;

/**
 * 低代码连接器服务。
 */
public interface LowCodeConnectorService extends IService<LowCodeConnector> {

    /**
     * 执行连接器。
     *
     * @param code   连接器编码
     * @param params 输入参数
     * @return 执行结果
     */
    ConnectorResult execute(String code, Map<String, Object> params);

    /**
     * 测试连接。
     *
     * @param code 连接器编码
     * @return 测试结果
     */
    ConnectorResult test(String code);

    /**
     * 测试单个操作（按操作名执行已保存连接器的指定操作）。
     *
     * <p>用于连接器设计器中的实时测试控制台：用户选择某个操作并输入参数，
     * 后端从已保存的连接器配置中找到该操作并执行。</p>
     *
     * @param code         连接器编码
     * @param operationName 操作名
     * @param params       执行参数
     * @return 执行结果
     */
    ConnectorResult testOperation(String code, String operationName, Map<String, Object> params);
}
