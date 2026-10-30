<template>
  <section class="split-workspace" aria-label="项目拆分工作区">
    <header class="workspace-heading">
      <div><h3>项目拆分</h3><p>划分交付范围，为每个子项目独立设置办事处与模板。</p></div>
      <el-tag :type="appliedAlready ? 'success' : preview?.valid && !dirty ? 'success' : 'info'" effect="plain">
        {{ appliedAlready ? '已完成' : preview?.valid && !dirty ? '可创建' : draft && !dirty ? '草稿已保存' : '编辑中' }}
      </el-tag>
    </header>
    <ol class="split-steps" aria-label="拆分流程">
      <li v-for="(label, index) in ['编辑方案', '校验范围', '创建子项目']" :key="label"
        :class="{ active: currentStep === index, complete: currentStep > index }" :aria-current="currentStep === index ? 'step' : undefined">
        <span class="step-mark">{{ currentStep > index ? '✓' : index + 1 }}</span><span>{{ label }}</span>
      </li>
    </ol>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="status-alert" />
    <div v-if="restoreFailed" class="actions">
      <el-button @click="restore">重试恢复草稿</el-button>
      <el-button @click="startNew">保留原草稿，开始新方案</el-button>
    </div>
    <el-skeleton v-if="restoring" :rows="5" animated />
    <template v-else-if="!restoreFailed">
      <template v-if="appliedAlready">
        <div class="completed-heading"><span class="completed-mark">✓</span><div><h4>子项目已创建</h4><p>交付范围已转入对应子项目，可分别进入并继续办理。</p></div></div>
        <ul v-if="result" class="created-projects">
          <li v-for="child in result.projects" :key="child.projectId">
            <RouterLink :to="{ path: '/pms/project-management/project-master-detail', query: { projectId: String(child.projectId) } }">
              <span><strong>{{ items.find(item => item.clientItemKey === child.clientItemKey)?.projectName }}</strong><small>{{ child.projectCode }}</small></span>
              <span class="open-child">查看子项目</span>
            </RouterLink>
          </li>
        </ul>
        <div class="completion-footer"><span>可在项目树和进度汇总查看最新关系</span><el-button @click="startNew">继续拆分</el-button></div>
      </template>
      <template v-else>
        <div class="parent-scope">
          <div><strong>父项目订单范围</strong><p>{{ loadingOptions ? '正在读取可分配范围' : `${scopeOptions.length} 个可选订单行` }}</p></div>
          <div class="scope-actions">
            <el-button v-hasPermi="['pms:commerce:scope:query']" :loading="loadingScopeContext" :disabled="busy" @click="openScopeManagement">查看订单范围</el-button>
            <el-button :loading="loadingOptions" :disabled="busy" text @click="loadOptions">刷新</el-button>
          </div>
        </div>
        <el-alert v-if="optionsError" :title="optionsError" type="error" :closable="false" class="status-alert" />
        <el-alert v-else-if="!loadingOptions && !scopeOptions.length" title="当前没有可拆分范围。请查看订单范围并核对合同订单或父项目拆分结果。" type="warning" :closable="false" class="status-alert" />
        <div class="section-heading"><h4>子项目配置 <span>{{ items.length }}</span></h4><el-button :disabled="busy" text type="primary" @click="addItem">＋ 新增子项目</el-button></div>
        <div class="split-list">
          <article v-for="(item, index) in items" :key="item.clientItemKey" class="split-card">
            <header class="child-heading"><div><span class="branch-mark" aria-hidden="true"></span><strong>子项目 {{ index + 1 }}</strong></div><el-button link type="danger" :disabled="busy" :aria-label="`移除子项目 ${index + 1}`" @click="items.splice(index, 1)">移除</el-button></header>
            <el-form label-position="top" :disabled="busy">
              <div class="item-fields">
                <el-form-item label="项目名称" required class="project-name-field"><el-input v-model="item.projectName" maxlength="255" placeholder="按区域、站点或交付范围命名" :aria-label="`子项目 ${index + 1} 名称`" /></el-form-item>
                <el-form-item label="所属办事处">
                  <el-select v-model="item.departmentCode" filterable clearable placeholder="沿用父项目办事处" :aria-label="`子项目 ${index + 1} 办事处`" :loading="loadingOptions">
                    <el-option v-for="office in departments" :key="office.id" :value="office.code" :label="`${office.name}（${office.code}）`" />
                  </el-select>
                </el-form-item>
                <el-form-item label="业务层级标签"><el-input v-model="item.businessLevelCode" maxlength="64" placeholder="可选，如区域、站点" :aria-label="`子项目 ${index + 1} 业务层级`" /></el-form-item>
              </div>
              <el-form-item label="子项目模板" required>
                <ProjectSplitTemplatePicker :parent-project-id="projectId" :project-name="item.projectName" :business-level-code="item.businessLevelCode"
                  :department-code="item.departmentCode" :revision-id="item.templateRevisionId" :disabled="busy"
                  @change="selectTemplate(item, $event)" />
              </el-form-item>
              <el-form-item v-if="item.templateRevisionId" label="模板选择说明"><el-input v-model="item.templateSelectionReason" maxlength="512" placeholder="选择非匹配模板时填写原因" :aria-label="`子项目 ${index + 1} 模板选择说明`" /></el-form-item>
              <div class="scope-heading"><strong>订单范围</strong><span>{{ item.scopes.length }} 项组合</span></div>
              <section v-for="(scope, scopeIndex) in item.scopes" :key="scopeIndex" class="scope-row" :aria-label="`子项目 ${index + 1} 范围 ${scopeIndex + 1}`">
                <div class="scope-row-heading"><span>范围 {{ scopeIndex + 1 }}</span><el-button link type="danger" :disabled="busy || item.scopes.length === 1" :aria-label="`删除子项目 ${index + 1} 范围 ${scopeIndex + 1}`" @click="item.scopes.splice(scopeIndex, 1)">删除</el-button></div>
                <div class="scope-fields">
                  <el-form-item label="订单行" required class="order-line-field">
                    <el-select v-model="scope.orderLineId" filterable placeholder="按订单号、产品搜索" :aria-label="`子项目 ${index + 1} 订单范围 ${scopeIndex + 1}`" :loading="loadingOptions" @change="scope.serialNumbers = []">
                      <el-option v-if="scope.orderLineId && !optionFor(scope)" :value="scope.orderLineId" :label="`原草稿订单行 ${scope.orderLineId}（当前不可用）`" disabled />
                      <el-option v-for="option in scopeOptions" :key="option.orderLineId" :value="option.orderLineId" :label="`${option.label} · 父项目范围 ${option.quantity}`" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="拆分数量" required>
                    <el-input-number v-model="scope.quantity" :min="0.0001" :precision="4" :disabled="busy || scope.serialNumbers.length > 0" :aria-label="`子项目 ${index + 1} 拆分数量 ${scopeIndex + 1}`" />
                  </el-form-item>
                </div>
                <details class="serial-options" :open="scope.serialNumbers.length > 0">
                  <summary>指定设备序列号 <span>{{ scope.serialNumbers.length ? `已选 ${scope.serialNumbers.length} 个` : '可选' }}</span></summary>
                  <p>按设备拆分时选择或输入序列号，数量将与序列号个数一致。</p>
                  <el-select v-model="scope.serialNumbers" multiple filterable allow-create default-first-option :reserve-keyword="false"
                    placeholder="选择或输入序列号" :aria-label="`子项目 ${index + 1} 序列号 ${scopeIndex + 1}`" @change="updateSerialQuantity(scope)">
                    <el-option v-for="serial in optionFor(scope)?.serialNumbers || []" :key="serial" :label="serial" :value="serial" />
                  </el-select>
                </details>
              </section>
              <el-button link type="primary" @click="item.scopes.push(newScope())">＋ 添加订单范围</el-button>
            </el-form>
          </article>
        </div>
        <el-empty v-if="!items.length" description="请添加要创建的子项目" />
        <el-button :disabled="busy" class="add-child" @click="addItem">＋ 新增子项目</el-button>
        <section v-if="preview && !dirty" class="preview" aria-live="polite">
          <h4>确认拆分内容</h4>
          <el-alert :title="preview.valid ? `校验通过，将创建 ${items.length} 个子项目` : '方案未通过校验，请按提示修正后重新预览'" :type="preview.valid ? 'success' : 'error'" :closable="false" />
          <ul v-if="!preview.valid" class="validation-errors"><li v-for="item in preview.errors" :key="item">{{ validationLabel(item) }}</li></ul>
          <div v-for="item in items" :key="item.clientItemKey" class="preview-item">
            <strong>{{ item.projectName }}</strong>
            <span>{{ departmentLabel(item.departmentCode) }} · {{ item.scopes.length }} 项范围</span>
            <span v-for="(scope, scopeIndex) in item.scopes" :key="scopeIndex">{{ optionFor(scope)?.label || '原草稿订单范围' }}：{{ scope.quantity }}{{ scope.serialNumbers.length ? `（${scope.serialNumbers.join('、')}）` : '' }}</span>
          </div>
        </section>
        <div class="footer-actions">
          <div class="save-state"><strong>{{ items.length }} 个子项目</strong><span>{{ draft && !dirty ? '草稿已保存' : '当前方案尚未保存' }}</span></div>
          <div class="footer-buttons"><el-button :disabled="busy" :loading="saving" @click="saveDraft">保存草稿</el-button>
            <el-button :type="preview?.valid && !dirty ? 'default' : 'primary'" :disabled="busy" :loading="previewing" @click="previewDraft">{{ preview ? '重新预览' : '生成预览' }}</el-button>
            <el-button :type="preview?.valid && !dirty ? 'primary' : 'default'" :disabled="busy || !preview?.valid || dirty" :loading="applying" @click="applyDraft">确认创建子项目</el-button></div>
        </div>
      </template>
    </template>
  </section>
  <el-drawer v-model="scopeManagementVisible" class="parent-scope-drawer" title="父项目订单范围" size="min(880px, 100%)" destroy-on-close
    :before-close="closeScopeManagement" :close-on-click-modal="false" @closed="loadOptions">
    <ProjectParentScopePanel v-if="scopeContext" :project-context="scopeContext" :is-root="!scopeProject?.parentId" :project-code="scopeProject?.projectCode"
      :project-name="scopeProject?.projectName" :department-name="scopeProject?.departmentName" @changed="refreshParentScope" @busy="scopeOperationBusy = $event" />
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, toRef, watch } from 'vue'
import type { LongId, SplitItem, SplitScope } from '@/api/pms/project/project-splits/workspace'
import * as ProjectsApi from '@/api/pms/project/projects'
import type { ProjectRouteContext } from '@/views/pms/commerce/commerceInteraction'
import ProjectParentScopePanel from './ProjectParentScopePanel.vue'
import ProjectSplitTemplatePicker from './ProjectSplitTemplatePicker.vue'
import { splitErrorMessage, useProjectSplitWorkspace } from './useProjectSplitWorkspace'
const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{ applied: [] }>()
const { items, draft, preview, result, scopeOptions, departments, dirty, appliedAlready, busy,
  saving, previewing, applying, restoring, loadingOptions, optionsError, error, restoreFailed,
  addItem, newScope, loadOptions, refreshParentScope, restore, saveDraft, previewDraft, applyDraft, startNew } = useProjectSplitWorkspace(toRef(props, 'projectId'), () => emit('applied'))
