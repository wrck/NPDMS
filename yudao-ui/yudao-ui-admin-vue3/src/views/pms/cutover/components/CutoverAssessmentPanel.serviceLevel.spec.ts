import { expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Panel from './CutoverAssessmentPanel.vue'
import { mount, passthrough, textOf, findByTestId } from '../cutoverTestHarness'
vi.mock('@/utils/dict', () => ({ getStrDictOptions: (type: string) => type === 'pms_service_level' ? [{ value: 'GOLD', label: '金牌' }, { value: 'SILVER', label: '银牌' }] : [] }))
const render = (status: string, editable = true, manual: string | null = null) => {
 const model = { answers: {}, manualGrade: null, manualCustomerServiceLevelCode: manual }
 const view = mount(Panel, { model, customerServiceLevel: { status, serviceLevelCode: status === 'AVAILABLE' ? 'GOLD' : null }, editable, submittable: true, saving: false, submitting: false }, { ElForm: passthrough, ElSelect: passthrough, ElOption: passthrough, ElRadioGroup: passthrough, ElRadio: passthrough })
 return { ...view, model }
}
it('displays the dictionary label for an available customer fact without allowing manual replacement', () => {
 const view = render('AVAILABLE'); expect(textOf(view.root)).toContain('金牌'); expect(findByTestId(view.root, 'manual-customer-service-level')).toBeUndefined(); view.app.unmount()
})
it('starts unconfigured manual choice empty and updates only the assessment choice', async () => {
 const view = render('NOT_CONFIGURED'); expect(view.model.manualCustomerServiceLevelCode).toBeNull()
 const select = findByTestId(view.root, 'manual-customer-service-level')!
 ;(select.props!['onUpdate:modelValue'] as Function)('SILVER'); await nextTick()
 expect(view.model.manualCustomerServiceLevelCode).toBe('SILVER'); view.app.unmount()
})
it('does not allow a manual choice when the source is unavailable', () => {
 const view = render('UNAVAILABLE'); expect(findByTestId(view.root, 'manual-customer-service-level')).toBeUndefined(); expect(textOf(view.root)).toContain('不能手动替代'); view.app.unmount()
})
it('shows the saved manual dictionary label in submitted history', () => {
 const view = render('NOT_CONFIGURED', false, 'SILVER'); expect(textOf(view.root)).toContain('银牌'); expect(findByTestId(view.root, 'manual-customer-service-level')).toBeUndefined(); view.app.unmount()
})
