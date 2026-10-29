import { expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Form from './CompletionCertificateForm.vue'
import PmsEntitySelect from '@/components/PmsEntitySelect/index.vue'
const users = vi.hoisted(() => ({
  getUserPage: vi.fn(async () => { throw new Error('FORBIDDEN') }),
  getSimpleUserList: vi.fn(async () => [{ id: 1, nickname: '当前工程师' }, { id: 2, nickname: '另一工程师' }])
}))
vi.mock('@/api/system/user', () => users)
vi.mock('@/api/pms/project/projects', () => ({ getProjectPage: vi.fn() }))
vi.mock('@/components/FormCreate', () => ({ SignaturePad: { render: () => null } }))
it('opens the engineer picker through the enabled-user directory without requiring user administration', async () => {
  const view = mount(Form, { projectId: 41, projectName: '验收项目', disabled: false, printTemplates: [], devices: [],
    form: { projectId: 41, engineerUserId: 1, engineerName: '当前工程师' } },
  { PmsEntitySelect, ...Object.fromEntries(['ElInput', 'ElSelect', 'ElOption', 'ElRow', 'ElCol', 'ElDatePicker', 'ElRadio', 'ElRadioGroup', 'ElInputNumber'].map(name => [name, passthrough])) })
  try {
    for (let i = 0; i < 8; i++) { await nextTick(); await Promise.resolve() }
    const nodes: any[] = []
    const walk = (node: any) => { nodes.push(node); (node.children || []).forEach(walk) }
    walk(view.root)
    const picker = nodes.find(node => node.props?.placeholder === '默认当前用户，可修改' && node.props?.onVisibleChange)
    expect(picker).toBeTruthy()
    await picker.props.onVisibleChange(true)
    for (let i = 0; i < 8; i++) { await nextTick(); await Promise.resolve() }
    expect(users.getSimpleUserList).toHaveBeenCalled()
    expect(users.getUserPage).not.toHaveBeenCalled()
    const result = await (view.vm as any).$.setupState.findEngineers({ nickname: '另一' })
    expect(result.list).toEqual([{ id: 2, nickname: '另一工程师' }])
  } finally { view.app.unmount() }
})

it('keeps customer signing read-only while retaining vendor signing', () => {
  const view=mount(Form,{projectId:41,projectName:'项目',disabled:false,printTemplates:[],devices:[],
    form:{projectId:41,customerSignUrl:'/historical.png',customerSignDate:'2026-10-10'}},
    Object.fromEntries(['ElInput','ElDatePicker','ElRadio','ElRadioGroup','ElInputNumber','PmsEntitySelect'].map(name=>[name,passthrough])))
  try {
    expect(textOf(view.root)).toContain('客户通过推送链接提交')
    const nodes: any[] = []
    const walk = (node: any) => { nodes.push(node); (node.children || []).forEach(walk) }
    walk(view.root)
    expect(nodes.some(node => node.props?.['aria-label'] === '甲方签章日期')).toBe(false)
    expect(nodes.find(node => node.props?.alt === '历史客户签字')?.props.src).toBe('/historical.png')
    expect(textOf(view.root)).toContain('服务方签章')
  } finally { view.app.unmount() }
})

it.each([
  ['是', '2026-10-20', '于2026年10月20日完成到货验收'],
  ['是', null, '于____年___月___日完成到货验收'],
  ['否', '2026-10-20', '完成到货验收'],
  ['不涉及', '2026-10-20', '完成到货验收'],
  ['', '2026-10-20', '完成到货验收']
])('renders arrival date prefix in the editable form only for yes: %s', (itemArrival, arrivalDate, expected) => {
  const view=mount(Form,{projectId:41,projectName:'项目',disabled:false,printTemplates:[],devices:[],
    form:{projectId:41,itemArrival,arrivalDate}},
    Object.fromEntries(['ElInput','ElDatePicker','ElRadio','ElRadioGroup','ElInputNumber','PmsEntitySelect'].map(name=>[name,passthrough])))
  try {
    expect(textOf(view.root)).toContain(expected)
    if(itemArrival !== '是')expect(textOf(view.root)).not.toContain('于2026年')
  } finally { view.app.unmount() }
})

it.each([
  ['是', true, true], ['是', false, false], ['否', true, false], ['不涉及', true, false], ['', true, false]
])('allows manual arrival date only for yes without a signed source: %s %s', (itemArrival, arrivalDateManualAllowed, expected) => {
  const view=mount(Form,{projectId:41,projectName:'项目',disabled:false,printTemplates:[],devices:[],
    form:{projectId:41,itemArrival,arrivalDateManualAllowed}},
    Object.fromEntries(['ElInput','ElDatePicker','ElRadio','ElRadioGroup','ElInputNumber','PmsEntitySelect'].map(name=>[name,passthrough])))
  try {
    const nodes:any[]=[]
    const walk=(node:any)=>{nodes.push(node);(node.children||[]).forEach(walk)}
    walk(view.root)
    expect(nodes.some(node=>node.props?.['aria-label']==='到货日期')).toBe(expected)
  } finally {view.app.unmount()}
})
