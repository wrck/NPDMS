import { describe, expect, it } from 'vitest'
import v1Source from '@/components/LowCodeFormRenderer/index.vue?raw'
import facadeSource from '@/components/LowCodeFormRendererFacade/index.vue?raw'
import designerSource from '@/views/lowcode/form-designer/index.vue?raw'
import runtimeSource from '@/views/lowcode/render/index.vue?raw'
import tabSource from '@/components/LowCodeTabRenderer/index.vue?raw'
import relatedPageSource from '@/components/LowCodeRelatedPageRenderer/index.vue?raw'
import listSource from '@/components/LowCodeListRenderer/index.vue?raw'

/**
 * “V1 无回归 + V2 全链路可替换”结构门禁。
 *
 * 这组测试不验证 Vue 视觉细节，而是锁住架构边界：
 * 1. V1 的稳定公开契约仍存在，且 V1 不依赖 FormCreate/V2；
 * 2. 业务消费方只能依赖 Facade，不能直接选择具体渲染器；
 * 3. 正式运行页必须通过 Facade submit 契约触发校验后持久化。
 */
describe('LowCodeFormRenderer acceptance gate', () => {
  it('keeps the V1 public contract independent from V2/FormCreate', () => {
    expect(v1Source).toContain("defineProps<{\n    config: FormConfig")
    expect(v1Source).toContain("(e: 'update:modelValue'")
    expect(v1Source).toContain("(e: 'submit'")
    expect(v1Source).toContain("(e: 'validate-fail'")
    expect(v1Source).toContain("(e: 'field-change'")
    expect(v1Source).toContain('defineExpose({')
    expect(v1Source).toContain('validate,')
    expect(v1Source).toContain('submit,')
    expect(v1Source).toContain('resetFields,')
    expect(v1Source).toContain('clearValidate,')
    expect(v1Source).toContain('getFormData:')
    expect(v1Source).not.toContain('@form-create/element-ui')
    expect(v1Source).not.toContain('LowCodeFormRendererV2')
  })

  it('keeps version routing inside the facade', () => {
    expect(facadeSource).toContain("LowCodeFormRenderer from '@/components/LowCodeFormRenderer/index.vue'")
    expect(facadeSource).toContain("LowCodeFormRendererV2 from '@/components/LowCodeFormRendererV2/index.vue'")
    expect(facadeSource).toContain('resolveLowCodeFormRendererVersion')
    expect(facadeSource).toContain('effectiveRendererVersion')
  })

  it('routes designer preview and generic runtime through the facade', () => {
    expect(designerSource).toContain("LowCodeFormRendererFacade from '@/components/LowCodeFormRendererFacade/index.vue'")
    expect(designerSource).toContain('<LowCodeFormRendererFacade')

    expect(runtimeSource).toContain("import('@/components/LowCodeFormRendererFacade/index.vue')")
    expect(runtimeSource).not.toContain("import('@/components/LowCodeFormRenderer/index.vue')")
    expect(runtimeSource).not.toContain("import('@/components/LowCodeFormRendererV2/index.vue')")
  })

  it('routes nested form consumers through the facade', () => {
    expect(tabSource).toContain("import('@/components/LowCodeFormRendererFacade/index.vue')")
    expect(relatedPageSource).toContain("import('@/components/LowCodeFormRendererFacade/index.vue')")
  })

  it('keeps list form operations route-based instead of embedding a concrete renderer', () => {
    expect(listSource).not.toContain("LowCodeFormRenderer/index.vue")
    expect(listSource).not.toContain("LowCodeFormRendererV2/index.vue")
  })

  it('uses the facade submit contract in the formal runtime save path', () => {
    expect(runtimeSource).toContain('await rendererRef.value.submit()')
    expect(runtimeSource).toContain('@submit="handleFormSubmit"')
    expect(runtimeSource).not.toContain('await handleFormSubmit()')
  })
})
