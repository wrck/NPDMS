import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive } from 'vue'
import { useDraftLeaveGuard } from '@/hooks/web/useDraftLeaveGuard'
import { registerDraftUpload, useDraftUploads } from '@/hooks/web/useDraftUploads'
import DraftLeaveDialog from '@/components/DraftLeaveDialog/index.vue'
import { mount, findByTestId } from '../cutoverTestHarness'

const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
const dialog = defineComponent({ setup(_, { slots }) { return () => h('section', [slots.default?.(), slots.footer?.()]) } })
const fixture = (save = vi.fn(async () => false)) => {
  const state = reactive({ text: 'unsaved', busy: false })
  let guard!: ReturnType<typeof useDraftLeaveGuard>
  const warnBusy = vi.fn()
  const discard = vi.fn(() => { state.text = 'saved' })
  const page = mount(defineComponent({ setup() {
    guard = useDraftLeaveGuard({ hasPending: () => state.text !== 'saved', stateKey: () => state.text,
      isBusy: () => state.busy, warnBusy, save, discard })
    return () => guard.leaveDialogVisible.value ? h(DraftLeaveDialog, { onDecision: guard.resolveLeaveDecision }) : null
  } }), {}, { ElDialog: dialog })
  const decide = async (decision: string) => {
    const button = findByTestId(page.root, `draft-leave-${decision}`)
    expect(button).toBeDefined()
    ;(button!.props!.onClick as Function)()
    await flush()
  }
  return { page, state, guard, save, discard, warnBusy, decide }
}

describe('failed draft leave recovery', () => {
  it('keeps edits after a thrown save and lets the user stay', async () => {
    const f = fixture(vi.fn(async () => { throw new Error('validation failed') }))
    const leaving = f.guard.requestLeave(); await flush()
    await f.decide('stay')
    expect(await leaving).toBe(false)
    expect(f.state.text).toBe('unsaved')
    expect(f.discard).not.toHaveBeenCalled()
    f.page.app.unmount()
  })
  it('retries through the same save operation and then leaves', async () => {
    const f = fixture()
    f.save.mockImplementationOnce(async () => false).mockImplementationOnce(async () => { f.state.text = 'saved'; return true })
    const leaving = f.guard.requestLeave(); await flush()
    await f.decide('retry')
    expect(await leaving).toBe(true)
    expect(f.save).toHaveBeenCalledTimes(2)
    expect(f.discard).not.toHaveBeenCalled()
    f.page.app.unmount()
  })
  it('deduplicates exit requests and discards without saving again at the route guard', async () => {
    const f = fixture()
    const leaving = f.guard.requestLeave()
    expect(f.guard.requestLeave()).toBe(leaving)
    await flush(); await f.decide('discard')
    expect(await leaving).toBe(true)
    expect(await f.guard.requestLeave()).toBe(true)
    expect(f.save).toHaveBeenCalledOnce()
    expect(f.discard).toHaveBeenCalledOnce()
    f.state.text = 'another edit'
    const again = f.guard.requestLeave(); await flush(); await f.decide('stay')
    expect(await again).toBe(false)
    expect(f.save).toHaveBeenCalledTimes(2)
    f.page.app.unmount()
  })
  it('keeps real in-flight writes protected without offering discard', async () => {
    const f = fixture(); f.state.busy = true
    expect(await f.guard.requestLeave()).toBe(false)
    expect(f.warnBusy).toHaveBeenCalledOnce()
    expect(f.guard.leaveDialogVisible.value).toBe(false)
    expect(f.save).not.toHaveBeenCalled()
    f.page.app.unmount()
  })
  it('shows the dialog again after a retry fails and resolves on unmount', async () => {
    const f = fixture(); const leaving = f.guard.requestLeave(); await flush()
    await f.decide('retry')
    expect(f.guard.leaveDialogVisible.value).toBe(true)
    f.page.app.unmount()
    expect(await leaving).toBe(false)
  })
  it('does not open an orphaned dialog when a save fails after its page was unmounted', async () => {
    let finish!: (value: boolean) => void
    const f = fixture(vi.fn(() => new Promise<boolean>(resolve => { finish = resolve })))
    const leaving = f.guard.requestLeave()
    f.page.app.unmount()
    finish(false)
    expect(await leaving).toBe(false)
    expect(f.guard.leaveDialogVisible.value).toBe(false)
  })
  it('acknowledges discard even when source autofill would still mark the buffer dirty', async () => {
    const f = fixture(); f.discard.mockImplementation(() => undefined)
    const leaving = f.guard.requestLeave(); await flush(); await f.decide('discard')
    expect(await leaving).toBe(true)
    expect(f.guard.hasPendingDraft()).toBe(false)
    expect(await f.guard.requestLeave()).toBe(true)
    expect(f.save).toHaveBeenCalledOnce()
    f.page.app.unmount()
  })
  it('tracks nested failed uploads separately from active transfers and removes unmounted uploaders', async () => {
    const state = reactive({ pending: true, busy: false, show: true })
    const discard = vi.fn(() => { state.pending = false })
    let uploads!: ReturnType<typeof useDraftUploads>
    const Child = defineComponent({ setup() {
      registerDraftUpload({ isBusy: () => state.busy, hasPendingFile: () => state.pending, discard })
      return () => null
    } })
    const page = mount(defineComponent({ setup() {
      uploads = useDraftUploads()
      return () => state.show ? h(Child) : null
    } }))
    expect(uploads.pending.value).toBe(true)
    expect(uploads.busy.value).toBe(false)
    state.busy = true; uploads.discard()
    expect(discard).not.toHaveBeenCalled()
    state.busy = false; uploads.discard()
    expect(uploads.pending.value).toBe(false)
    state.pending = true; state.show = false; await nextTick()
    expect(uploads.pending.value).toBe(false)
    page.app.unmount()
  })
})
