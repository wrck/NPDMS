import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { generateUUID } from '@/utils'
import { useMessage } from '@/hooks/web/useMessage'
import { getSimpleDeptList, type DeptVO } from '@/api/system/dept'
import * as api from '@/api/pms/project/project-splits/workspace'
import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'

export const splitErrorMessage = (error: unknown) => error instanceof Error ? error.message
  : typeof error === 'string' && error !== 'error' ? error : '操作未完成，请查看错误通知中的原因后重试'

/** 从原拆分向导复制增强，继续使用原有草稿、预览、事务应用契约。 */
export const useProjectSplitWorkspace = (projectId: Ref<number>, applied: () => void) => {
  const message = useMessage()
  const items = ref<api.SplitItem[]>([])
  const draft = ref<api.SplitDraft>()
  const preview = ref<api.ProjectSplitPreviewVO>()
  const result = ref<api.SplitResult>()
  const scopeOptions = ref<api.SplitScopeOption[]>([])
  const departments = ref<DeptVO[]>([])
  const restoring = ref(false), saving = ref(false), previewing = ref(false), applying = ref(false)
  const loadingOptions = ref(false), optionsError = ref(''), error = ref(''), restoreFailed = ref(false)
  const savedFingerprint = ref('')
  const parentScopeChanged = ref(false)
  const submission = createSubmissionIdempotencyState()
  let generation = 0, optionsGeneration = 0
  const storageKey = () => `fproj002:split-request:${projectId.value}`
  const clean = (value?: string) => value?.trim() || undefined
  const payload = (): api.SplitInput => ({ parentProjectId: projectId.value, items: items.value.map(item => ({
    ...item, projectName: item.projectName.trim(), businessLevelCode: clean(item.businessLevelCode),
    departmentCode: clean(item.departmentCode), templateSelectionReason: clean(item.templateSelectionReason),
    scopes: item.scopes.map(scope => ({ ...scope, departmentCode: clean(scope.departmentCode),
      serialNumbers: scope.serialNumbers.map(serial => serial.trim()).filter(Boolean) }))
  })) })
  const dirty = computed(() => parentScopeChanged.value || JSON.stringify(payload()) !== savedFingerprint.value)
  const appliedAlready = computed(() => draft.value?.status === 'APPLIED' || !!result.value)
  const busy = computed(() => restoring.value || saving.value || previewing.value || applying.value)
  const newScope = (): api.SplitScope => ({ quantity: 1, serialNumbers: [] })
  const addItem = () => items.value.push({ clientItemKey: generateUUID(), projectName: '', scopes: [newScope()] })
  watch(items, () => { preview.value = undefined }, { deep: true, flush: 'sync' })

  const loadOptions = async () => {
    const current = ++optionsGeneration, id = projectId.value
    loadingOptions.value = true; optionsError.value = ''
    try {
      const [scopes, offices] = await Promise.all([api.getScopeOptions(id), getSimpleDeptList()])
      if (current !== optionsGeneration) return
      scopeOptions.value = scopes
      departments.value = offices.filter(office => office.code && office.status !== 1)
    } catch (failure) {
      if (current === optionsGeneration) optionsError.value = `订单范围或办事处读取失败：${splitErrorMessage(failure)}`
    } finally { if (current === optionsGeneration) loadingOptions.value = false }
  }
  const refreshParentScope = () => {
    parentScopeChanged.value = true; preview.value = undefined
    void loadOptions()
  }

  const restore = async () => {
    const current = ++generation, id = projectId.value
    restoring.value = true; saving.value = false; previewing.value = false; applying.value = false
    draft.value = undefined; preview.value = undefined; result.value = undefined; items.value = []
    error.value = ''; restoreFailed.value = false; parentScopeChanged.value = false; submission.reset()
    const requestId = localStorage.getItem(storageKey())
    try {
      if (requestId) {
        const restored = await api.getDraft(requestId)
        if (current !== generation) return
        if (String(restored.parentProjectId) !== String(id)) throw new Error('草稿不属于当前父项目')
        draft.value = restored; items.value = api.restoreItems(restored)
      }
      if (!items.value.length) addItem()
      savedFingerprint.value = JSON.stringify(payload())
    } catch (failure) {
      if (current === generation) {
        restoreFailed.value = true
        error.value = `草稿未能恢复，已保留草稿引用。${splitErrorMessage(failure)}`
      }
    } finally { if (current === generation) restoring.value = false }
  }

  const validateLocal = () => {
    if (!items.value.length) return '请新增至少一个子项目'
    if (items.value.some(item => !item.projectName.trim())) return '请填写每个子项目的名称'
    if (items.value.some(item => !item.templateRevisionId)) return '请为每个子项目选择模板'
    if (items.value.some(item => !item.scopes.length || item.scopes.some(scope => !scope.orderLineId
      || !Number.isFinite(scope.quantity) || scope.quantity <= 0))) return '请选择订单范围并填写有效数量'
    if (items.value.some(item => item.scopes.some(scope => scope.serialNumbers.length > 0
      && scope.serialNumbers.length !== scope.quantity))) return '按序列号拆分时，数量必须与序列号个数一致'
  }
  const saveDraft = async (): Promise<boolean> => {
    if (busy.value || appliedAlready.value || restoreFailed.value) return false
    const invalid = validateLocal()
    if (invalid) { message.warning(invalid); return false }
    saving.value = true; error.value = ''
    const current = generation, input = payload(), fingerprint = JSON.stringify(input)
    try {
      const saved = draft.value ? await api.updateDraft(draft.value, input, generateUUID())
        : await api.createDraft(input, generateUUID())
      if (current !== generation) return false
      draft.value = saved; savedFingerprint.value = fingerprint; parentScopeChanged.value = false; preview.value = undefined
      localStorage.setItem(storageKey(), String(saved.id))
      message.success('拆分草稿已保存，可刷新后继续编辑')
      return true
    } catch (failure) {
      if (current === generation) error.value = splitErrorMessage(failure)
      return false
    } finally { if (current === generation) saving.value = false }
  }
  const previewDraft = async () => {
    if (busy.value || appliedAlready.value || restoreFailed.value) return
    const current = generation
    if ((!draft.value || dirty.value) && !(await saveDraft())) return
    if (!draft.value || current !== generation) return
    previewing.value = true; error.value = ''
    try {
      const value = await api.previewDraft(draft.value, generateUUID())
      if (current !== generation || dirty.value) return
      preview.value = value
      draft.value = { ...draft.value, parentVersion: value.parentVersion, scopeVersion: value.scopeVersion, treeVersion: value.treeVersion }
    } catch (failure) { if (current === generation) error.value = splitErrorMessage(failure) }
    finally { if (current === generation) previewing.value = false }
  }
  const applyDraft = async () => {
    if (busy.value || appliedAlready.value || dirty.value || !draft.value || !preview.value?.valid) return
    applying.value = true; error.value = ''
    const current = generation
    const command = { id: draft.value.id, draftVersion: draft.value.draftVersion, parentVersion: draft.value.parentVersion,
      scopeVersion: draft.value.scopeVersion, treeVersion: draft.value.treeVersion }
    try {
      const appliedResult = await api.applyDraft(draft.value, submission.keyFor(command))
      if (current !== generation) return
      result.value = appliedResult; draft.value = { ...draft.value, status: 'APPLIED' }
      localStorage.removeItem(storageKey())
      message.success(`已创建 ${appliedResult.projects.length} 个子项目`)
      applied(); void loadOptions()
    } catch (failure) { if (current === generation) error.value = splitErrorMessage(failure) }
    finally { if (current === generation) applying.value = false }
  }
  const startNew = async () => {
    if (busy.value) return
    localStorage.removeItem(storageKey())
    await restore()
  }
  watch(projectId, () => { scopeOptions.value = []; departments.value = []; void restore(); void loadOptions() }, { immediate: true })
  onBeforeUnmount(() => { generation++; optionsGeneration++ })
  return { items, draft, preview, result, scopeOptions, departments, dirty, appliedAlready, busy,
    saving, previewing, applying, restoring, loadingOptions, optionsError, error, restoreFailed,
    addItem, newScope, loadOptions, refreshParentScope, restore, saveDraft, previewDraft, applyDraft, startNew }
}
