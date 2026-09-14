package cn.iocoder.yudao.module.infra.api.db;

import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DataSourceConfigSaveReqVO;
import cn.iocoder.yudao.module.infra.service.db.DataSourceConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.List;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class ExternalDataSourceApiImpl implements ExternalDataSourceApi {
    private final DataSourceConfigService service;

    public List<Info> list() {
        return service.getDataSourceConfigList().stream().filter(c -> c.getId() != 0)
                .map(c -> new Info(c.getId(), c.getName(), c.getUrl(), c.getUsername())).toList();
    }

    public Long save(Save c) {
        if (c == null || c.url() == null || !c.url().startsWith("jdbc:mysql://")
                || c.url().contains("@") || c.url().toLowerCase().matches(".*[?&](password|user|allowmultiqueries)=.*"))
            throw new IllegalArgumentException("仅支持 MySQL URL，凭据必须通过独立字段传递");
        if (c.id() != null && c.id() <= 0) throw new IllegalArgumentException("不能修改主数据源");
        String password = c.password();
        if ((password == null || password.isBlank()) && c.id() != null) {
            var old = service.getDataSourceConfig(c.id());
            if (old == null) throw new IllegalArgumentException("数据源不存在");
            password = old.getPassword();
        }
        var request = new DataSourceConfigSaveReqVO().setId(c.id()).setName(c.name()).setUrl(c.url())
                .setUsername(c.username()).setPassword(password);
        if (c.id() == null) return service.createDataSourceConfig(request);
        service.updateDataSourceConfig(request);
        return c.id();
    }

    public Connection openReadOnly(Long id) throws SQLException {
        if (id == null || id <= 0) throw new IllegalArgumentException("不能使用应用主数据源作为外部来源");
        var c = service.getDataSourceConfig(id);
        if (c == null || !c.getUrl().startsWith("jdbc:mysql://")) throw new IllegalArgumentException("MySQL 数据源不存在");
        // Append after existing properties so URL settings cannot override the connector's read boundary.
        String url = c.getUrl() + (c.getUrl().contains("?") ? "&" : "?")
                + "allowMultiQueries=false&connectTimeout=10000&socketTimeout=60000&readOnlyPropagatesToServer=true";
        Properties properties = new Properties();
        properties.setProperty("user", c.getUsername());
        properties.setProperty("password", c.getPassword());
        Connection connection = DriverManager.getConnection(url, properties);
        try {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException ex) {
            connection.close();
            throw ex;
        }
    }
}

