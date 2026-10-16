import { expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, provide, ref } from 'vue'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import { nativeBusinessKey, useNativeBusinessApi, useNativeBusinessDialog, useNativeBusinessEntry,
  type NativeBusinessContext } from './nativeBusinessContext'

it('retains an open native dialog when the shared form refuses to discard unfinished input', async () => {
  const visible = ref(true), saving = ref(false), requestLeave = vi.fn(async () => false)
  let dialog!: ReturnType<typeof useNativeBusinessDialog>
  const Content = defineComponent({ setup() {
    dialog = useNativeBusinessDialog('training', visible, () => saving.value)
    return () => h('dialog', { open: dialog.value })
  } })
  const mounted = mount(defineComponent({ setup() {
    provide(nativeBusinessKey, { requestLeave } as unknown as NativeBusinessContext)
    return () => h(Content)
  } }))
  try {
    dialog.value = false
    await nextTick(); await nextTick()
    expect(visible.value).toBe(true)
    expect(requestLeave).toHaveBeenCalledWith('training')
    requestLeave.mockResolvedValue(true)
    saving.value = true
    dialog.value = false
    await nextTick()
    expect(visible.value).toBe(true)
    expect(requestLeave).toHaveBeenCalledTimes(1)
    saving.value = false
    dialog.value = false
    await nextTick(); await nextTick()
    expect(visible.value).toBe(false)
  } finally { mounted.app.unmount() }
})

it('opens the task-associated record losslessly and leaves the original route API intact without a host', async () => {
  const open = vi.fn(async (_id: unknown) => undefined)
  const original = { save: vi.fn() }
  let unhostedApi: unknown
  const Plain = defineComponent({ setup() { unhostedApi = useNativeBusinessApi('arrival', original); return () => h('span') } })
  const plain = mount(Plain)
  expect(unhostedApi).toBe(original)
  plain.app.unmount()
  const Content = defineComponent({ setup() { useNativeBusinessEntry('arrival', open); return () => h('span') } })
  const mounted = mount(defineComponent({ setup() {
    provide(nativeBusinessKey, { initialEntityId: kind => kind === 'arrival' ? '2102228462045130754' : undefined } as NativeBusinessContext)
    return () => h(Content)
  } }))
  try { await nextTick(); expect(open).toHaveBeenCalledWith('2102228462045130754') }
  finally { mounted.app.unmount() }
})
