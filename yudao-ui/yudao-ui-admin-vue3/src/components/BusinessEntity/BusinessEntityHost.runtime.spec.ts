import { nextTick } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import Host from './BusinessEntityHost.vue'
import request from '@/config/axios'
import { mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import type { TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/utils/auth', () => ({ getCurrentUserId: () => 0, getTenantId: () => 7, getVisitTenantId: () => undefined }))
vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn(), getProject: vi.fn(async () => ({ id: 7, projectName: 'test' })) }))
vi.mock('@/components/BusinessView/registry', async () => {
  const { defineComponent, h } = await import('vue')
  const component = defineComponent({ props: ['project'], setup: props => () => h('div', `professional-project:${props.project.id}`) })
  return { resolveStandaloneBusinessEntityView: (code: string) => code === 'sol_requirement_analysis' ? { component, resolve: (project: unknown) => ({ project }) } : undefined }
})
vi.mock('@/components/PmsEntitySelect/index.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return { default: defineComponent({ emits: ['update:modelValue'], setup: (_, { emit }) => () => h('button', { onClick: () => emit('update:modelValue', 7) }, 'select-project') }) }
})
vi.mock('./BusinessEntityList.vue', () => ({ default: { render: () => null } }))
vi.mock('./BusinessEntityForm.vue', () => ({ default: { render: () => null } }))
vi.mock('./DeliveryPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./ApprovalPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./ContentHistoryPanel.vue', () => ({ default: { render: () => null } }))
const flush = async () => { for (let i=0;i<12;i++) { await Promise.resolve(); await nextTick() } }
const button = (root: TestNode): TestNode | undefined => root.type === 'button' && textOf(root).includes('select-project') ? root : root.children.map(button).find(Boolean)
beforeEach(() => vi.clearAllMocks())
it('consumes the real detail API shape and selects the professional view without querying a flat list', async () => {
  vi.mocked(request.get).mockResolvedValue({ ownerModule:'SOL',entityType:'requirementAnalysis',stableCode:'RA',title:'RA',viewCode:'sol_requirement_analysis',fields:[],operations:[],capabilities:[] })
  const mounted=mount(Host,{ ownerModule:'SOL',entityType:'requirementAnalysis' },{ ContentWrap:passthrough,ElForm:passthrough,ElFormItem:passthrough,ElEmpty:passthrough })
  await flush()
  expect(request.get).toHaveBeenCalledWith({ url:'/api/v1/pms/business-models/SOL/requirementAnalysis',silentError:true })
  expect(request.post).not.toHaveBeenCalled()
  await (button(mounted.root)!.props!.onClick as Function)(); await flush()
  expect(textOf(mounted.root)).toContain('professional-project:7')
  mounted.app.unmount()
})


it('rejects a runtime component whose stable identity does not match the authenticated model directory', async () => {
  vi.mocked(request.get).mockResolvedValue({ ownerModule:'IT',entityType:'note',stableCode:'IT_NOTE',fields:[],operations:[],capabilities:[] })
  const mounted=mount(Host,{ ownerModule:'IT',entityType:'note',expectedStableCode:'FORGED',initialEntityId:7 },{ ContentWrap:passthrough,ElAlert:passthrough })
  await flush()
  expect(request.post).not.toHaveBeenCalled()
  expect(textOf(mounted.root)).toContain('运行视图与声明模型不匹配')
  mounted.app.unmount()
})