const currentStep = computed(() => appliedAlready.value ? 3 : preview.value?.valid && !dirty.value ? 2 : draft.value && !dirty.value ? 1 : 0)
const loadingScopeContext = ref(false), scopeManagementVisible = ref(false)
const scopeContext = ref<ProjectRouteContext>()
const scopeProject = ref<ProjectsApi.ProjectMasterVO>(), scopeOperationBusy = ref(false)
const closeScopeManagement = (done: () => void) => { if (!scopeOperationBusy.value) done() }
const openScopeManagement = async () => {
  if (loadingScopeContext.value) return
  const id = props.projectId
  loadingScopeContext.value = true
  try {
    const [project, tree] = await Promise.all([
      ProjectsApi.getProject(id), ProjectsApi.queryTree(id, { queryType: 'LOCATE', pageSize: 1 })
    ])
    if (id !== props.projectId) return
    if (tree.updating || project.version == null) throw new Error('项目关系正在更新，请稍后重试')
    scopeContext.value = { projectId: id, projectVersion: project.version, projectScopeVersion: tree.treeVersion }
    scopeProject.value = project
    scopeManagementVisible.value = true
  } catch (failure) { if (id === props.projectId) error.value = splitErrorMessage(failure) }
  finally { loadingScopeContext.value = false }
}
watch(() => props.projectId, () => { scopeManagementVisible.value = false; scopeContext.value = undefined })
const selectTemplate = (item: SplitItem, revisionId: LongId) => { item.templateRevisionId = revisionId; item.templateSelectionReason = '' }
const optionFor = (scope: SplitScope) => scopeOptions.value.find(option => option.orderLineId === scope.orderLineId)
const departmentLabel = (code?: string) => departments.value.find(office => office.code === code)?.name || code || '沿用父项目办事处'
const updateSerialQuantity = (scope: SplitScope) => { if (scope.serialNumbers.length) scope.quantity = scope.serialNumbers.length }
const validationLabels: Record<string, string> = {
  CHILD_TEMPLATE_OVERRIDE_FORBIDDEN: '没有模板匹配覆盖权限，请选择匹配推荐模板',
  CHILD_TEMPLATE_REASON_REQUIRED: '选择非匹配模板时需要填写原因',
  CHILD_TEMPLATE_NOT_SELECTABLE: '模板不可用，请重新选择已发布模板',
  DEPARTMENT_CODE_INVALID: '办事处无效或已停用，请重新选择',
  PARENT_VERSION_CONFLICT: '父项目已变化，请保存草稿后重新预览',
  TREE_VERSION_CONFLICT: '项目树已变化，请保存草稿后重新预览',
  SCOPE_VERSION_CONFLICT: '订单范围已变化，请刷新可选范围并保存草稿后重新预览',
  COMMERCE_SCOPE_UNAVAILABLE: '订单范围暂不可用，请稍后重试',
  ASSET_SCOPE_UNAVAILABLE: '设备范围暂不可用，请稍后重试',
  PARENT_SCOPE_EXCEEDED: '拆分数量超过父项目范围',
  OVER_ALLOCATION: '拆分数量超过父项目范围', ORDER_LINE_NOT_AVAILABLE: '订单行不可用，请刷新可选范围',
  UNIT_PRECISION_INVALID: '数量精度不符合订单计量单位', DUPLICATE_ALLOCATION: '子项目中存在重复的订单范围组合',
  SERIAL_QUANTITY_MISMATCH: '序列号个数与拆分数量不一致', AST_SERIAL_NOT_ASSIGNABLE: '设备序列号当前不可分配',
  SERIAL_MISSING: '序列号不存在', SERIAL_UNAVAILABLE: '序列号不在可分配范围', DUPLICATE_SERIAL: '序列号重复分配'
}
const validationLabel = (value: string) => {
  const [code, reference] = value.split(':')
  const item = items.value.find(item => item.clientItemKey === reference)
  const scope = scopeOptions.value.find(scope => String(scope.orderLineId) === reference)
  return validationLabels[code] ? `${item ? `${item.projectName}：` : ''}${validationLabels[code]}${reference && !item ? `（${scope?.label || reference}）` : ''}` : value
}
</script>

