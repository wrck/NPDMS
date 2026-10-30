import type { SiteSurveyVO } from './entity'

/** 查询投影不是写命令；移除编码与状态等服务端控制字段；保留原写字段，未知新增字段交给服务端严格校验。 */
export function siteSurveyOperationInput(data: SiteSurveyVO): Record<string, unknown> {
  const { execution: _execution, code: _code, status: _status, addressId: _addressId, addressVersion: _addressVersion,
    siteId: _siteId, siteVersion: _siteVersion, siteLocationId: _siteLocationId,
    siteLocationVersion: _siteLocationVersion, locationResolutionStatus: _locationResolutionStatus,
    addressSnapshot: _addressSnapshot, locationSnapshot: _locationSnapshot,
    outsourceRequestId: _outsourceRequestId, fieldBindings: _fieldBindings,
    fieldCatalog: _fieldCatalog, createTime: _createTime, ...input } = data
  return input
}
