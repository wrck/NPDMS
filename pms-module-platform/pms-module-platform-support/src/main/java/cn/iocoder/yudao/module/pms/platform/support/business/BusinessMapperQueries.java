package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector;
import cn.iocoder.yudao.module.pms.platform.support.model.DeclaredBusinessFieldValues;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.springframework.core.ResolvableType;

/** Simple SQL uses only trusted ORM columns and bound values; no model registry or operation dispatcher. */
final class BusinessMapperQueries {
    private BusinessMapperQueries() { }
    static <E extends BaseProjectBusinessEntity> PageResult<E> page(BusinessMapper<E> mapper, BusinessReadQuery query) {
        var criteria = query.criteria();
        if (query.tenantId() == null || criteria == null || criteria.getPageNo() < 1 || criteria.getPageSize() < 1 || criteria.getPageSize() > 200)
            throw new BusinessContractException("QUERY_INVALID", "Invalid tenant or page bounds");
        if (query.projectIds().isEmpty()) return new PageResult<>(List.of(), 0L);
        Class<?> type = ResolvableType.forInstance(mapper).as(BaseMapper.class).getGeneric(0).resolve();
        if (type == null) throw new BusinessContractException("MAPPER_TYPE_UNRESOLVED", "Business Mapper must bind its entity type");
        var fields = BusinessModelIntrospector.businessFields(type);
        var wrapper = new QueryWrapper<E>().eq("tenant_id", query.tenantId()).in("project_id", query.projectIds());
        for (var filter : criteria.getFilters() == null ? List.<cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter>of() : criteria.getFilters()) {
            if (filter == null || filter.operator() == null) throw new BusinessContractException("FILTER_INVALID", "Invalid field filter");
            var field = fields.stream().filter(item -> item.code().equals(filter.fieldCode())).findFirst()
                    .orElseThrow(() -> new BusinessContractException("FILTER_FIELD_UNKNOWN", "Unknown business filter field"));
            var exposure = field.property().getAnnotation(BusinessModelField.class);
            if (exposure == null || !exposure.readable()) throw new BusinessContractException("FILTER_FIELD_FORBIDDEN", "Field is not readable");
            var values = filter.values() == null ? List.of() : filter.values();
            boolean unary = filter.operator() == cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter.Operator.IS_NULL
                    || filter.operator() == cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter.Operator.NOT_NULL;
            boolean multiple = filter.operator() == cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter.Operator.IN;
            if ((!unary && !multiple && values.size() != 1) || (unary && !values.isEmpty()))
                throw new BusinessContractException("FILTER_INVALID", "Filter operand count is invalid");
            var converted = values.stream().map(value -> DeclaredBusinessFieldValues.convert(value, field)).toList();
            Object value = converted.isEmpty() ? null : converted.getFirst();
            String column = field.column();
            switch (filter.operator()) {
                case EQ -> { if (value == null) wrapper.isNull(column); else wrapper.eq(column, value); }
                case NE -> { if (value == null) wrapper.isNotNull(column); else wrapper.ne(column, value); }
                case IN -> { if (converted.isEmpty()) return new PageResult<>(List.of(), 0L); wrapper.in(column, converted); }
                case LIKE -> {
                    if (field.type() != cn.iocoder.yudao.module.pms.platform.api.entity.EntityField.Type.TEXT || value == null)
                        throw new BusinessContractException("FILTER_INVALID", "LIKE requires a text value");
                    wrapper.like(column, value);
                }
                case GT -> wrapper.gt(column, value);
                case GTE -> wrapper.ge(column, value);
                case LT -> wrapper.lt(column, value);
                case LTE -> wrapper.le(column, value);
                case IS_NULL -> wrapper.isNull(column);
                case NOT_NULL -> wrapper.isNotNull(column);
            }
        }
        wrapper.orderByDesc("id");
        var page = mapper.selectPage(new Page<E>(criteria.getPageNo(), criteria.getPageSize()), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }
}
