import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, onErrorCaptured, reactive, ref } from 'vue'
import LocationSelector from './index.vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import * as Api from '@/api/pms/asset/location'
import * as Customers from '@/api/pms/customer'
import * as Projects from '@/api/pms/project/projects'
import * as Installation from '@/api/pms/engineering/installation'

vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getStrDictOptions: () => [] }))
vi.mock('@/api/pms/asset/location', () => ({
  getProjectCustomerLocations: vi.fn(), getSitePage: vi.fn(), getAddressPage: vi.fn(),
  getAddress: vi.fn(), getSiteLocationTree: vi.fn()
}))
vi.mock('@/api/pms/customer', () => ({ getCustomerByCode: vi.fn() }))
vi.mock('@/api/pms/project/projects', () => ({ getProject: vi.fn() }))
vi.mock('@/api/pms/engineering/installation', () => ({ getInstallationProjectSites: vi.fn() }))
const flush = async () => { for (let i=0;i<15;i++) await nextTick() }
const result = () => ({customerId:200,customerName:'项目客户',sites:[{id:21,customerId:200,addressId:11,name:'客户站点',version:2}],
  addresses:[{id:11,fullAddress:'现场地址',version:3}],customerAddresses:[{id:11,fullAddress:'现场地址',version:3}],siteLocations:[]})
