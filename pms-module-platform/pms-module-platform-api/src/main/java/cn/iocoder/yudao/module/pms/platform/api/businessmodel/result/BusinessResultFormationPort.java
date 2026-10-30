package cn.iocoder.yudao.module.pms.platform.api.businessmodel.result;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;

/**
 * 统一业务结果形成与失效：形成依据必须是真实业务依据（事件身份/执行引用），
 * 不能以字段修改时间替代；同一依据重复形成幂等返回原结果。
 */
public interface BusinessResultFormationPort {

    BusinessResultRecord form(String resultType, EntityRef objectRef, ResultSemantics semantics,
                              String formationBasis, String formedByBackend);

    void invalidate(String resultType, EntityRef objectRef, String basis);
}
