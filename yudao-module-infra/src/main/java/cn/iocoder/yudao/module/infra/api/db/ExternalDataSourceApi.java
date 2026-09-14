package cn.iocoder.yudao.module.infra.api.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Server-only JDBC access. Passwords never leave this provider in DTOs. */
public interface ExternalDataSourceApi {
    record Info(Long id, String name, String url, String username) {}
    record Save(Long id, String name, String url, String username, String password) {
        @Override public String toString() { return "ExternalDataSourceSave[id=" + id + "]"; }
    }
    List<Info> list();
    Long save(Save command);
    Connection openReadOnly(Long id) throws SQLException;
}

