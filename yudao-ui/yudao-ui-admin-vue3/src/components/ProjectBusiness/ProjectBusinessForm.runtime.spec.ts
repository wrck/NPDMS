import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Form from './ProjectBusinessForm.vue'

const mocks = vi.hoisted(() => ({ build: vi.fn() }))
vi.mock('./ProjectBusinessContentForm.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ buildInput: mocks.build }); return () => h('input') }
}) }))
const flush = async () => { for (let i = 0; i < 16; i++) { await Promise.resolve(); await nextTick() } }
const layout = (id: string) => ({ binding: { formRevisionId: id, version: 1, fieldBindings: {} }, fields: [], formVersion: 1, formConfJson: '{}', formRulesJson: '[]' })
const api = () => ({ formDefaults: vi.fn().mockResolvedValue({ extensions: { version: 0, fields: {} }, definitions: [] }), formOptions: vi.fn().mockResolvedValue([{ name: '第二配置', layout: layout('2') }]) })
const current = { ref: { entityId: '11' }, concurrencyBasis: 1 }
const presentation = { layout: layout('1'), extensions: { version: 0, fields: {} }, definitions: [] }
beforeEach(() => { vi.resetAllMocks(); mocks.build.mockResolvedValue({}) })

it('passes server default project qualification to a new specialized form',async()=>{
  const client=api()
  client.formDefaults.mockResolvedValue({extensions:{version:0,fields:{}},definitions:[],context:{project:{customerId:'2102228462045130754',projectVersion:5}}} as any)
  const contactForm=defineComponent({props:['presentation'],setup(props,{expose}){
    expose({buildInput:async()=>{
      const project=props.presentation?.context?.project
      if(!project?.customerId)throw new Error('项目尚未关联客户')
      return {name:'验收联系人',$business:{expectedProjectVersion:project.projectVersion}}
    }})
    return()=>h('input')
  }})
  const mounted=mount(Form,{api:client,scopeProjectId:20,formComponent:contactForm,writableFields:[]})
  try{await flush();expect(await (mounted.vm as any).buildInput()).toEqual({name:'验收联系人',$business:{expectedProjectVersion:5}})}
  finally{mounted.app.unmount()}
})

it('allows a saved detail form to complete when the server returns object keys in a different order', async () => {
  const stored = [{ deviceName: '验收设备', serialNumber: 'IT-001', options: { enabled: false, count: 0 } }]
  const displayed = [{ serialNumber: 'IT-001', options: { count: 0, enabled: false }, deviceName: '验收设备' }]
  const readValues = vi.fn().mockResolvedValue({ fields: { businessDeviceDetails: displayed }, extensions: { devices: displayed } })
  const detailForm = defineComponent({ setup(_, { expose }) { expose({ readValues }); return () => h('input') } })
  const mounted = mount(Form, { api: api(), current, scopeProjectId: 20, formComponent: detailForm,
    presentation: { ...presentation, extensions: { version: 1, fields: { devices: stored } } },
    writableFields: [], initialValues: { businessDeviceDetails: stored } })
  try {
    await flush()
    expect(await (mounted.vm as any).buildInput()).toEqual({})
    const changed = [{ ...displayed[0], options: { count: 1, enabled: false } }]
    readValues.mockResolvedValue({ fields: { businessDeviceDetails: changed }, extensions: { devices: changed } })
    expect(await (mounted.vm as any).buildInput()).toMatchObject({ businessDeviceDetails: changed, $extensions: { expectedVersion: 1, values: { devices: changed } } })
  } finally { mounted.app.unmount() }
})

it('retains actual detail row reordering as an unsaved change', async () => {
  const stored = [{ sn: 'IT-001' }, { sn: 'IT-002' }], reordered = [...stored].reverse()
  const mounted = mount(Form, { api: api(), current, scopeProjectId: 20, presentation, writableFields: [], initialValues: { details: stored } })
  try {
    await flush(); mocks.build.mockResolvedValue({ details: reordered })
    expect(await (mounted.vm as any).buildInput()).toEqual({ details: reordered })
  } finally { mounted.app.unmount() }
})

it('saves a configuration-only change and retains false, zero and arrays across selections', async () => {
  const mounted = mount(Form, { api: api(), current, scopeProjectId: 20, presentation, writableFields: [], initialValues: {} })
  try {
    await flush(); const state = (mounted.vm as any).$.setupState
    await state.choose('2'); await flush()
    expect((await (mounted.vm as any).buildInput()).$binding.formRevisionId).toBe('2')
    mocks.build.mockResolvedValueOnce({ flag: false, count: 0, tags: [], $extensions: { expectedVersion: 0, values: { hidden: false } } })
    await state.choose('1'); await flush()
    expect(await (mounted.vm as any).buildInput()).toMatchObject({ flag: false, count: 0, tags: [], $extensions: { values: { hidden: false } } })
  } finally { mounted.app.unmount() }
})

