import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Attachments from './ChecklistAttachments.vue'
import * as ChecklistApi from '@/api/pms/acceptance/deliverable-checklist'
import * as DeliveryApi from '@/api/pms/platform/delivery'
import { mount, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/utils', () => ({ generateUUID: vi.fn(() => 'actual-new-slot') }))
vi.mock('@/api/pms/acceptance/deliverable-checklist', () => ({ getDeliverableChecklist: vi.fn(), updateDeliverableChecklist: vi.fn() }))
vi.mock('@/api/pms/platform/delivery', () => ({ listMaterials: vi.fn(), withdrawMaterial: vi.fn() }))
vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileUploader: defineComponent({ setup(_, {attrs}) { return () => h('native-uploader', attrs) } }),
  PmsFileReferenceList: defineComponent({ setup(_, {attrs}) { return () => h('native-file', attrs) } })
}))
const nodes = (node: TestNode, type: string): TestNode[] => [...(node.type === type ? [node] : []), ...node.children.flatMap(child => nodes(child, type))]
const flush = async () => { for (let i=0;i<8;i++) await nextTick() }
beforeEach(() => { vi.resetAllMocks(); vi.mocked(DeliveryApi.listMaterials).mockResolvedValue([]) })
it('native completion uses persisted Owner and preserves retry after a failed save', async () => {
  const mounted=mount(Attachments,{entityId:19,readonly:false}); const state=(mounted.vm as any).$.setupState
  try {
    await flush(); vi.mocked(ChecklistApi.getDeliverableChecklist).mockResolvedValue({id:19,projectId:20,name:'saved',status:0,version:3})
    vi.mocked(ChecklistApi.updateDeliverableChecklist).mockRejectedValueOnce(new Error('registration unavailable'))
    await nodes(mounted.root,'native-uploader')[0].props!.onCompleted({artifactId:31,versionNo:1}); await flush()
    expect(state.pending).toBe(true); expect(state.error).toContain('registration unavailable'); expect(nodes(mounted.root,'native-uploader')).toEqual([])
    await state.collect(); await flush()
    expect(ChecklistApi.updateDeliverableChecklist).toHaveBeenCalledTimes(2)
    expect(ChecklistApi.updateDeliverableChecklist).toHaveBeenLastCalledWith(expect.objectContaining({id:19,name:'saved',version:3}))
    expect(state.pending).toBe(false)
  } finally { mounted.app.unmount() }
})
it('readonly owner cannot upload, save or withdraw attachments', async () => {
  const mounted=mount(Attachments,{entityId:19,readonly:true}); const state=(mounted.vm as any).$.setupState
  try { await flush(); expect(nodes(mounted.root,'native-uploader')).toEqual([]); await state.collect(); await state.withdraw(1); expect(ChecklistApi.updateDeliverableChecklist).not.toHaveBeenCalled(); expect(DeliveryApi.withdrawMaterial).not.toHaveBeenCalled() }
  finally { mounted.app.unmount() }
})
it('a source read failure stays visible and does not open a new upload', async () => {
  vi.mocked(DeliveryApi.listMaterials).mockRejectedValueOnce(new Error('read unavailable'))
  const mounted=mount(Attachments,{entityId:19,readonly:false}); const state=(mounted.vm as any).$.setupState
  try { await flush(); expect(state.error).toContain('read unavailable'); expect(nodes(mounted.root,'native-uploader')).toEqual([]) }
  finally { mounted.app.unmount() }
})