<style scoped lang="scss">
:global(.parent-scope-drawer) { font-family: "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", sans-serif; }
:global(.parent-scope-drawer .el-drawer__header) { margin-bottom: 0; padding: 22px 24px 18px; color: var(--el-text-color-primary); border-bottom: 1px solid var(--el-border-color-lighter); }
:global(.parent-scope-drawer .el-drawer__title) { font-size: 20px; font-weight: 600; }
:global(.parent-scope-drawer .el-drawer__body) { padding: 20px 24px; }
@media (max-width: 520px) { :global(.parent-scope-drawer .el-drawer__body) { padding: 16px; } :global(.parent-scope-drawer .el-drawer__header) { padding: 18px 16px; } }
.split-workspace { container-type: inline-size; min-width: 0; color: var(--el-text-color-primary, #243348); font-family: "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", sans-serif; background: var(--el-bg-color, #fff); border: 1px solid var(--el-border-color-lighter); border-radius: 6px; padding: 22px; }
.workspace-heading, .parent-scope, .scope-actions, .section-heading, .child-heading, .child-heading > div, .scope-heading, .scope-row-heading, .footer-actions, .footer-buttons, .completion-footer { display: flex; align-items: center; }
.workspace-heading, .parent-scope, .section-heading, .child-heading, .scope-row-heading, .footer-actions, .completion-footer { justify-content: space-between; gap: 16px; }
.workspace-heading { align-items: flex-start; }
.workspace-heading h3 { margin: 0 0 7px; font-size: 20px; line-height: 1.3; font-weight: 650; letter-spacing: -.3px; }
.workspace-heading p, .parent-scope p, .completed-heading p { margin: 0; font-size: 13px; line-height: 1.7; color: var(--el-text-color-secondary, #64748b); }
.workspace-heading .el-tag { flex: none; }
.split-steps { list-style: none; display: flex; padding: 0; margin: 22px 0; }
.split-steps li { flex: 1; display: flex; align-items: center; gap: 8px; color: var(--el-text-color-secondary); font-size: 13px; white-space: nowrap; }
.split-steps li:not(:last-child)::after { content: ''; height: 1px; background: var(--el-border-color); flex: 1; margin: 0 12px 0 4px; }
.step-mark { display: grid; place-items: center; width: 24px; height: 24px; flex: none; border: 1px solid var(--el-border-color); border-radius: 50%; font-size: 12px; font-variant-numeric: tabular-nums; }
.split-steps .active { color: var(--el-color-primary); font-weight: 600; }
.active .step-mark { color: #fff; background: var(--el-color-primary); border-color: var(--el-color-primary); }
.complete .step-mark { color: var(--el-color-success); background: var(--el-color-success-light-9); border-color: var(--el-color-success-light-5); }
.parent-scope { background: var(--el-fill-color-lighter, #f4f7fb); border-left: 3px solid var(--el-color-primary); padding: 12px 14px; margin-bottom: 16px; }
.parent-scope strong { font-size: 13px; font-weight: 600; }
.parent-scope p { margin-top: 3px; font-size: 12px; }
.scope-actions { flex: none; gap: 2px; }
.scope-actions :deep(.el-button + .el-button) { margin-left: 4px; }
.status-alert { margin-bottom: 16px; }
.section-heading { margin: 22px 0 10px; }
.section-heading h4, .preview h4 { margin: 0; font-size: 14px; font-weight: 600; }
.section-heading h4 span { margin-left: 6px; color: var(--el-text-color-secondary); font-size: 12px; font-weight: 400; }
.split-list { display: grid; gap: 20px; }
.split-card { min-width: 0; border: 1px solid var(--el-border-color); border-radius: 5px; overflow: hidden; }
.child-heading { padding: 10px 16px; background: var(--el-fill-color-lighter); border-bottom: 1px solid var(--el-border-color-lighter); }
.child-heading > div { gap: 10px; }
.child-heading strong { font-size: 14px; font-weight: 600; }
.branch-mark { width: 12px; height: 12px; border-left: 2px solid var(--el-color-primary-light-5); border-bottom: 2px solid var(--el-color-primary-light-5); position: relative; align-self: flex-start; margin-top: 1px; }
.branch-mark::after { content: ''; position: absolute; width: 5px; height: 5px; border-radius: 50%; background: var(--el-color-primary); right: -2px; bottom: -3px; }
.split-card > .el-form { padding: 18px 16px; }
.item-fields, .scope-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.project-name-field, .order-line-field { grid-column: 1 / -1; }
.item-fields > *, .scope-fields > * { min-width: 0; }
.split-workspace :deep(.el-form-item) { margin-bottom: 18px; }
.split-workspace :deep(.el-form-item__label) { font-size: 12px; line-height: 20px; margin-bottom: 6px; color: var(--el-text-color-regular); }
.split-workspace :deep(.el-input__wrapper), .split-workspace :deep(.el-select__wrapper) { min-height: 36px; }
.split-workspace :deep(.el-input-number) { width: 100%; }
.scope-heading { gap: 9px; padding-top: 4px; margin-bottom: 10px; font-size: 13px; }
.scope-heading span { font-size: 12px; color: var(--el-text-color-secondary); }
.scope-row { padding: 0 12px 12px; margin-bottom: 12px; background: var(--el-fill-color-lighter); border: 1px solid var(--el-border-color-extra-light); border-radius: 4px; }
.scope-row-heading { min-height: 38px; font-size: 12px; color: var(--el-text-color-secondary); }
.scope-row :deep(.el-form-item:last-child), .scope-row :deep(.el-form-item:nth-last-child(2)) { margin-bottom: 12px; }
.serial-options { padding-top: 9px; border-top: 1px solid var(--el-border-color-lighter); }
.serial-options summary { width: fit-content; cursor: pointer; font-size: 12px; color: var(--el-text-color-regular); line-height: 24px; }
.serial-options summary span { margin-left: 6px; color: var(--el-text-color-secondary); }
.serial-options p { font-size: 12px; line-height: 1.6; color: var(--el-text-color-secondary); margin: 6px 0 10px; }
.serial-options summary:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 3px; }
.add-child { width: 100%; min-height: 38px; margin-top: 14px; border-style: dashed; color: var(--el-color-primary); }
.preview { padding: 18px; margin-top: 22px; border: 1px solid var(--el-border-color); border-radius: 5px; }
.preview h4 { margin-bottom: 12px; }
.preview-item { display: grid; gap: 7px; padding: 14px 0; border-bottom: 1px solid var(--el-border-color-lighter); overflow-wrap: anywhere; }
.preview-item:last-child { border-bottom: 0; padding-bottom: 0; }
.preview-item strong { font-size: 14px; }
.preview-item span { color: var(--el-text-color-regular); font-size: 12px; line-height: 1.7; }
.validation-errors { color: var(--el-color-danger); font-size: 13px; line-height: 1.8; padding-left: 20px; }
.footer-actions { position: sticky; bottom: 0; z-index: 2; flex-wrap: wrap; padding: 16px 0 2px; margin-top: 22px; background: var(--el-bg-color); border-top: 1px solid var(--el-border-color-lighter); }
.save-state { display: flex; align-items: center; gap: 12px; font-size: 12px; }
.save-state strong { font-weight: 500; }
.save-state span { color: var(--el-text-color-secondary); }
.footer-buttons { flex-wrap: wrap; gap: 8px; margin-left: auto; }
.footer-buttons :deep(.el-button + .el-button) { margin-left: 0; }
.completed-heading { display: flex; align-items: flex-start; gap: 14px; padding: 16px 0 24px; }
.completed-mark { display: grid; place-items: center; width: 38px; height: 38px; flex: none; font-size: 21px; color: var(--el-color-success); background: var(--el-color-success-light-9); border-radius: 50%; }
.completed-heading h4 { margin: 0 0 5px; font-size: 18px; }
.created-projects { padding: 0; list-style: none; margin: 0; }
.created-projects li + li { border-top: 1px solid var(--el-border-color-lighter); }
.created-projects a { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 16px 10px; color: var(--el-text-color-primary); text-decoration: none; }
.created-projects a:hover { background: var(--el-fill-color-lighter); }
.created-projects strong, .created-projects small { display: block; }
.created-projects strong { font-size: 14px; font-weight: 500; }
.created-projects small { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 6px; }
.open-child { color: var(--el-color-primary); font-size: 12px; white-space: nowrap; }
.completion-footer { border-top: 1px solid var(--el-border-color-lighter); padding-top: 18px; margin-top: 12px; }
.completion-footer > span { font-size: 12px; color: var(--el-text-color-secondary); }
@container (min-width: 800px) { .item-fields { grid-template-columns: 2fr 1fr 1fr; } .project-name-field { grid-column: auto; } .scope-fields { grid-template-columns: 2fr 1fr 1fr; } .order-line-field { grid-column: auto; } }
@container (max-width: 460px) { .parent-scope { flex-wrap: wrap; gap: 10px; } .scope-actions { margin-left: auto; } .split-steps li { gap: 5px; font-size: 12px; } .split-steps li:not(:last-child)::after { margin: 0 6px; } .step-mark { width: 21px; height: 21px; } .workspace-heading h3 { font-size: 18px; } .completion-footer { align-items: flex-start; flex-direction: column; } }
@container (max-width: 360px) { .item-fields, .scope-fields { grid-template-columns: minmax(0, 1fr); } .footer-buttons { display: grid; width: 100%; grid-template-columns: 1fr; } .split-steps li { flex-direction: column; gap: 6px; } .split-steps li:not(:last-child)::after { display: none; } .open-child { display: none; } }
@media (max-width: 767px) { .split-workspace { padding: 16px; } }
</style>