beforeEach(() => { vi.resetAllMocks(); vi.mocked(Api.getProjectCustomerLocations).mockResolvedValue(result()) })
const render = (modelValue: any = undefined, projectId: string | undefined = '992203060198', capturedErrors?: unknown[], installationProjectSites = false) => {
  const props = reactive({projectId,projectScoped:true,installationProjectSites,modelValue,'onUpdate:modelValue':vi.fn()})
  const selector = ref<any>()
  const mounted = mount(defineComponent({setup:()=>{
    if (capturedErrors) onErrorCaptured(error => { capturedErrors.push(error); return false })
    return () => h(LocationSelector,{...props,ref:selector})
  }}),{},
    Object.fromEntries(['ElRadioGroup','ElRadioButton','ElSelect','ElOption','ElTreeSelect','ElDivider','PmsDivisionInput','ElInput','ElCol','ElRow','ElAlert'].map(name=>[name,passthrough])))
  return {...mounted,props,get state(){return selector.value.$.setupState}}
}
it('allows an enabled current installation site without broadening global reads', async () => {
  vi.mocked(Installation.getInstallationProjectSites).mockResolvedValue({
    sites: [{ id: 98, name: '历史站点', addressId: 18, status: 0 }],
    addresses: [{ id: 18, fullAddress: '历史地址' }], siteLocations: []
  })
  const m = render({ site: { id: 98 }, fallbackLocation: '历史地址 / 历史站点' }, '20', undefined, true)
  try {
    await flush()
    expect(m.state.sites.map((site: any) => site.id)).toEqual([21, 98])
    expect(m.state.getValidationError()).toBe('')
    expect(Installation.getInstallationProjectSites).toHaveBeenCalledWith('20', 98)
    noGlobalReads()
  } finally { m.app.unmount() }
})
const noGlobalReads = () => {
  expect(Api.getSitePage).not.toHaveBeenCalled();expect(Api.getAddressPage).not.toHaveBeenCalled()
  expect(Api.getAddress).not.toHaveBeenCalled();expect(Api.getSiteLocationTree).not.toHaveBeenCalled()
  expect(Customers.getCustomerByCode).not.toHaveBeenCalled();expect(Projects.getProject).not.toHaveBeenCalled()
}
it('loads project customer locations without global location or customer queries',async()=>{
  const m=render();try{await flush()
    expect(Api.getProjectCustomerLocations).toHaveBeenCalledWith('992203060198')
    expect(m.state.sites[0].id).toBe(21);expect(m.state.customerName).toBe('项目客户')
    noGlobalReads()
    await m.state.selectSite(21)
    expect(Api.getProjectCustomerLocations).toHaveBeenLastCalledWith('992203060198',21)
    noGlobalReads()
  }finally{m.app.unmount()}
})
it('reopens saved address and site versions without replacing reference inputs',async()=>{
  const m=render({projectId:'992203060198',address:{id:11,expectedVersion:3},site:{id:21,expectedVersion:2}})
  try{await flush();expect(m.state.draft.address).toEqual({id:11,expectedVersion:3})
    expect(m.state.draft.site).toEqual({id:21,expectedVersion:2});noGlobalReads()
  }finally{m.app.unmount()}
})
it('shows the saved location text for an out-of-scope site while keeping it invalid', async () => {
  const m = render({ projectId: '992203060198', site: { id: 98, expectedVersion: 2 }, fallbackLocation: '河北省廊坊市 / 原站点' })
  try {
    await flush()
    expect(m.state.unavailableSiteLabel).toBe('河北省廊坊市 / 原站点')
    expect(m.state.getValidationError()).toContain('原站点不在当前项目可选范围')
    expect(m.state.sites.map((site: any) => site.id)).toEqual([21])
    expect(m.state.draft.site).toEqual({ id: 98, expectedVersion: 2 })
    noGlobalReads()
  } finally { m.app.unmount() }
})
it('keeps a saved survey location mounted when reopening its site tree is forbidden', async () => {
  const saved = { projectId: '992203060198', address: { id: 11, expectedVersion: 3 },
    site: { id: 21, expectedVersion: 2 }, siteLocation: { id: 31, expectedVersion: 4 },
    fallbackLocation: '已保存的现场机房' }
  vi.mocked(Api.getProjectCustomerLocations).mockImplementation(async (_projectId, siteId) => {
    if (siteId === 21) throw new Error('没有该操作权限')
    return result()
  })
  const capturedErrors: unknown[] = []
  const m = render(saved, saved.projectId, capturedErrors)
  try {
    await flush()
    expect(Api.getProjectCustomerLocations).toHaveBeenCalledWith(saved.projectId, 21)
    expect(capturedErrors).toEqual([])
    expect(m.state.getValidationError()).toContain('项目地点加载失败')
    expect(m.state.draft.address).toEqual(saved.address)
    expect(m.state.draft.site).toEqual(saved.site)
    expect(m.state.draft.siteLocation).toEqual(saved.siteLocation)
    expect(m.state.draft.fallbackLocation).toBe(saved.fallbackLocation)
    noGlobalReads()
  } finally { m.app.unmount() }
})
it('shows a failed project lookup and never falls back to unrestricted endpoints',async()=>{
  vi.mocked(Api.getProjectCustomerLocations).mockRejectedValue(new Error('scope denied'))
  const m=render();try{await flush();expect(m.state.sites).toEqual([])
    expect(m.state.contextError).toContain('项目地点加载失败');noGlobalReads()
  }finally{m.app.unmount()}
})
it('does not query global locations when no project is selected',async()=>{
  const m=render(undefined,'')
  try{await flush();expect(m.state.sites).toEqual([]);expect(Api.getProjectCustomerLocations).not.toHaveBeenCalled();noGlobalReads()}
  finally{m.app.unmount()}
})
it('keeps an unavailable historical reference visible and requires an explicit new selection', async () => {
  const saved = { projectId: '992203060198', site: { id: 33, expectedVersion: 4 }, fallbackLocation: '历史安装地址' }
  const m = render(saved)
  try {
    await flush()
    expect(Api.getProjectCustomerLocations).not.toHaveBeenCalledWith('992203060198', 33)
    expect(m.state.getValidationError()).toContain('不在当前项目可选范围')
    expect(m.state.draft.site).toEqual(saved.site)
    expect(m.state.draft.fallbackLocation).toBe('历史安装地址')
    m.state.mode = 'new'
    m.state.changeMode('new')
    await flush()
    expect(m.state.getValidationError()).toBe('')
    expect(m.state.draft.site.id).toBeUndefined()
    expect(saved.site.id).toBe(33)
    noGlobalReads()
  } finally { m.app.unmount() }
})
it('refreshes project-scoped options after a new site is saved without trusting an arbitrary saved ID', async () => {
  const m = render()
  try {
    await flush()
    m.state.mode = 'new'
    m.state.changeMode('new')
    await flush()
    const created = { id: 22, customerId: 200, addressId: 11, name: '新建工勘站点', version: 1 }
    vi.mocked(Api.getProjectCustomerLocations).mockResolvedValue({ ...result(), sites: [...result().sites, created] })
    m.props.modelValue = { projectId: '992203060198', site: { id: 22, expectedVersion: 1 } }
    await flush()
    expect(m.state.sites.find(site => site.id === 22)?.name).toBe('新建工勘站点')
    expect(m.state.getValidationError()).toBe('')
    expect(m.state.selectedSiteId).toBe(22)
    noGlobalReads()
    m.props.modelValue = { projectId: '992203060198', site: { id: 99, expectedVersion: 1 }, fallbackLocation: '不可选站点' }
    await flush()
    expect(m.state.getValidationError()).toContain('不在当前项目可选范围')
    expect(m.state.sites.some(site => site.id === 99)).toBe(false)
    expect(Api.getProjectCustomerLocations).not.toHaveBeenCalledWith('992203060198', 99)
  } finally { m.app.unmount() }
})
it('keeps a failed project lookup blocking after changing location mode', async () => {
  vi.mocked(Api.getProjectCustomerLocations).mockRejectedValue(new Error('scope denied'))
  const m = render()
  try {
    await flush()
    m.state.mode = 'new'
    m.state.changeMode('new')
    await flush()
    expect(m.state.getValidationError()).toContain('项目地点加载失败')
    noGlobalReads()
  } finally { m.app.unmount() }
})
it.each(['success', 'failure'] as const)('ignores a late %s from the previously selected site', async outcome => {
  const m = render()
  try {
    await flush()
    m.state.sites = [...result().sites, { id: 22, name: '第二站点' }]
    let finishFirst!: (value: any) => void
    let failFirst!: (reason: Error) => void
    const first = new Promise<any>((resolve, reject) => { finishFirst = resolve; failFirst = reject })
    vi.mocked(Api.getProjectCustomerLocations).mockImplementation(async (_projectId, siteId) => siteId === 21
      ? first : { ...result(), siteLocations: [{ id: 221, siteId: 22, name: '第二站点机房' }] })
    const firstSelection = m.state.selectSite(21)
    await m.state.selectSite(22)
    if (outcome === 'success') finishFirst({ ...result(), siteLocations: [{ id: 211, siteId: 21, name: '旧机房' }] })
    else failFirst(new Error('old request failed'))
    await firstSelection
    expect(m.state.locationTree.map(node => node.id)).toEqual([221])
    expect(m.state.getValidationError()).toBe('')
  } finally { m.app.unmount() }
})
it('keeps saving blocked until the latest selected site finishes loading', async () => {
  const m = render()
  try {
    await flush()
    m.state.sites = [...result().sites, { id: 22, name: '第二站点' }]
    const resolvers: Array<(value: any) => void> = []
    vi.mocked(Api.getProjectCustomerLocations).mockImplementation(() => new Promise(resolve => resolvers.push(resolve)))
    const first = m.state.selectSite(21)
    const second = m.state.selectSite(22)
    resolvers[0](result())
    await first
    expect(m.state.getValidationError()).toContain('正在加载')
    resolvers[1](result())
    await second
    expect(m.state.getValidationError()).toBe('')
  } finally { m.app.unmount() }
})