it('does not replace a stored readonly layout with a newer publication', async () => {
  const client = api(), mounted = mount(Form, { api: client, current, scopeProjectId: 20, presentation, writableFields: [], disabled: true, readonly: true })
  try { await flush(); expect(client.formDefaults).not.toHaveBeenCalled(); expect(client.formOptions).not.toHaveBeenCalled(); expect(await (mounted.vm as any).buildInput()).toEqual({}) }
  finally { mounted.app.unmount() }
})

it('rejects saving after a configuration load failure and allows an explicit retry', async () => {
  const client = api(); client.formOptions.mockRejectedValueOnce(new Error('offline'))
  const mounted = mount(Form, { api: client, scopeProjectId: 20, writableFields: [] })
  try {
    await flush(); await expect((mounted.vm as any).buildInput()).rejects.toThrow()
    await (mounted.vm as any).$.setupState.load(); await flush()
    expect(await (mounted.vm as any).buildInput()).toEqual({})
  } finally { mounted.app.unmount() }
})

it('ignores a late configuration response after the entity scope changes', async () => {
  let completeOld:(value:unknown)=>void=()=>undefined
  const client=api(), scope=ref(20)
  client.formDefaults.mockImplementationOnce(()=>new Promise(resolve=>{completeOld=resolve})).mockResolvedValue({layout:layout('new'),extensions:{version:0,fields:{}},definitions:[],context:{projectId:21}} as any)
  const mounted=mount(defineComponent({setup:()=>()=>h(Form,{api:client as any,scopeProjectId:scope.value,writableFields:[]})}))
  try{
    await flush();scope.value=21;await flush();completeOld({layout:layout('old'),extensions:{version:0,fields:{}},definitions:[],context:{projectId:20}});await flush()
    const child=(mounted.vm as any).$.subTree.component.setupState
    expect(child.selected.binding.formRevisionId).toBe('new')
    expect(child.visiblePresentation.context.projectId).toBe(21)
    expect(client.formOptions).toHaveBeenCalledTimes(1);expect(client.formOptions).toHaveBeenCalledWith(21)
  }finally{mounted.app.unmount()}
})

it('keeps published definition sources when the first save follows a layout switch', async () => {
  const full={...layout('1'),binding:{...layout('1').binding,fieldBindings:{flag:'flag',tags:'tags'}},fields:[{fieldKey:'flag',valueType:'boolean',componentType:'switch'},{fieldKey:'tags',valueType:'array',componentType:'checkbox'}]}
  const compact=layout('2'),client=api()
  client.formDefaults.mockResolvedValue({layout:full,extensions:{version:0,fields:{}},definitions:[]})
  const mounted=mount(Form,{api:client,scopeProjectId:20,writableFields:[],initialValues:{}})
  try{
    await flush();mocks.build.mockResolvedValueOnce({$extensions:{expectedVersion:0,values:{flag:false,tags:['A','B']}}})
    await (mounted.vm as any).$.setupState.choose('2');await flush()
    const input=await (mounted.vm as any).buildInput()
    expect(input.$binding).toMatchObject({formRevisionId:compact.binding.formRevisionId,definitionSources:[{formRevisionId:'1',fieldBindings:{flag:'flag',tags:'tags'}}]})
    expect(input.$extensions.values).toEqual({flag:false,tags:['A','B']})
    expect((mounted.vm as any).$.setupState.visiblePresentation.definitions.map(field=>field.code)).toEqual(['flag','tags'])
  }finally{mounted.app.unmount()}
})
it('carries newly edited extension definitions after switching back to the saved layout',async()=>{
  const client=api(),other={...layout('2'),binding:{...layout('2').binding,fieldBindings:{newFlag:'newFlag'}},fields:[{fieldKey:'newFlag',valueType:'boolean',componentType:'switch'}]}
  client.formOptions.mockResolvedValue([{name:'新增字段配置',layout:other}])
  const mounted=mount(Form,{api:client,current,scopeProjectId:20,presentation,writableFields:[],initialValues:{}})
  try{
    await flush();await (mounted.vm as any).$.setupState.choose('2');await flush()
    mocks.build.mockResolvedValueOnce({$extensions:{expectedVersion:0,values:{newFlag:false}}})
    await (mounted.vm as any).$.setupState.choose('1');await flush()
    expect(await (mounted.vm as any).buildInput()).toMatchObject({$binding:{formRevisionId:'1',definitionSources:[{formRevisionId:'2',fieldBindings:{newFlag:'newFlag'}}]},$extensions:{values:{newFlag:false}}})
  }finally{mounted.app.unmount()}
})
