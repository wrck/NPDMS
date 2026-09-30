<template>
  <!-- 项目工作台：实施方案是项目内逐份编写的文档，直接展开当前方案正文，不套列表+弹窗 -->
  <template v-if="props.projectId">
    <!-- 引导（分页→自动选中→详情）与切换详情加载期间：不渲染任何方案内容，整块骨架占位并在就绪后一次性替换，
         不出现「空正文→内容涌入」的跳变；切换时占位高度冻结为上次正文高度，正文区不再塌缩再撑开 -->
    <ContentWrap v-if="!contentReady">
      <div
        class="workbench-boot-skeleton"
        :style="bodyMinHeight ? { minHeight: `${bodyMinHeight}px` } : undefined"
      >
        <el-skeleton :rows="4" animated />
      </div>
    </ContentWrap>
    <template v-else>
      <ContentWrap data-testid="solution-workbench">
        <el-alert
          v-if="policies[props.projectId]?.configured && policies[props.projectId].reviewLevel === null"
          :title="policies[props.projectId].reason || '项目审核判定依据缺失，提交前请由工程管理部补齐来源及规则'"
          type="warning"
          :closable="false"
        />
        <div class="workbench-toolbar" data-testid="solution-workbench-toolbar">
          <span class="workbench-label">当前方案</span>
          <el-select
            :model-value="form.id ?? undefined"
            :loading="detailLoading"
            placeholder="选择本项目的实施方案"
            class="!w-320px"
            data-testid="solution-workbench-select"
            @change="pickSolution"
          >
            <el-option v-for="row in rows" :key="row.id" :value="row.id!" :label="solutionLabel(row)" />
          </el-select>
          <el-button type="primary" plain @click="openCreate" v-hasPermi="['pms:sol-solution:create']">
            <Icon icon="ep:plus" />新增方案
          </el-button>
          <template v-if="form.id">
            <el-button
              v-if="form.status === 1 || form.status === 2"
              link
              type="primary"
              @click="tieredReview.open(form)"
              v-hasPermi="['pms:sol-solution:query']"
              >审批进度</el-button
            >
            <el-button
              v-if="form.status === 0"
              link
              type="danger"
              @click="remove(form)"
              v-hasPermi="['pms:sol-solution:delete']"
              >删除</el-button
            >
          </template>
        </div>
      </ContentWrap>
      <ContentWrap ref="bodyCardRef">
        <el-alert v-if="detailError" :title="detailError" type="error" :closable="false">
          <el-button @click="openForm(form)">重新加载</el-button>
        </el-alert>
        <el-form v-else ref="formRef" :model="form" label-width="100px" :disabled="readOnly">
          <SolutionChapterForm
            v-model="form"
            :read-only="readOnly"
            :saving="saving"
            @save="save"
            @submit-review="submitReview"
          />
        </el-form>
      </ContentWrap>
    </template>
  </template>

  <!-- 全局管理视图：跨项目方案台账，保留列表+弹窗 -->
  <template v-else>
    <ContentWrap>
      <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
        <el-form-item label="项目编号" prop="projectId">
          <PmsEntitySelect
            v-model="query.projectId"
            :api="ProjectApi.getProjectPage"
            label-field="projectName"
            value-field="id"
            query-field="projectName"
            placeholder="请选择项目"
            class="!w-180px"
          />
        </el-form-item>
        <el-form-item label="方案名称" prop="name">
          <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="query.status" clearable class="!w-140px">
            <el-option
              v-for="dict in getIntDictOptions(DICT_TYPE.PMS_APPROVAL_STATUS)"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
          <el-button type="primary" @click="openForm()" v-hasPermi="['pms:sol-solution:create']"
            ><Icon icon="ep:plus" />新增方案</el-button
          >
        </el-form-item>
      </el-form>
      <el-alert title="方案可引用前序阶段数据。提交后按项目审核策略进入审批流：服务经理审核，重大项目由工程管理部复审，审批记录与批准基线会保留。" type="info" :closable="false" />
    </ContentWrap>
    <ContentWrap>
      <el-table v-loading="loading" :data="rows">
        <el-table-column prop="name" label="方案名称" min-width="180" />
        <el-table-column prop="versionLabel" label="版本" width="90" />
        <el-table-column prop="baselineVersion" label="基线版本" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              link
              type="primary"
              @click="tieredReview.open(row)"
              v-hasPermi="['pms:sol-solution:update']"
              >提交审批</el-button
            >
            <el-button
              v-if="row.status === 1 || row.status === 2"
              link
              type="primary"
              @click="tieredReview.open(row)"
              v-hasPermi="['pms:sol-solution:query']"
              >审批进度</el-button
            >
            <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:sol-solution:query']"
              >{{ editableDraft(row) ? '编辑' : '查看' }}</el-button
            >
            <el-button v-if="row.status === 0" link type="danger" @click="remove(row)" v-hasPermi="['pms:sol-solution:delete']"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        :total="total"
        v-model:page="query.pageNo"
        v-model:limit="query.pageSize"
        @pagination="load"
      />
    </ContentWrap>
  </template>

  <Dialog
    v-if="!props.projectId"
    v-model="formVisible"
    :title="form.id ? (readOnly ? '查看方案' : '编辑方案') : '新增方案'"
    :loading="detailLoading"
    width="min(1080px, 96vw)"
  >
    <el-alert v-if="detailError" :title="detailError" type="error" :closable="false">
      <el-button @click="openForm(form)">重新加载</el-button>
    </el-alert>
    <el-descriptions v-if="form.id && !detailError" title="当前记录审核信息" :column="2" border class="mb-16px" data-testid="solution-review-result">
      <el-descriptions-item label="状态"><dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="form.status ?? ''" /></el-descriptions-item>
      <el-descriptions-item label="记录版本">{{ form.version ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="基线版本">{{ form.baselineVersion ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="审核时间">{{ form.approvedTime ? formatDate(form.approvedTime) : '—' }}</el-descriptions-item>
      <el-descriptions-item label="审核人编号">{{ form.approvedBy ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="审核意见" :span="2"><span class="whitespace-pre-wrap break-words">{{ form.approvalOpinion || '—' }}</span></el-descriptions-item>
    </el-descriptions>
    <el-form v-if="!detailError" ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="readOnly">
      <el-form-item label="项目编号" prop="projectId">
        <PmsEntitySelect
          v-model="form.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          :disabled="!!form.id"
        />
      </el-form-item>
      <SolutionDocMetaForm v-model="form" :read-only="readOnly" />
      <SolutionChapterForm
        v-model="form"
        :read-only="readOnly"
        :saving="saving"
        @save="save"
        @submit-review="submitReview"
      />
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存草稿</el-button>
    </template>
  </Dialog>

  <!-- 新增方案弹窗（项目工作台）：客户方案创建时一次性选择；方案名称/类型由记录规则自动填充 -->
  <Dialog
    v-if="props.projectId && createVisible"
    v-model="createVisible"
    title="新增方案"
    width="520px"
    data-testid="solution-create-dialog"
  >
    <el-form label-width="100px">
      <el-form-item label="客户方案">
        <el-radio-group v-model="createChoice.hasCustomerPlan">
          <el-radio value="yes">已有客户方案</el-radio>
          <el-radio value="no">无</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="createChoice.hasCustomerPlan === 'yes'" label="上传方案文件">
        <UploadFile v-model="createChoice.customerPlanUrl" />
      </el-form-item>
      <el-form-item v-if="createChoice.hasCustomerPlan === 'yes'">
        <div class="create-tip">确定后本版本正文直接展示客户文档，不再显示九章编辑内容；系统识别未接入</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" @click="confirmCreate">确定</el-button>
    </template>
  </Dialog>

  <SolutionTieredReviewDialog ref="tieredReview" @changed="load" />
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as SolutionApi from '@/api/pms/engineering/solution'
import * as ReviewApi from '@/api/pms/engineering/solution-review'
import type { SolutionVO } from '@/api/pms/engineering/solution'
import * as ProjectApi from '@/api/pms/project/projects'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import { checkPermi } from '@/utils/permission'
import { buildRecordName } from '../recordNaming'
import { formatDate } from '@/utils/formatTime'
import SolutionTieredReviewDialog from './SolutionTieredReviewDialog.vue'
import SolutionDocMetaForm from './SolutionDocMetaForm.vue'
import SolutionChapterForm from './SolutionReviewChapterForm.vue'

defineOptions({ name: 'PmsSolutionReviewed' })
const tieredReview = ref()
const props = defineProps<{ projectId?: number }>()
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<SolutionVO[]>([])
const policies = reactive<Record<string, ReviewApi.ReviewPolicy>>({})
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, projectId: props.projectId ?? '', name: '', status: undefined })
const formVisible = ref(false)
const detailLoading = ref(false)
// 首次引导（分页→自动选中→详情/草稿）未完成前正文不渲染：加载目标未确定就不显示方案内容
const bootstrapPending = ref(props.projectId != null)
// 正文就绪：引导与详情加载均结束才渲染章节表单/错误提示
const contentReady = computed(() => !bootstrapPending.value && !detailLoading.value)
// 加载占位高度冻结为上次工作台（工具栏+正文两卡）总高：切换方案时占位不塌缩，新内容就绪后高度平滑衔接。
// 就绪期间用 ResizeObserver 跟随实际高度（富文本编辑器异步撑开，一次性测量会偏小）
const bodyCardRef = ref()
const bodyMinHeight = ref(0)
let bodyResizeObserver: ResizeObserver | undefined
watch(contentReady, async (ready) => {
  if (!ready) {
    bodyResizeObserver?.disconnect()
    return
  }
  await nextTick()
  const el = bodyCardRef.value?.$el as HTMLElement | undefined
  const host = el?.parentElement
  if (!host) return
  bodyResizeObserver?.disconnect()
  bodyResizeObserver = new ResizeObserver(() => {
    if (host.offsetHeight) bodyMinHeight.value = host.offsetHeight
  })
  bodyResizeObserver.observe(host)
})
const detailError = ref('')
let detailSequence = 0
const formRef = ref()
const form = ref<SolutionVO>({ projectId: props.projectId ?? 0, name: '', reviewLevel: 0, status: 0 })
const editableDraft = (row: SolutionVO) => row.status === 0 && checkPermi(['pms:sol-solution:update'])
const readOnly = computed(() => detailLoading.value || !!detailError.value || (form.value.id ? !editableDraft(form.value) : !checkPermi(['pms:sol-solution:create'])))
const rules = {
  projectId: [
    { required: true, message: '请选择项目' },
    { validator: (_rule: unknown, value: number | string, callback: (error?: Error) => void) => callback(Number(value) > 0 ? undefined : new Error('请选择项目')) }
  ],
  name: [{ required: true, message: '请输入方案名称' }]
}

// 方案名称规范：项目编码_项目名称_实施方案_(版本标签|年月日时分秒)；用户改过名称后不再自动覆盖
const projectInfo = ref<ProjectMasterVO>()
const lastAutoName = ref('')
const applyAutoName = () => {
  if (readOnly.value || form.value.id || !projectInfo.value) return
  const name = buildRecordName({
    projectCode: projectInfo.value.projectCode,
    projectName: projectInfo.value.projectName,
    typeLabel: '实施方案',
    version: form.value.versionLabel
  })
  if (!form.value.name || form.value.name === lastAutoName.value) {
    form.value.name = name
    lastAutoName.value = name
  }
}
watch([projectInfo, () => form.value.versionLabel], applyAutoName)
watch(
  () => form.value.projectId,
  async (pid) => {
    const id = Number(pid)
    if (!id || id <= 0) return
    try {
      const project = await ProjectApi.getProject(id)
      if (Number(form.value.projectId) === id) projectInfo.value = project
    } catch {
      // 项目信息获取失败时保持手输名称
    }
  },
  { immediate: true }
)

const solutionLabel = (row: SolutionVO) => `${row.name}（${row.versionLabel || '未设版本'}）`
const pickSolution = (id: number | string) => {
  const row = rows.value.find(item => item.id === Number(id))
  if (row) openForm(row)
}

const load = async () => {
  loading.value = true
  try {
    const data = await SolutionApi.getSolutionPage(query)
    rows.value = data.list
    total.value = data.total
    const projects = new Set<number>(data.list.map((row: SolutionVO) => row.projectId))
    if (props.projectId) projects.add(props.projectId)
    await Promise.all([...projects].map(async projectId => {
      try { policies[projectId] = await ReviewApi.policy(projectId) }
      catch { policies[projectId] = { configured: true, reviewLevel: null, reason: '审核判定加载失败，请刷新后重试。' } }
    }))
    // 工作台没有列表态：加载后保持选中并刷新当前方案状态；无方案时进入新建草稿
    if (props.projectId) await syncSelection()
  } catch {
    // 拦截器已弹后端错误提示；保留既有列表/选中态，吞掉 rejection 防止逃逸到业务视图错误边界
  } finally {
    loading.value = false
    bootstrapPending.value = false
  }
}
const syncSelection = async () => {
  if (!props.projectId) return
  const currentId = form.value.id
  const row = (currentId ? rows.value.find(item => item.id === currentId) : null) ?? rows.value[0]
  if (row) await openForm(row)
  else {
    // 项目暂无方案：进入新建草稿态时同样走新增弹窗补齐客户方案登记
    await openForm()
    openCreate()
  }
}

// 新增方案弹窗：客户方案在创建时一次性选定并随记录冻结；确定后进入自动命名的本地草稿，
// 保存仍由保存草稿/提交审核承载。createChoice 两键写入 remark 信封（META_REMARK_KEYS 归属方案信息组件）
const createVisible = ref(false)
const createChoice = reactive({ hasCustomerPlan: 'no', customerPlanUrl: '' })
const openCreate = () => {
  createChoice.hasCustomerPlan = 'no'
  createChoice.customerPlanUrl = ''
  createVisible.value = true
}
const parseEnvelope = (raw: unknown): Record<string, unknown> => {
  try {
    const v = raw ? JSON.parse(String(raw)) : null
    return v && typeof v === 'object' && !Array.isArray(v) ? v : {}
  } catch {
    return {}
  }
}
const confirmCreate = () => {
  createVisible.value = false
  openForm()
  form.value.remark = JSON.stringify({
    ...parseEnvelope(form.value.remark),
    hasCustomerPlan: createChoice.hasCustomerPlan,
    customerPlanUrl: createChoice.customerPlanUrl
  })
}
const openForm = async (row?: SolutionVO) => {
  const sequence = ++detailSequence
  detailError.value = ''
  detailLoading.value = !!row?.id
  // Replace the whole object so a previous reviewed record cannot leak its
  // status, baseline or approval metadata into a newly created draft.
  form.value = {
      projectId: props.projectId ?? 0,
      name: '',
      solutionType: 'IMPLEMENTATION',
      background: '',
      target: '',
      team: '',
      inventory: '',
      plan: '',
      topology: '',
      interfacePlan: '',
      ipPlan: '',
      versionLabel: '',
      script: '',
      quality: '',
      risk: '',
      oAndM: '',
      reviewLevel: 0,
      remark: '',
      version: undefined,
      status: 0,
      id: row?.id
  }
  if (!props.projectId) formVisible.value = true
  if (!row?.id) {
    // 工作台新建草稿：版本标签按项目内方案版本自动顺延（V+序数），界面不再提供录入入口
    if (props.projectId) form.value.versionLabel = `V${total.value + 1}`
    applyAutoName()
    return
  }
  try {
    const detail: SolutionVO | null = await SolutionApi.getSolution(row.id)
    if (sequence !== detailSequence) return
    if (!detail || detail.id !== row.id) {
      detailError.value = '方案不存在或已不可见，请刷新后重试'
      return
    }
    form.value = detail
  } catch {
    if (sequence === detailSequence) detailError.value = '方案详情加载失败，请重试；当前不能保存'
  } finally {
    if (sequence === detailSequence) detailLoading.value = false
  }
}
watch(formVisible, visible => { if (!visible) ++detailSequence })
onBeforeUnmount(() => {
  ++detailSequence
  bodyResizeObserver?.disconnect()
})
const save = async () => {
  if (readOnly.value) return
  // 校验拒绝留在表单内提示；API 失败由拦截器弹错并在此吞掉 rejection，逃逸会进入业务视图错误边界，整块组件被替换成不可用回退。
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    form.value.id ? await SolutionApi.updateSolution(form.value) : await SolutionApi.createSolution(form.value)
    message.success('保存成功')
    if (!props.projectId) formVisible.value = false
    await load()
  } catch {
    // 保存失败保留表单内容与弹窗状态，便于修正后重试；提示已由拦截器弹出
  } finally {
    saving.value = false
  }
}
// 提交审批：唯一提交入口——保存后统一弹出审批发起框选择候选人；流程内分支网关按提交时冻结的审核级别路由（服务经理审核，重大项目由工程管理部复审），界面不再区分级别
const submitReview = async () => {
  if (readOnly.value) return
  if (formRef.value?.validate) {
    try { await formRef.value.validate() } catch { return }
  }
  saving.value = true
  try {
    if (!form.value.id) form.value.id = (await SolutionApi.createSolution(form.value)) as unknown as number
    else await SolutionApi.updateSolution(form.value)
    await load()
    tieredReview.value?.open(form.value)
  } catch {
    // 同 save：保存/刷新失败不打开审批框，保留表单与工作区，提示已由拦截器弹出
  } finally {
    saving.value = false
  }
}
const remove = async (row: SolutionVO) => {
  if (row.status !== 0) return
  await message.delConfirm()
  try {
    await SolutionApi.deleteSolution(row.id!)
    message.success('删除成功')
    await load()
  } catch {
    // 拦截器已弹后端错误提示；吞掉 rejection 防止逃逸到业务视图错误边界，工作区保持当前状态
  }
}
onMounted(load)
</script>

<style scoped>
.workbench-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.workbench-label {
  font-weight: 600;
  white-space: nowrap;
}
/* 引导期整块骨架占位的最小高度：与就绪后工具栏+正文首屏量级接近，占位不显得塌陷 */
.workbench-boot-skeleton {
  min-height: 140px;
}
.create-tip {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
