<template>
  <ContentWrap>
    <el-form :inline="true" @submit.prevent="search">
      <el-form-item label="实体类型"
        ><el-input v-model="query.entityType" clearable placeholder="实体类型编码"
      /></el-form-item>
      <el-form-item label="视图来源">
        <el-select v-model="query.viewSource" clearable placeholder="全部来源" class="!w-180px">
          <el-option label="业务页面" value="PAGE" /><el-option
            label="动态表单"
            value="DYNAMIC_FORM"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        ><el-button type="primary" @click="search">查询</el-button
        ><el-button @click="resetSearch">重置</el-button></el-form-item
      >
    </el-form>
    <el-button v-hasPermi="['pms:business-view:manage']" type="primary" plain @click="openCreate"
      >新建业务视图</el-button
    >
  </ContentWrap>
  <ContentWrap>
    <el-alert v-if="listError" :title="listError" type="error" :closable="false" show-icon />
    <el-table v-loading="loading" :data="rows" row-key="id">
      <el-table-column prop="entityType" label="实体类型" min-width="185" />
      <el-table-column prop="viewKey" label="视图键" min-width="180" />
      <el-table-column prop="revisionNo" label="修订" width="80" />
      <el-table-column prop="ownerContext" label="Owner" width="100" />
      <el-table-column label="来源" width="100"
        ><template #default="{ row }">{{ sourceLabel(row.viewSource) }}</template></el-table-column
      >
      <el-table-column prop="componentKey" label="组件" min-width="230" />
      <el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">{{
            statusLabel(row.status)
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="操作" width="165" fixed="right"
        ><template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">查看 / 管理</el-button>
          <el-button link type="primary" @click="openHistory(row)">历史</el-button>
        </template></el-table-column
      >
      <template #empty><el-empty description="暂无业务视图注册" /></template>
    </el-table>
    <Pagination
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      :total="total"
      @pagination="loadList"
    />
  </ContentWrap>

  <el-drawer
    v-model="editorOpen"
    size="min(820px, 100%)"
    :title="current ? `${current.viewKey} · R${current.revisionNo}` : '新建业务视图草稿'"
    :before-close="beforeEditorClose"
    destroy-on-close
  >
    <div v-loading="editorLoading" class="business-view-editor">
      <el-alert
        title="注册不授予业务对象权限"
        description="Owner、Provider、Schema与支持动作均由已部署目录决定。保存和发布仅改变注册配置，不创建实体或完成项目任务。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-alert
        v-if="editorError"
        :title="editorError"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="状态">{{ statusLabel(current.status) }}</el-descriptions-item>
        <el-descriptions-item label="内容版本">{{ current.version }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{
          current.publishedAt || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="停用时间">{{
          current.disabledAt || '-'
        }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="125px" :disabled="busy || editorLoading || !editable">
        <el-form-item label="视图键" required
          ><el-input
            v-model="form.viewKey"
            :disabled="!!current"
            maxlength="128"
            placeholder="稳定编码，不可改义"
        /></el-form-item>
        <el-form-item label="已部署组件" required>
          <el-select
            v-model="selectedKey"
            class="w-full"
            placeholder="选择精确组件版本"
            @change="selectComponent"
          >
            <el-option
              v-for="item in compatibleComponents"
              :key="componentIdentity(item)"
              :value="componentIdentity(item)"
              :label="`${item.componentKey} / ${item.componentVersion} · ${item.entityType}`"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="selectedComponent?.viewSource === 'DYNAMIC_FORM'"
          label="表单发布修订"
          required
        >
          <el-select
            v-model="form.dynamicFormRevisionId"
            class="w-full"
            placeholder="选择可用模板的精确发布修订"
          >
            <el-option
              v-for="item in formRevisions"
              :key="item.currentPublishedRevisionId"
              :value="item.currentPublishedRevisionId"
              :label="`${item.templateName} (${item.templateCode}) · R${item.currentPublishedRevisionNo} #${item.currentPublishedRevisionId}`"
            />
            <el-option
              v-if="
                current?.dynamicFormRevisionId &&
                !formRevisions.some((item) =>
                  sameBusinessViewId(
                    item.currentPublishedRevisionId,
                    current?.dynamicFormRevisionId
                  )
                )
              "
              :value="current.dynamicFormRevisionId"
              :label="`已冻结修订 #${current.dynamicFormRevisionId}（仅保留原引用）`"
              disabled
            />
          </el-select>
        </el-form-item>
      </el-form>
      <el-descriptions v-if="selectedComponent" :column="1" border>
        <el-descriptions-item label="实体 / Owner"
          >{{ selectedComponent.entityType }} /
          {{ selectedComponent.ownerContext }}</el-descriptions-item
        >
        <el-descriptions-item label="来源">{{
          sourceLabel(selectedComponent.viewSource)
        }}</el-descriptions-item>
        <el-descriptions-item label="组件版本"
          >{{ selectedComponent.componentKey }} /
          {{ selectedComponent.componentVersion }}</el-descriptions-item
        >
        <el-descriptions-item label="支持动作（非对象授权）">{{
          selectedComponent.supportedActions.join(', ') || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="查询 Provider">{{
          selectedComponent.queryProviderKey
        }}</el-descriptions-item>
        <el-descriptions-item label="命令 Provider">{{
          selectedComponent.commandProviderKey
        }}</el-descriptions-item>
        <el-descriptions-item label="权限 Provider">{{
          selectedComponent.permissionProviderKey
        }}</el-descriptions-item>
        <el-descriptions-item label="上下文 Schema">
          <pre class="schema">{{ JSON.stringify(selectedComponent.contextSchema, null, 2) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="validation"
        :title="validation.valid ? '配置校验通过（尚未发布）' : '配置校验未通过'"
        :type="validation.valid ? 'success' : 'warning'"
        :closable="false"
        show-icon
      />
      <el-table v-if="validation?.issues.length" :data="validation.issues">
        <el-table-column prop="field" label="字段" /><el-table-column
          prop="code"
          label="问题编码"
        /><el-table-column prop="message" label="原因" min-width="200" />
      </el-table>
      <el-alert
        v-if="current && current.status !== 'DRAFT'"
        title="历史精确版本只读"
        description="停用仅阻止新引用，已发布正文不可覆盖；调整请复制为下一修订。此配置页面没有已授权的目标对象上下文，不提供任意ID预览或业务操作。"
        type="info"
        :closable="false"
      />
    </div>
    <template #footer>
      <div class="editor-actions">
        <el-button :disabled="busy" @click="refreshCurrent">刷新权威版本</el-button>
        <el-button
          v-if="editable"
          data-testid="save-registration"
          type="primary"
          :loading="busy"
          :disabled="editorLoading"
          @click="save"
          >保存草稿</el-button
        >
        <el-button v-if="can('VALIDATE')" :disabled="busy || dirty" @click="validateCurrent"
          >校验</el-button
        >
        <el-button v-if="can('COPY')" :disabled="busy || dirty" @click="runAction('COPY')"
          >复制下一修订</el-button
        >
        <el-button
          v-if="can('PUBLISH')"
          type="success"
          :disabled="busy || dirty"
          @click="runAction('PUBLISH')"
          >发布</el-button
        >
        <el-button
          v-if="can('DISABLE')"
          type="danger"
          plain
          :disabled="busy || dirty"
          @click="runAction('DISABLE')"
          >停用</el-button
        >
      </div>
    </template>
  </el-drawer>
  <el-drawer v-model="historyOpen" :title="historyTitle" size="min(900px, 100%)" destroy-on-close>
    <el-alert
      title="版本历史"
      description="仅查询同一稳定身份的修订；停用不会删除已发布正文。"
      type="info"
      :closable="false"
    />
    <el-alert v-if="historyError" :title="historyError" type="error" :closable="false" />
    <el-table v-loading="historyLoading" :data="historyRows" row-key="id">
      <el-table-column prop="revisionNo" label="修订" /><el-table-column
        prop="componentKey"
        label="组件"
        min-width="220"
      />
      <el-table-column prop="componentVersion" label="组件版本" /><el-table-column label="状态"
        ><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column
      >
      <el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="openDetail(row.id)">查看</el-button></template
        ></el-table-column
      >
    </el-table>
  </el-drawer>
</template>

<script setup lang="ts">
import { onBeforeRouteLeave } from 'vue-router'
import * as BusinessViewApi from '@/api/pms/platform/business-view'
import type {
  BusinessViewAction,
  BusinessViewComponentVO,
  BusinessViewCreate,
  BusinessViewRegistrationVO,
  BusinessViewValidation,
  BusinessViewPageQuery
} from '@/api/pms/platform/business-view'
import { getTemplateSelection, type DynamicFormSelectionVO } from '@/api/pms/platform/dynamic-form'
import { sameBusinessViewId, type BusinessViewId } from '@/api/pms/platform/business-view/ids'
type FormRevisionSelection = Omit<DynamicFormSelectionVO, 'currentPublishedRevisionId'> & {
  currentPublishedRevisionId: BusinessViewId
}

// PM-03 / F-PLT-003. No delete action: published revisions and their history are immutable.
defineOptions({ name: 'PmsBusinessView' })
const message = useMessage()
const query = reactive<BusinessViewPageQuery>({ pageNo: 1, pageSize: 10 })
const rows = ref<BusinessViewRegistrationVO[]>([])
const total = ref(0)
const loading = ref(false)
const listError = ref('')
const editorOpen = ref(false)
const editorLoading = ref(false)
const editorError = ref('')
const current = ref<BusinessViewRegistrationVO>()
const components = ref<BusinessViewComponentVO[]>([])
const formRevisions = ref<FormRevisionSelection[]>([])
const selectedKey = ref('')
const emptyForm = (): BusinessViewCreate => ({
  entityType: '',
  viewKey: '',
  componentKey: '',
  componentVersion: ''
})
const form = reactive(emptyForm())
const baseline = ref('')
const busy = ref(false)
const validation = ref<BusinessViewValidation>()
const dirty = computed(() => editorOpen.value && JSON.stringify(form) !== baseline.value)
const can = (action: BusinessViewAction) => current.value?.allowedActions.includes(action) === true
const editable = computed(
  () => !current.value || (current.value.status === 'DRAFT' && can('UPDATE'))
)
const componentIdentity = (
  item: Pick<BusinessViewComponentVO, 'componentKey' | 'componentVersion'>
) => JSON.stringify([item.componentKey, item.componentVersion])
const compatibleComponents = computed(() =>
  components.value.filter((item) => !current.value || item.entityType === current.value.entityType)
)
const selectedComponent = computed(() => {
  // Published metadata is frozen history, not a projection of today's deployed directory.
  if (current.value && current.value.status !== 'DRAFT') return current.value
  return (
    components.value.find((item) => componentIdentity(item) === selectedKey.value) ||
    (current.value && componentIdentity(current.value) === selectedKey.value
      ? current.value
      : undefined)
  )
})
const sourceLabel = (source: string) =>
  ({ PAGE: '业务页面', DYNAMIC_FORM: '动态表单' })[source] || source
const statusLabel = (status: string) =>
  ({ DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' })[status] || status
const intents = new Map<string, string>()
const intentKey = (action: string, payload: unknown) => {
  const intent = JSON.stringify([action, payload])
  if (!intents.has(intent)) intents.set(intent, crypto.randomUUID())
  return intents.get(intent)!
}
let listSequence = 0
const loadList = async () => {
  const sequence = ++listSequence
  loading.value = true
  listError.value = ''
  try {
    const page = await BusinessViewApi.getBusinessViewPage({
      ...query,
      entityType: query.entityType || undefined,
      viewSource: query.viewSource || undefined
    })
    if (sequence === listSequence) {
      rows.value = page.list
      total.value = page.total
    }
  } catch {
    if (sequence === listSequence) listError.value = '列表加载失败，请重新查询。'
  } finally {
    if (sequence === listSequence) loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  loadList()
}
const resetSearch = () => {
  query.entityType = undefined
  query.viewSource = undefined
  search()
}
const apply = (data?: BusinessViewRegistrationVO) => {
  current.value = data
  Object.assign(
    form,
    emptyForm(),
    { dynamicFormRevisionId: undefined },
    data
      ? {
          entityType: data.entityType,
          viewKey: data.viewKey,
          componentKey: data.componentKey,
          componentVersion: data.componentVersion,
          dynamicFormRevisionId: data.dynamicFormRevisionId
        }
      : {}
  )
  selectedKey.value = data ? componentIdentity(data) : ''
  baseline.value = JSON.stringify(form)
  validation.value = undefined
}
const loadCatalog = async () => {
  components.value = await BusinessViewApi.getBusinessViewComponents()
  formRevisions.value = []
}
const loadFormRevisions = async () => {
  const all: DynamicFormSelectionVO[] = []
  for (let pageNo = 1; ; pageNo++) {
    const page = await getTemplateSelection({ pageNo, pageSize: 100 })
    all.push(...page.list)
    if (!page.list.length || all.length >= page.total) break
  }
  formRevisions.value = all
}
const selectComponent = async () => {
  const selected = selectedComponent.value
  if (!selected) return
  form.entityType = selected.entityType
  form.componentKey = selected.componentKey
  form.componentVersion = selected.componentVersion
  form.dynamicFormRevisionId = undefined
  validation.value = undefined
  if (selected.viewSource === 'DYNAMIC_FORM') {
    try {
      await loadFormRevisions()
    } catch {
      editorError.value = '动态表单发布修订查询失败，不能猜测或输入任意修订。'
    }
  }
}
const requestLeave = async () => {
  if (busy.value || editorLoading.value) return false
  if (!dirty.value) return true
  try {
    await message.confirm('当前注册草稿尚未保存，是否放弃本地修改？')
    return true
  } catch {
    return false
  }
}
const beforeEditorClose = async (done: () => void) => {
  if (await requestLeave()) done()
}
const openCreate = async () => {
  if (!(await requestLeave())) return
  apply()
  editorError.value = ''
  editorOpen.value = true
  editorLoading.value = true
  try {
    await loadCatalog()
  } catch {
    editorError.value = '受控目录查询失败，不能创建注册。'
  } finally {
    editorLoading.value = false
  }
}
const openDetail = async (id: BusinessViewId) => {
  if (!(await requestLeave())) return
  editorLoading.value = true
  editorError.value = ''
  try {
    const data = await BusinessViewApi.getBusinessView(id)
    apply(data)
    editorOpen.value = true
    await loadCatalog()
    if (data.viewSource === 'DYNAMIC_FORM' && data.status === 'DRAFT') await loadFormRevisions()
  } catch {
    editorError.value = '精确修订或受控目录加载失败，请重试。'
  } finally {
    editorLoading.value = false
  }
}
const refreshCurrent = async () => {
  if (current.value) await openDetail(current.value.id)
  else await openCreate()
}
const refreshAfterFailure = async (error: unknown) => {
  const intended = { ...form }
  if (current.value) {
    try {
      const authoritative = await BusinessViewApi.getBusinessView(current.value.id)
      apply(authoritative)
      if (authoritative.status === 'DRAFT' && authoritative.allowedActions.includes('UPDATE')) {
        Object.assign(form, intended)
        selectedKey.value = componentIdentity(intended)
      }
    } catch {
      editorError.value = '操作结果未知且权威刷新失败，请勿假定保存成功。'
      return
    }
  }
  editorError.value = BusinessViewApi.isBusinessViewConflict(error)
    ? '版本冲突：已刷新权威版本；仍可编辑的草稿保留本次选择，请核对后重试。'
    : '操作未确认：已重新读取权威状态并保留可编辑意图；请核对后重试，不会自动重放。'
  await loadList()
}
const save = async () => {
  if (!editable.value || busy.value || editorLoading.value) return
  if (
    !/^[A-Za-z][A-Za-z0-9_.:-]{0,127}$/.test(form.viewKey) ||
    !selectedComponent.value ||
    !components.value.some((item) => componentIdentity(item) === selectedKey.value)
  ) {
    message.warning('请填写合法视图键并选择已部署的精确组件。')
    return
  }
  if (selectedComponent.value.viewSource === 'DYNAMIC_FORM' && !form.dynamicFormRevisionId) {
    message.warning('请选择动态表单发布修订。')
    return
  }
  busy.value = true
  editorError.value = ''
  try {
    const data = {
      ...form,
      ...(current.value
        ? {
            entityType: current.value.entityType,
            viewKey: current.value.viewKey
          }
        : {})
    }
    const result = current.value
      ? await BusinessViewApi.updateBusinessView(
          current.value.id,
          current.value.version,
          data,
          intentKey('UPDATE', [current.value.id, current.value.version, data])
        )
      : await BusinessViewApi.createBusinessView(data, intentKey('CREATE', data))
    apply(result)
    message.success('注册草稿已保存')
    await loadList()
  } catch (error) {
    await refreshAfterFailure(error)
  } finally {
    busy.value = false
  }
}
const validateCurrent = async () => {
  if (!current.value || !can('VALIDATE') || busy.value || dirty.value) return
  busy.value = true
  editorError.value = ''
  try {
    validation.value = await BusinessViewApi.validateBusinessView(current.value.id)
  } catch {
    editorError.value = '校验服务不可用，不能视为通过。'
  } finally {
    busy.value = false
  }
}
const runAction = async (action: 'COPY' | 'PUBLISH' | 'DISABLE') => {
  if (!current.value || !can(action) || busy.value || dirty.value) return
  try {
    await message.confirm(
      action === 'DISABLE'
        ? '停用后禁止新引用，历史保持可查，是否继续？'
        : action === 'PUBLISH'
          ? '发布后正文不可修改，是否继续？'
          : '是否复制为下一修订草稿？'
    )
  } catch {
    return
  }
  busy.value = true
  editorError.value = ''
  try {
    const { id, version } = current.value
    const command = {
      COPY: BusinessViewApi.copyBusinessView,
      PUBLISH: BusinessViewApi.publishBusinessView,
      DISABLE: BusinessViewApi.disableBusinessView
    }[action]
    apply(await command(id, version, intentKey(action, [id, version])))
    message.success('注册操作成功')
    await loadList()
  } catch (error) {
    await refreshAfterFailure(error)
  } finally {
    busy.value = false
  }
}
const historyOpen = ref(false)
const historyLoading = ref(false)
const historyTitle = ref('版本历史')
const historyRows = ref<BusinessViewRegistrationVO[]>([])
const historyError = ref('')
let historySequence = 0
const openHistory = async (source: BusinessViewRegistrationVO) => {
  const sequence = ++historySequence
  historyTitle.value = `${source.entityType} / ${source.viewKey} · 版本历史`
  historyOpen.value = true
  historyLoading.value = true
  historyRows.value = []
  historyError.value = ''
  try {
    let count = 0
    for (let pageNo = 1; ; pageNo++) {
      const page = await BusinessViewApi.getBusinessViewPage({
        pageNo,
        pageSize: 100,
        entityType: source.entityType
      })
      if (sequence !== historySequence) return
      historyRows.value.push(...page.list.filter((item) => item.viewKey === source.viewKey))
      count += page.list.length
      if (!page.list.length || count >= page.total) break
    }
  } catch {
    if (sequence === historySequence) historyError.value = '历史查询不完整，请关闭后重试。'
  } finally {
    if (sequence === historySequence) historyLoading.value = false
  }
}
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (!dirty.value && !busy.value) return
  event.preventDefault()
  event.returnValue = ''
}
onMounted(() => {
  loadList()
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
onBeforeRouteLeave(requestLeave)
</script>

<style scoped lang="scss">
.business-view-editor {
  display: grid;
  gap: 16px;
  min-width: 0;
}
.schema {
  margin: 0;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
.editor-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
