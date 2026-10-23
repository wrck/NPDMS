package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi;

/** 服务器从两个Owner获取的小范围上下文；项目/合同授权在SQL内关联。 */
public record DevicePageReadScope(ProjectViewReadScopeApi.ReadScope projectScope,
                                 ContractDeviceVisibilityApi.ReadScope contractScope) {}
