import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import LocationSelector from './index.vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import * as Api from '@/api/pms/asset/location'
import * as Customers from '@/api/pms/customer'
import * as Projects from '@/api/pms/project/projects'

vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getStrDictOptions: () => [] }))
vi.mock('@/api/pms/asset/location', () => ({
  getProjectCustomerLocations: vi.fn(), getSitePage: vi.fn(), getAddressPage: vi.fn(),
  getAddress: vi.fn(), getSiteLocationTree: vi.fn()
}))
vi.mock('@/api/pms/customer', () => ({ getCustomerByCode: vi.fn() }))
vi.mock('@/api/pms/project/projects', () => ({ getProject: vi.fn() }))
const flush = async () => { for (let i=0;i<15;i++) await nextTick() }
const result = () => ({customerId:200,customerName:'项目客户',sites:[{id:21,customerId:200,addressId:11,name:'客户站点',version:2}],
  addresses:[{id:11,fullAddress:'现场地址',version:3}],customerAddresses:[{id:11,fullAddress:'现场地址',version:3}],siteLocations:[]})
beforeEach(() => { vi.resetAllMocks(); vi.mocked(Api.getProjectCustomerLocations).mockResolvedValue(result()) })
const render = (modelValue: any = undefined, projectId: string | undefined = '992203060198') => {
  const props = reactive({projectId,projectScoped:true,modelValue,'onUpdate:modelValue':vi.fn()})
  const selector = ref<any>()
  const mounted = mount(defineComponent({setup:()=>()=>h(LocationSelector,{...props,ref:selector})}),{},
    Object.fromEntries(['ElRadioGroup','ElRadioButton','ElSelect','ElOption','ElTreeSelect','ElDivider','PmsDivisionInput','ElInput','ElCol','ElRow','ElAlert'].map(name=>[name,passthrough])))
  return {...mounted,props,get state(){return selector.value.$.setupState}}
}
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
