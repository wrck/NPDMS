import request from '@/config/axios'

export interface AddressVO {
  id?: number
  expectedVersion?: number
  countryCode?: string
  countryName?: string
  provinceCode?: string
  provinceName?: string
  cityCode?: string
  cityName?: string
  districtCode?: string
  districtName?: string
  detailAddress?: string
  fullAddress?: string
  longitude?: number
  latitude?: number
  normalizedAddress?: string
  addressFingerprint?: string
  status?: number
  version?: number
}

export interface SiteVO {
  id?: number
  expectedVersion?: number
  code?: string
  name?: string
  customerId?: number
  addressId?: number
  siteType?: string
  status?: number
  version?: number
}

export interface SiteLocationVO {
  id?: number
  expectedVersion?: number
  siteId?: number
  parentId?: number
  code: string
  name: string
  locationType: string
  treePath?: string
  treeDepth?: number
  treeSort: number
  status?: number
  version?: number
  children?: SiteLocationVO[]
}

export interface AreaDepartmentMappingVO {
  id?: number
  expectedVersion?: number
  areaCode: string
  areaLevel: string
  mappingType?: string
  departmentCode: string
  departmentName?: string
  effectiveFrom: string
  effectiveTo?: string
  status: number
  version?: number
}

export interface LocationMaintainRequest {
  projectId?: number
  address?: AddressVO
  site?: SiteVO
  siteLocation?: SiteLocationVO
  fallbackLocation?: string
  sourceBusinessType?: string
  sourceBusinessId?: string
  sourceVersion?: string
}

const baseUrl = '/pms/asset-locations'

export const maintainLocation = (data: LocationMaintainRequest) =>
  request.post({ url: `${baseUrl}/maintain`, data })
export const getAddressPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/addresses/page`, params })
export const getAddress = (id: number, version?: number) =>
  request.get({ url: `${baseUrl}/addresses/get`, params: { id, version } })
export const getSitePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/sites/page`, params })
export const getSite = (id: number, version?: number) =>
  request.get({ url: `${baseUrl}/sites/get`, params: { id, version } })
export const getSiteLocationTree = (siteId: number): Promise<SiteLocationVO[]> =>
  request.get({ url: `${baseUrl}/sites/tree`, params: { siteId } })
export const disableSiteLocation = (data: { id: number; version: number }) =>
  request.put({ url: `${baseUrl}/sites/tree/disable`, data })
export const getAreaDepartmentMappingPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/area-department-mappings/page`, params })
export const resolveAreaDepartment = (areaCode: string, areaLevel: string) =>
  request.get<AreaDepartmentMappingVO | null>({
    url: `${baseUrl}/area-department-mappings/resolve`,
    params: { areaCode, areaLevel }
  })
export const saveAreaDepartmentMapping = (data: AreaDepartmentMappingVO) =>
  request.post({ url: `${baseUrl}/area-department-mappings/save`, data })
