package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.lowcode.engine.connector.DynamicDataSourceManager;
import com.dp.plat.lowcode.entity.LowCodeDataSource;
import com.dp.plat.lowcode.mapper.LowCodeDataSourceMapper;
import com.dp.plat.lowcode.service.LowCodeDataSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

/**
 * 多数据源配置服务实现（批次3-T7）。
 *
 * <p>管理数据源配置（持久化到 pms_lowcode_datasource 表），
 * 并在激活/停用时同步到 {@link DynamicDataSourceManager}（运行时数据源缓存）。</p>
 *
 * <p>三种集成模式的处理：
 * <ul>
 *   <li>DIRECT：激活时注册到 DynamicDataSourceManager，停用时注销</li>
 *   <li>REPLICA：激活时注册数据源 + 触发表同步（同步逻辑由调用方实现）</li>
 *   <li>FEDERATED：激活时注册数据源，中间库由数据库层配置</li>
 * </ul></p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowCodeDataSourceServiceImpl
        extends ServiceImpl<LowCodeDataSourceMapper, LowCodeDataSource>
        implements LowCodeDataSourceService {

    private final DynamicDataSourceManager dynamicDataSourceManager;

    @Override
    public Map<String, Object> testConnection(LowCodeDataSource ds) {
        Map<String, Object> result = new HashMap<>();
        Connection conn = null;
        try {
            Class.forName(ds.getDriverClassName());
            conn = DriverManager.getConnection(ds.getUrl(), ds.getUsername(), ds.getPassword());
            if (conn != null && conn.isValid(5)) {
                result.put("success", true);
                result.put("message", "连接成功");
                result.put("databaseProductName", conn.getMetaData().getDatabaseProductName());
                result.put("databaseProductVersion", conn.getMetaData().getDatabaseProductVersion());
            } else {
                result.put("success", false);
                result.put("message", "连接成功但连接无效");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
            log.warn("数据源连接测试失败: code={}, url={}", ds.getCode(), ds.getUrl(), e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }
        return result;
    }

    @Override
    public void activate(String code) {
        LowCodeDataSource ds = lambdaQuery()
                .eq(LowCodeDataSource::getCode, code)
                .one();
        if (ds == null) {
            throw new IllegalArgumentException("数据源不存在: " + code);
        }
        if (!"ACTIVE".equals(ds.getStatus())) {
            ds.setStatus("ACTIVE");
            updateById(ds);
        }
        // 注册到运行时数据源管理器
        dynamicDataSourceManager.register(
                ds.getCode(),
                ds.getUrl(),
                ds.getUsername(),
                ds.getPassword(),
                ds.getDriverClassName());
        log.info("数据源 {} 已激活（模式={}）", code, ds.getIntegrationMode());
    }

    @Override
    public void deactivate(String code) {
        LowCodeDataSource ds = lambdaQuery()
                .eq(LowCodeDataSource::getCode, code)
                .one();
        if (ds != null) {
            ds.setStatus("INACTIVE");
            updateById(ds);
        }
        // 从运行时数据源管理器注销
        dynamicDataSourceManager.unregister(code);
        log.info("数据源 {} 已停用", code);
    }

    @Override
    public Connection getConnection(String code) {
        DataSource ds = dynamicDataSourceManager.getDataSource(code);
        if (ds == null) {
            // 尝试自动激活
            activate(code);
            ds = dynamicDataSourceManager.getDataSource(code);
        }
        if (ds == null) {
            throw new IllegalStateException("数据源未激活或注册失败: " + code);
        }
        try {
            return ds.getConnection();
        } catch (Exception e) {
            throw new RuntimeException("获取数据源连接失败: " + code, e);
        }
    }
}
