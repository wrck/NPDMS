<template>
  <ContentWrap>
    <header class="page-heading"><h2>项目交付模板</h2><p>使用 DesignerDocument 组织阶段、真实任务、办理方式和规则；发布时由 Compiler 生成不可变 ExecutionSnapshot。</p></header>
  </ContentWrap>
  <div class="template-list">
    <ContentWrap>
      <el-form :model="query" inline>
        <el-form-item label="模板编码"><el-input v-model="query.code" clearable @keyup.enter="reload" /></el-form-item>
        <el-form-item label="模板名称"><el-input v-model="query.name" clearable @keyup.enter="reload" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable class="!w-140px"><el-option value="DRAFT" label="草稿" /><el-option value="ACTIVE" label="生效" /><el-option value="RETIRED" label="停用" /></el-select></el-form-item>
        <el-form-item><el-button @click="reload">查询</el-button><el-button type="primary" @click="openCreate" v-hasPermi="['pms:project-template:create']">新增模板</el-button><el-button type="warning" plain @click="openMatchPreview" v-hasPermi="['pms:project-template:query']">匹配预演</el-button></el-form-item>
      </el-form>
      <el-alert v-if="failure" :title="failure" type="error" :closable="false" role="alert" class="mb-12px" />
      <el-table :data="rows" v-loading="loading" empty-text="暂无项目模板数据">
        <el-table-column label="模板名称" min-width="240"><template #default="{ row }"><strong class="template-name">{{ row.name }}</strong><div class="secondary">{{ row.code }}</div></template></el-table-column>
        <el-table-column label="适用场景" min-width="220"><template #default="{ row }">{{ summaries[row.id]?.scenario ?? '打开编辑查看场景' }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="设计概览" min-width="220"><template #default="{ row }">{{ summaries[row.id]?.tasks ?? '打开编辑查看交付设计' }}</template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:project-template:query']">编辑</el-button><el-button link type="primary" @click="openCopy(row)" v-hasPermi="['pms:project-template:update']">复制</el-button></template></el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
    </ContentWrap>
  </div>

  <Dialog v-model="createVisible" title="新增项目模板" width="620px">
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
      <el-form-item label="模板编码" prop="code"><el-input v-model="createForm.code" placeholder="创建后不可修改" /></el-form-item>
      <el-form-item label="模板名称" prop="name"><el-input v-model="createForm.name" /></el-form-item>
      <el-form-item label="匹配优先级"><el-input-number v-model="createForm.matchPriority" :min="1" /><span class="ml-8px">数值小者先命中</span></el-form-item>
      <el-form-item label="描述"><el-input v-model="createForm.description" type="textarea" /></el-form-item>
    </el-form>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" />
    <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveCreate">创建</el-button></template>
  </Dialog>

  <ContentWrap>
    <el-collapse v-model="pageAdvanced"><el-collapse-item title="高级资产管理 · 可复用定义库（仅导入来源）" name="definitions"><DefinitionLibrary v-if="pageAdvanced.includes('definitions')" /></el-collapse-item></el-collapse>
    <p class="secondary">DefinitionLibrary 只提供可复用导入来源；模板运行结构由 DesignerDocument 和发布后的 ExecutionSnapshot 决定。</p>
  </ContentWrap>

  <el-drawer v-model="detailVisible" :title="`交付设计 · ${detail?.name ?? ''}`" size="96%" :before-close="beforeCloseDetail">
    <template v-if="detail">
      <el-alert v-if="failure" :title="failure" type="error" :closable="false" role="alert" class="mb-12px" />
      <el-tabs v-model="detailTab">
        <el-tab-pane label="基本信息" name="identity">
          <el-form :model="identityForm" label-width="110px" class="identity-form" :disabled="draftReadonly || saving">
            <el-form-item label="模板编码"><el-input :model-value="detail.code" disabled /></el-form-item>
            <el-form-item label="状态"><el-tag :type="statusTagType(detail.status)">{{ statusLabel(detail.status) }}</el-tag></el-form-item>
            <el-form-item label="模板名称"><el-input v-model="identityForm.name" /></el-form-item>
            <el-form-item label="匹配优先级"><el-input-number v-model="identityForm.matchPriority" :min="1" /></el-form-item>
            <el-form-item label="描述"><el-input v-model="identityForm.description" type="textarea" /></el-form-item>
            <el-form-item><el-button type="primary" :loading="saving" @click="saveIdentity" v-hasPermi="['pms:project-template:update']">保存基本信息</el-button><el-button :disabled="detail.status !== 'ACTIVE' || saving" @click="disable(detail)" v-hasPermi="['pms:project-template:disable']">停用模板</el-button><el-button type="danger" plain :disabled="saving" @click="remove(detail)" v-hasPermi="['pms:project-template:delete']">删除草稿模板</el-button></el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="交付设计" name="draft">
          <el-alert v-if="draftReadonly" title="模板已停用，Designer 只读。需要新供给请显式复制，不修改既有历史。" type="warning" :closable="false" />
          <TemplateContentEditor ref="contentEditor" :content="draft" :readonly="draftReadonly || saving" @dirty-change="bindingDirty = $event" />
          <div class="mt-16px">
            <el-button type="primary" :disabled="draftReadonly" :loading="saving" @click="saveDraft" v-hasPermi="['pms:project-template:update']">保存 Designer 草稿</el-button>
            <el-button :disabled="saving" @click="precheck(detail)" v-hasPermi="['pms:project-template:query']">Compiler 预检</el-button>
            <el-button type="success" :disabled="draftReadonly" :loading="saving" @click="publish(detail)" v-hasPermi="['pms:project-template:publish']">编译并发布</el-button>
            <span v-if="draftDirty" class="ml-12px">存在未保存修改；预检与发布只读取服务端已保存 Designer。</span>
          </div>
        </el-tab-pane>
        <el-tab-pane label="版本历史" name="revisions">
          <el-table :data="detail.revisions" border>
            <el-table-column prop="revisionNo" label="版本" width="80" /><el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="Schema" width="120"><template #default="{ row }">{{ row.executionSchemaVersion ? `Exec v${row.executionSchemaVersion}` : row.designerSchemaVersion ? `Designer v${row.designerSchemaVersion}` : 'Legacy' }}</template></el-table-column>
            <el-table-column label="四维条件" min-width="220"><template #default="{ row }">{{ dimText(row) }}</template></el-table-column>
            <el-table-column prop="compilerVersion" label="Compiler" min-width="150" /><el-table-column prop="snapshotHash" label="Snapshot Hash" min-width="180" show-overflow-tooltip />
            <el-table-column prop="validationSummary" label="校验摘要" min-width="180" /><el-table-column prop="publishedTime" label="发布时间" min-width="160" :formatter="dateFormatter" />
            <el-table-column label="操作" width="170"><template #default="{ row }"><el-button v-if="row.status === 'PUBLISHED'" link @click="viewRevision(row)">查看冻结投影</el-button><el-button link @click="openCopy(detail, row.revisionNo)" v-hasPermi="['pms:project-template:update']">复制该版</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-drawer>

  <Dialog v-model="validationVisible" title="Compiler 发布预检" width="760px">
    <el-alert v-if="validation" :type="validation.valid ? 'success' : 'error'" :title="validation.valid ? '预检通过；发布命令仍会重新编译并校验依赖' : '未通过：没有生成发布快照，也没有修改历史'" :closable="false" />
    <el-table :data="validation?.issues ?? []" border class="mt-12px"><el-table-column prop="field" label="字段 / 关系" min-width="180" /><el-table-column prop="code" label="错误码" min-width="150" /><el-table-column prop="message" label="原因" min-width="280" /></el-table>
  </Dialog>

  <Dialog v-model="copyVisible" title="复制模板为新 Designer 草稿" width="620px">
    <el-form label-width="120px"><el-form-item label="来源">{{ copySource?.code }}（根并发版本 {{ copySource?.version }}）</el-form-item>
      <el-form-item label="来源修订"><el-select v-model="copyForm.sourceRevisionNo"><el-option :value="0" label="当前草稿（0）" /><el-option v-for="revision in copySource?.revisions.filter((revision) => revision.status === 'PUBLISHED') ?? []" :key="revision.revisionNo" :value="revision.revisionNo" :label="`已发布 v${revision.revisionNo}`" /></el-select></el-form-item>
      <el-form-item label="新模板编码"><el-input v-model="copyForm.code" /></el-form-item><el-form-item label="新模板名称"><el-input v-model="copyForm.name" /></el-form-item>
    </el-form>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" />
    <template #footer><el-button :disabled="saving" @click="copyVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveCopy">复制为新草稿</el-button></template>
  </Dialog>

  <Dialog v-model="snapshotVisible" :title="`已发布只读投影 v${snapshot?.revisionNo ?? ''}`" width="92%">
    <template v-if="snapshot">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="四维条件">{{ dimText(snapshot) }}</el-descriptions-item><el-descriptions-item label="发布时间">{{ formatDate(snapshot.publishedTime) }}</el-descriptions-item>
        <el-descriptions-item label="Compiler">{{ snapshot.compilerVersion ?? 'Legacy' }}</el-descriptions-item><el-descriptions-item label="Snapshot Hash">{{ snapshot.snapshotHash ?? '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert class="mt-12px" title="该内容是发布版本的只读兼容/运行投影，不会回灌 Designer，也不能原地修改。" type="info" :closable="false" />
      <pre class="snapshot-json">{{ JSON.stringify(snapshot.content, null, 2) }}</pre>
    </template>
  </Dialog>

  <Dialog v-model="matchVisible" title="四维匹配预演" width="640px">
    <el-form label-width="110px"><el-form-item v-for="dimension in dimensions" :key="dimension.key" :label="dimension.label"><el-select v-model="matchForm[dimension.key]" clearable><el-option v-for="option in getStrDictOptions(dimension.dict)" :key="option.value" :value="option.value" :label="option.label" /></el-select></el-form-item><el-button type="primary" :loading="matching" @click="runMatchPreview">执行预演</el-button></el-form>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" />
    <template v-if="matchResult"><el-result v-if="matchResult.outcome === 'MATCHED' && matchResult.matched" icon="success" :title="`唯一命中：${matchResult.matched.code} - ${matchResult.matched.name}`" :sub-title="`匹配优先级 ${matchResult.matched.matchPriority ?? '-'}`" /><el-result v-else-if="matchResult.outcome === 'NO_MATCH'" icon="warning" title="无匹配模板" /><el-alert v-else title="同优先级多匹配，需人工处理，不静默选模" type="error" :closable="false" /><ul><li v-for="(conflict, index) in matchResult.conflicts" :key="index">{{ conflict }}</li></ul></template>
  </Dialog>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { dateFormatter, formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import * as TemplateApi from '@/api/pms/project/project-templates'
import type { MatchPreviewReqVO, MatchRespVO, ProjectTemplateDetailVO, ProjectTemplateRevisionDetailVO, ProjectTemplateVO, TemplateCopy, TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import type { ValidationResult } from '@/api/pms/project/project-templates/definitions'
import { isBusinessViewConflict } from '@/api/pms/platform/business-view'
import DefinitionLibrary from './DefinitionLibrary.vue'
import TemplateContentEditor from './TemplateContentEditor.vue'
import { cloneContent, commandIntent, emptyContent, errorText, graphIssues } from './editorModel'

defineOptions({ name: 'PmsProjectTemplate' })
const message = useMessage()
const pageAdvanced = ref<string[]>([])
const contentEditor = ref<InstanceType<typeof TemplateContentEditor>>()
const bindingDirty = ref(false)
const summaries = reactive<Record<number, { scenario: string; tasks: string }>>({})
const loading = ref(false)
const saving = ref(false)
const failure = ref('')
const rows = ref<ProjectTemplateVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, code: '', name: '', status: undefined as string | undefined })
const dimensions = [
  { key: 'signingMethod', label: '签约方式', dict: DICT_TYPE.PMS_SIGNING_METHOD }, { key: 'projectCategory', label: '项目类别', dict: DICT_TYPE.PMS_PROJECT_CATEGORY },
  { key: 'implementationMethod', label: '实施方式', dict: DICT_TYPE.PMS_IMPLEMENTATION_METHOD }, { key: 'majorProjectLevel', label: '重大项目级别', dict: DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL }
] as const
const dimText = (row: MatchPreviewReqVO) => dimensions.map((dimension) => {
  const value = row[dimension.key]
  return value ? getStrDictOptions(dimension.dict).find((option) => option.value === value)?.label ?? value : undefined
}).filter(Boolean).join(' / ') || '全部场景'

const load = async () => {
  loading.value = true
  try {
    const page = await TemplateApi.getProjectTemplatePage(query); rows.value = page.list; total.value = page.total
    await Promise.all(rows.value.map(async (row) => {
      if (row.id == null) return
      try {
        const designer = await TemplateApi.getProjectTemplateDraft(row.id)
        summaries[row.id] = { scenario: dimText(designer.match), tasks: `${designer.stages.length} 个阶段 · ${designer.tasks.filter((task) => task.stageCode !== 'S0').length} 项任务` }
      } catch { delete summaries[row.id] }
    }))
  } catch (error) { failure.value = errorText(error) }
  finally { loading.value = false }
}
const reload = () => { query.pageNo = 1; failure.value = ''; load() }
const statusLabel = (status?: string) => ({ DRAFT: '草稿', ACTIVE: '生效', RETIRED: '停用' }[status ?? ''] ?? status)
const statusTagType = (status?: string) => status === 'ACTIVE' ? 'success' : status === 'RETIRED' ? 'danger' : 'info'
const createVisible = ref(false)
const createFormRef = ref()
const createForm = reactive({ code: '', name: '', matchPriority: 100, description: '' })
const createRules = { code: [{ required: true, message: '请输入模板编码' }], name: [{ required: true, message: '请输入模板名称' }] }
const openCreate = () => { Object.assign(createForm, { code: '', name: '', matchPriority: 100, description: '' }); failure.value = ''; createVisible.value = true }
const saveCreate = async () => {
  if (saving.value) return
  await createFormRef.value.validate(); saving.value = true
  try { const id = await TemplateApi.createProjectTemplate(createForm); await TemplateApi.updateProjectTemplateDraft(id, emptyContent()); createVisible.value = false; message.success('已创建 V2 Designer 草稿'); await load() }
  catch (error) { failure.value = errorText(error) }
  finally { saving.value = false }
}

const detailVisible = ref(false)
const detailTab = ref('draft')
const detail = ref<ProjectTemplateDetailVO>()
const identityForm = reactive({ name: '', matchPriority: 100, description: '' })
const draft = ref<TemplateDesignerDocument>(emptyContent())
const baseline = ref('')
const identityBaseline = ref('')
const draftDirty = computed(() => bindingDirty.value || JSON.stringify(draft.value) !== baseline.value)
const identityDirty = computed(() => JSON.stringify(identityForm) !== identityBaseline.value)
const draftReadonly = computed(() => detail.value?.status === 'RETIRED')
interface DraftStash { savedAt: number; identity: { name: string; matchPriority: number; description: string }; content: TemplateDesignerDocument }
const stashKey = (id?: number) => id == null ? '' : `pms:template-designer-v2-stash:${id}`
const readStash = (id: number): DraftStash | undefined => { try { const raw = localStorage.getItem(stashKey(id)); return raw ? JSON.parse(raw) : undefined } catch { return undefined } }
const clearStash = (id?: number) => { if (id != null) localStorage.removeItem(stashKey(id)) }
let stashTimer: ReturnType<typeof setTimeout> | undefined
watch([draft, identityForm], () => {
  if (!detailVisible.value || detail.value?.id == null || saving.value) return
  if (stashTimer) clearTimeout(stashTimer)
  stashTimer = setTimeout(() => {
    if (!draftDirty.value && !identityDirty.value) return
    try { localStorage.setItem(stashKey(detail.value?.id), JSON.stringify({ savedAt: Date.now(), identity: { ...identityForm }, content: cloneContent(draft.value) } satisfies DraftStash)) } catch { /* best effort */ }
  }, 800)
}, { deep: true })
const openDetail = async (row: ProjectTemplateVO, tab = 'draft') => {
  try {
    const [result, designer] = await Promise.all([TemplateApi.getProjectTemplate(row.id!), TemplateApi.getProjectTemplateDraft(row.id!)])
    detail.value = result
    Object.assign(identityForm, { name: result.name, matchPriority: result.matchPriority ?? 100, description: result.description ?? '' })
    draft.value = cloneContent(designer); baseline.value = JSON.stringify(draft.value); identityBaseline.value = JSON.stringify(identityForm); failure.value = ''; detailTab.value = tab
    const stash = readStash(result.id!)
    if (stash && (JSON.stringify(stash.content) !== baseline.value || JSON.stringify(stash.identity) !== identityBaseline.value)) {
      try { await message.confirm(`检测到 ${formatDate(stash.savedAt)} 保存的本地未提交 Designer，是否恢复？`); draft.value = cloneContent(stash.content); Object.assign(identityForm, stash.identity); message.success('已恢复本地暂存；尚未保存到服务器。') }
      catch { clearStash(result.id) }
    }
    detailVisible.value = true
  } catch (error) { failure.value = errorText(error) }
}
const beforeCloseDetail = async (done: () => void) => {
  if (saving.value) return
  if (draftDirty.value || identityDirty.value) { try { await message.confirm('有未保存修改，确认关闭并放弃本地编辑？') } catch { return }; clearStash(detail.value?.id) }
  done()
}
const saveIdentity = async () => {
  if (!detail.value?.id || saving.value) return
  saving.value = true
  try { await TemplateApi.updateProjectTemplate(detail.value.id, { ...identityForm }); identityBaseline.value = JSON.stringify(identityForm); detail.value = { ...detail.value, ...identityForm }; await load(); message.success('基本信息已保存') }
  catch (error) { failure.value = errorText(error) }
  finally { saving.value = false }
}
const saveDraft = async () => {
  if (!detail.value?.id || saving.value) return
  saving.value = true; failure.value = ''
  try {
    const designer = await contentEditor.value?.prepareSave() ?? draft.value
    await TemplateApi.updateProjectTemplateDraft(detail.value.id, designer)
    draft.value = cloneContent(await TemplateApi.getProjectTemplateDraft(detail.value.id)); bindingDirty.value = false; baseline.value = JSON.stringify(draft.value); clearStash(detail.value.id)
    message.success('Designer 草稿已保存；尚未发布，运行时不会读取草稿')
  } catch (error) { failure.value = errorText(error) }
  finally { saving.value = false }
}

const validation = ref<ValidationResult>()
const validationVisible = ref(false)
const check = async (row: ProjectTemplateVO) => {
  if (detailVisible.value && detail.value?.id === row.id && draftDirty.value) { failure.value = '请先保存 Designer 修改；预检与发布只读取服务端草稿。'; return false }
  const designer = await TemplateApi.getProjectTemplateDraft(row.id!)
  const local = graphIssues(cloneContent(designer))
  const server = await TemplateApi.validateProjectTemplate(row.id!)
  validation.value = { valid: server.valid && !local.length, issues: [...local, ...server.issues] }; validationVisible.value = true
  return validation.value.valid
}
const precheck = async (row: ProjectTemplateVO) => { if (saving.value) return; saving.value = true; failure.value = ''; try { await check(row) } catch (error) { failure.value = errorText(error) } finally { saving.value = false } }
const publish = async (row: ProjectTemplateVO) => {
  if (saving.value) return
  saving.value = true; failure.value = ''
  try {
    if (!await check(row)) return
    try { await message.confirm(`确认编译并发布模板「${row.code}」？将冻结不可变 ExecutionSnapshot，不覆盖既有项目。`) } catch { return }
    await TemplateApi.publishProjectTemplate(row.id!); message.success('编译并发布成功'); clearStash(row.id); validationVisible.value = false; detailVisible.value = false; await load()
  } catch (error) { failure.value = errorText(error); validation.value = { valid: false, issues: [{ field: 'publish', code: 'PUBLISH_REJECTED', message: failure.value }] }; validationVisible.value = true }
  finally { saving.value = false }
}

const destructive = async (row: ProjectTemplateVO, action: 'disable' | 'delete') => {
  if (saving.value) return
  try { await message.confirm(action === 'disable' ? `停用「${row.code}」？新项目不再匹配，历史不受影响。` : `删除「${row.code}」？仅无发布版本且非系统保留模板可删除。`) } catch { return }
  saving.value = true
  try { await (action === 'disable' ? TemplateApi.disableProjectTemplate : TemplateApi.deleteProjectTemplate)(row.id!); detailVisible.value = false; await load() }
  catch (error) { failure.value = errorText(error) }
  finally { saving.value = false }
}
const disable = (row: ProjectTemplateVO) => destructive(row, 'disable')
const remove = (row: ProjectTemplateVO) => destructive(row, 'delete')

const copyVisible = ref(false)
const copySource = ref<ProjectTemplateDetailVO>()
const copyForm = reactive<TemplateCopy>({ code: '', name: '', sourceRevisionNo: 0 })
const copyIntent = commandIntent()
const openCopy = async (row: ProjectTemplateVO, revisionNo = 0) => {
  try { copySource.value = await TemplateApi.getProjectTemplate(row.id!); Object.assign(copyForm, { code: '', name: '', sourceRevisionNo: revisionNo }); failure.value = ''; copyIntent.clear(); copyVisible.value = true }
  catch (error) { failure.value = errorText(error) }
}
const saveCopy = async () => {
  if (!copySource.value?.id || saving.value) return
  if (!copyForm.code.trim() || !copyForm.name.trim()) { failure.value = '新模板编码和名称必填。'; return }
  if (copySource.value.version == null) { failure.value = '缺少根并发版本，请重新打开复制窗口。'; return }
  saving.value = true
  try {
    const source = copySource.value; const body = { ...copyForm }; const key = copyIntent.key({ id: source.id, version: source.version, body })
    await TemplateApi.copyProjectTemplate(source.id!, source.version!, body, key); copyIntent.clear(); copyVisible.value = false; await load(); message.success('已复制为新 Designer 草稿')
  } catch (error) { failure.value = errorText(error); if (isBusinessViewConflict(error)) { copySource.value.version = undefined; failure.value = '来源版本冲突，请重新打开复制窗口。' } }
  finally { saving.value = false }
}

const snapshotVisible = ref(false)
const snapshot = ref<ProjectTemplateRevisionDetailVO>()
const viewRevision = async (revision: { revisionNo: number }) => {
  if (!detail.value?.id) return
  try { snapshot.value = await TemplateApi.getProjectTemplateRevision(detail.value.id, revision.revisionNo); snapshotVisible.value = true }
  catch (error) { failure.value = errorText(error) }
}

const matchVisible = ref(false)
const matching = ref(false)
const matchResult = ref<MatchRespVO>()
const matchForm = reactive<MatchPreviewReqVO>({})
const openMatchPreview = () => { matchResult.value = undefined; failure.value = ''; matchVisible.value = true }
const runMatchPreview = async () => { matching.value = true; try { matchResult.value = await TemplateApi.matchPreview({ ...matchForm }) } catch (error) { failure.value = errorText(error) } finally { matching.value = false } }
onMounted(load)
</script>
<style scoped>
.identity-form { max-width: 600px; }
.page-heading h2 { margin: 0; font-size: 22px; font-weight: 600; }.page-heading p { margin: 10px 0 0; color: var(--el-text-color-secondary); font-size: 14px; line-height: 1.7; }
.template-name { font-size: 14px; font-weight: 500; }.secondary { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 6px; line-height: 1.7; }
.snapshot-json { max-height: 58vh; overflow: auto; padding: 14px; border-radius: 6px; background: var(--el-fill-color-light); font-size: 12px; line-height: 1.6; white-space: pre-wrap; word-break: break-all; }
@media (max-width: 600px) { .page-heading h2 { font-size: 19px; } } ul { padding-left: 18px; }
</style>