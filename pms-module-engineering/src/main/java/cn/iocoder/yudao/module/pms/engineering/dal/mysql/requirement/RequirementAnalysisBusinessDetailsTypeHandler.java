package cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisBusinessDetail;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.sql.*;
import java.util.List;

/** Keeps the repeated business rows typed when reading both current and historical content. */
public class RequirementAnalysisBusinessDetailsTypeHandler extends BaseTypeHandler<List<RequirementAnalysisBusinessDetail>> {
    @Override public void setNonNullParameter(PreparedStatement statement, int index,
            List<RequirementAnalysisBusinessDetail> value, JdbcType jdbcType) throws SQLException {
        statement.setString(index, JsonUtils.toJsonString(value));
    }
    @Override public List<RequirementAnalysisBusinessDetail> getNullableResult(ResultSet result, String column) throws SQLException {
        return read(result.getString(column));
    }
    @Override public List<RequirementAnalysisBusinessDetail> getNullableResult(ResultSet result, int column) throws SQLException {
        return read(result.getString(column));
    }
    @Override public List<RequirementAnalysisBusinessDetail> getNullableResult(CallableStatement result, int column) throws SQLException {
        return read(result.getString(column));
    }
    private List<RequirementAnalysisBusinessDetail> read(String value) {
        return value == null ? null : JsonUtils.parseArray(value, RequirementAnalysisBusinessDetail.class);
    }
}
