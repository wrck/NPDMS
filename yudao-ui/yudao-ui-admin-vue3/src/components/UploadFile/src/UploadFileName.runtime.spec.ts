import { expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import UploadFile from './UploadFile.vue'
import { mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('./useUpload', () => ({ useUpload: () => ({ uploadUrl: '/unused', httpRequest: vi.fn() }) }))

it('shows a decoded filename without exposing a signed URL query as its label', async () => {
  const url = 'http://127.0.0.1:29000/test/%E7%AD%BE%E6%94%B6.txt?X-Amz-Signature=test-signature'
  const mounted = mount(UploadFile, { modelValue: url, disabled: true }, { ElLink: passthrough })
  try {
    await nextTick()
    expect(textOf(mounted.root)).toContain('签收.txt')
    expect(textOf(mounted.root)).not.toContain('X-Amz-Signature')
    expect((mounted.vm as any).$.setupState.fileList[0].url).toBe(url)
  } finally { mounted.app.unmount() }
})

it('keeps the original filename for each URL in an array', async () => {
  const mounted = mount(UploadFile, { modelValue: ['/files/one.txt?token=test-only', '/files/two.pdf#preview'], disabled: true }, { ElLink: passthrough })
  try {
    await nextTick()
    expect(textOf(mounted.root)).toContain('one.txt')
    expect(textOf(mounted.root)).toContain('two.pdf')
    expect(textOf(mounted.root)).not.toContain('token=')
    expect(textOf(mounted.root)).not.toContain('#preview')
  } finally { mounted.app.unmount() }
})
