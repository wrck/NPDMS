// @vitest-environment happy-dom
import { expect, it, vi } from 'vitest'
import { createApp, nextTick } from 'vue'
import Pagination from './index.vue'
import { passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/store/modules/app', () => ({ useAppStore: () => ({ currentSize: 'default' }) }))
it('globally emits positive total for all and restores ordinary paging without opt-in', async () => {
  const events: any[] = []
  const app = createApp(Pagination, { total: 401, page: 2, limit: 20, pagerCount: 7,
    'onUpdate:limit': (limit: number) => { (m.vm as any).$.props.limit = limit },
    'onUpdate:page': (page: number) => { (m.vm as any).$.props.page = page },
    onPagination: (event: any) => events.push(event)
  })
  app.component('ElSelect', passthrough); app.component('ElOption', passthrough); app.component('ElPagination', passthrough)
  const m = { app, vm: app.mount(document.createElement('div')) }
  const state = (m.vm as any).$.setupState
  try {
    state.selectLimit('all'); await nextTick()
    expect(events.at(-1)).toEqual({ page: 1, limit: 401 })
    expect(state.showingAll).toBe(true)
    ;(m.vm as any).$.props.total = 402; await nextTick(); await nextTick()
    expect(events.at(-1)).toEqual({ page: 1, limit: 402 })
    state.selectLimit(10); await nextTick()
    expect(state.showingAll).toBe(false)
    expect(events.at(-1)).toEqual({ page: 1, limit: 10 })
  } finally { m.app.unmount() }
})
