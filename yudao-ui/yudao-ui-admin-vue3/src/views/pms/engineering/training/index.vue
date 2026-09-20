<template>
  <ContentWrap :body-style="{ padding: '20px', overflow: 'hidden' }" class="training-content">
    <header class="training-heading"><h2>现场培训</h2><p>记录培训内容，邀请客户评价并签字确认。</p></header>
    <el-form :model="listQuery" inline class="-mb-15px">
      <el-form-item v-if="!props.projectId" label="项目" prop="projectId">
        <PmsEntitySelect
          v-model="listQuery.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="listQuery.name" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="listQuery.status" clearable class="!w-140px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button v-hasPermi="['pms:dynamic-form-template:query']" @click="printTemplateManager?.open()">打印模板配置</el-button>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:imp-training:create']"
          ><Icon icon="ep:plus" />新建培训记录</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap :body-style="{ padding: '20px', overflow: 'hidden' }">
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="name" label="培训名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="trainingTypes" label="培训类型" min-width="160">
        <template #default="{ row }">
          <el-tag
            v-for="type in (row.trainingTypes || '').split(',')"
            :key="type"
            class="mr-4px"
            size="small"
            >{{ typeLabel(type) }}</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column prop="trainingTime" label="培训时间" width="110" />
      <el-table-column prop="trainerName" label="培训工程师" width="110" />
      <el-table-column prop="contactName" label="客户联系人" width="110" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="客户确认" min-width="180">
        <template #default="{ row }">
          <span v-if="row.status === 2">
            {{ row.signConfirmerName }} · 已签字确认
          </span>
          <span v-else-if="row.status === 1" class="text-gray-400">待客户确认（有效期至 {{ formatDate(row.tokenExpiresAt) }}）</span>
          <span v-else class="text-gray-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="350" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          <el-button
            link
            type="primary"
            v-if="row.status === 0"
            @click="openForm(row)"
            v-hasPermi="['pms:imp-training:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0 || row.status === 1"
            @click="issue(row)"
            v-hasPermi="['pms:imp-training:issue']"
            >{{ row.status === 1 ? '重新外发' : '推送至客户' }}</el-button
          >
          <el-button link type="primary" :disabled="row.status === 3" :loading="downloadingIds.has(row.id!)" @click="downloadRecord(row)">{{ downloadingIds.has(row.id!) ? '准备下载中' : '下载 PDF' }}</el-button>
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 1"
            @click="voidRow(row)"
            v-hasPermi="['pms:imp-training:update']"
            >作废</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0"
            @click="remove(row)"
            v-hasPermi="['pms:imp-training:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="listQuery.pageNo" v-model:limit="listQuery.pageSize" @pagination="load" />
  </ContentWrap>

  <TrainingPrintPreview ref="printPreview" />
  <TrainingPrintTemplates ref="printTemplateManager" @changed="loadPrintTemplates" />

  <!-- 新建/编辑对话框 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑培训记录' : '新建培训记录'" width="min(860px, 94vw)">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-row :gutter="16">
        <el-col v-if="!props.projectId" :xs="24" :sm="12">
          <el-form-item label="项目" prop="projectId">
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
        </el-col>
        <el-col :span="24">
          <el-form-item label="培训名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="客户联系人" prop="contactName">
            <el-input v-model="form.contactName" placeholder="可从项目联系人带入">
              <template #append>
                <el-button :disabled="!form.projectId" @click="pickContact">带入</el-button>
              </template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="form.contactPhone" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="培训类型" prop="trainingTypeList">
            <el-checkbox-group v-model="form.trainingTypeList">
              <el-checkbox
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_TRAINING_TYPE)"
                :key="dict.value"
                :value="dict.value"
                >{{ dict.label }}</el-checkbox
              >
            </el-checkbox-group>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="培训时间" prop="trainingTime">
            <el-date-picker
              v-model="form.trainingTime"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择培训时间"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="培训工程师" prop="trainerUserId">
            <PmsEntitySelect
              v-model="form.trainerUserId"
              :api="UserApi.getUserPage"
              label-field="nickname"
              value-field="id"
              query-field="nickname"
              placeholder="默认当前登录人"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="参训人数" prop="traineeCount">
            <el-input-number v-model="form.traineeCount" :min="1" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="培训内容" prop="content">
            <el-input v-model="form.content" type="textarea" :rows="4" placeholder="手动填写培训内容" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="客户确认模板" prop="confirmationTemplateId">
            <el-select v-model="form.confirmationTemplateId" class="!w-full" placeholder="请选择已发布的客户确认模板" @visible-change="visible => visible && loadConfirmationTemplates()">
              <el-option v-for="item in confirmationTemplates" :key="item.templateId" :value="item.templateId" :label="`${item.templateName} · 修订 ${item.currentPublishedRevisionNo}`" />
            </el-select>
            <div class="template-hint">外发时冻结已发布版本。后续修改模板不会影响已外发记录。
              <el-button v-hasPermi="['pms:dynamic-form-template:query']" link type="primary" @click="openTemplateManagement">统一模板配置</el-button>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="打印模板" prop="printTemplateId">
            <el-select v-model="form.printTemplateId" class="!w-full" placeholder="请选择已发布的打印模板" @visible-change="visible => visible && loadPrintTemplates()">
              <el-option v-if="form.printTemplateId && !printTemplates.some(item => item.templateId === form.printTemplateId)" :value="form.printTemplateId" label="已绑定历史打印模板（保留原版式）" disabled />
              <el-option v-for="item in printTemplates" :key="item.templateId" :value="item.templateId" :label="`${item.templateName} · 修订 ${item.currentPublishedRevisionNo}`" />
            </el-select>
            <div class="template-hint">保存时绑定已发布版式。客户确认模板和打印模板分别配置。</div>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" /></el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <!-- 外发结果对话框 -->
  <Dialog v-model="issueVisible" title="培训记录已外发" width="min(640px, 94vw)">
    <el-alert type="warning" :closable="false" :title="issueResult?.notice" />
    <div v-if="publicUrl" class="confirmation-qr">
      <Qrcode :text="publicUrl" :width="200" @done="url => qrImage = url" />
      <p>客户扫码后可直接填写评价并手写签字</p>
      <a v-if="qrImage" :href="qrImage" download="培训确认二维码.png">保存二维码</a>
    </div>
    <el-form label-position="top" class="mt-12px">
      <el-form-item label="客户可访问的系统地址">
        <el-input v-model="publicBaseUrl" placeholder="例如 https://pms.example.com" />
        <span class="template-hint">使用手机可访问的域名或局域网地址；localhost 和 127.0.0.1 无法从另一台手机访问。</span>
      </el-form-item>
    </el-form>
    <el-descriptions :column="1" border class="mt-12px">
      <el-descriptions-item label="确认链接">
        <el-link type="primary" :href="publicUrl" target="_blank">{{ publicUrl }}</el-link>
      </el-descriptions-item>
      <el-descriptions-item label="令牌有效期">{{ formatDate(issueResult?.tokenExpiresAt) }}</el-descriptions-item>
      <el-descriptions-item label="培训记录表">
        <el-link v-if="issueResult?.fileUrl" type="primary" :href="issueResult?.fileUrl" target="_blank"
          >查看已生成文件</el-link
        >
      </el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button v-if="publicUrl" @click="copyLink">复制链接</el-button>
      <el-button type="primary" @click="issueVisible = false">关闭</el-button>
    </template>
  </Dialog>
  <el-drawer v-model="detailVisible" title="培训记录" size="min(720px, 96vw)">
    <el-skeleton v-if="detailLoading" :rows="10" animated aria-label="正在加载培训记录" />
    <el-result v-else-if="detailError" icon="error" title="培训记录加载失败" sub-title="请重试加载当前记录">
      <template #extra><el-button type="primary" @click="detailRow && openDetail(detailRow)">重新加载</el-button></template>
    </el-result>
    <div class="training-detail" v-else-if="detail">
      <h2>{{ detail.name }}</h2>
      <el-tag :type="statusTagType(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
      <h3>基本信息</h3>
      <dl class="training-facts">
        <div><dt>培训类型</dt><dd>{{ (detail.trainingTypes || '').split(',').filter(Boolean).map(typeLabel).join('、') || '—' }}</dd></div>
        <div><dt>培训时间</dt><dd>{{ detail.trainingTime || '—' }}</dd></div>
        <div><dt>培训工程师</dt><dd>{{ detail.trainerName || '—' }}</dd></div>
        <div><dt>参训人数</dt><dd>{{ detail.traineeCount ?? '—' }}</dd></div>
        <div><dt>客户联系人</dt><dd>{{ detail.contactName || '—' }}</dd></div>
        <div><dt>联系电话</dt><dd>{{ detail.contactPhone || '—' }}</dd></div>
      </dl>
      <h3>培训内容</h3><p class="training-text">{{ detail.content || '未填写' }}</p>
      <h3>客户评价与确认</h3>
      <template v-if="detail.status === 2">
        <dl class="training-facts">
          <div><dt>培训技能</dt><dd>{{ detail.skillRating || '—' }}</dd></div>
          <div><dt>培训效果</dt><dd>{{ detail.effectRating || '—' }}</dd></div>
          <div><dt>满意程度</dt><dd>{{ detail.satisfactionRating || '—' }}</dd></div>
          <div><dt>客户确认人</dt><dd>{{ detail.signConfirmerName || '—' }}</dd></div>
          <div><dt>确认时间</dt><dd>{{ formatDate(detail.signTime) }}</dd></div>
        </dl>
        <div class="training-opinion"><span>改进意见</span><p class="training-text">{{ detail.signOpinion || '客户未填写改进意见' }}</p></div>
        <dl v-if="additionalAnswers.length" class="training-facts">
          <div v-for="item in additionalAnswers" :key="item.field"><dt>{{ item.title }}</dt><dd>{{ item.value }}</dd></div>
        </dl>
      </template>
      <el-empty v-else description="尚无客户评价与签字确认" :image-size="60" />
      <section v-if="detail.signatureImageDataUrl" class="training-signature">
        <h3>客户手写签字</h3>
        <img :src="detail.signatureImageDataUrl" alt="客户手写签字" />
        <el-button tag="a" :href="detail.signatureImageDataUrl" download="客户手写签字.png" link type="primary">保存签字图片</el-button>
      </section>
      <template v-if="detail.remark"><h3>备注</h3><p class="training-text">{{ detail.remark }}</p></template>
    </div>
    <template #footer>
      <div class="training-detail-actions">
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!detail || detailLoading || detail.status === 3" :loading="!!detail && downloadingIds.has(detail.id!)" @click="detail && downloadRecord(detail)">
          <Icon v-if="!detail || !downloadingIds.has(detail.id!)" icon="ep:download" />
          {{ detail && downloadingIds.has(detail.id!) ? '准备下载中' : '下载 PDF' }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import TrainingPrintTemplates from './TrainingPrintTemplates.vue'
import TrainingPrintPreview from './TrainingPrintPreview.vue'
import { PRINT_CATEGORY } from './trainingPrintForm'
import * as TrainingApi from '@/api/pms/engineering/training'
import type { TrainingVO } from '@/api/pms/engineering/training'
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserApi from '@/api/system/user'
import * as ContactApi from '@/api/pms/customer/contacts'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import { getTenantId } from '@/utils/auth'
import { useUserStore } from '@/store/modules/user'
import { Qrcode } from '@/components/Qrcode'
import * as DynamicFormApi from '@/api/pms/platform/dynamic-form'

defineOptions({ name: 'PmsImpTraining' })

// 项目详情页嵌入模式：传入 project-id 固定项目并隐藏项目选择；独立路由页行为不变
const props = defineProps<{ projectId?: number }>()

const message = useMessage()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const rows = ref<TrainingVO[]>([])
const total = ref(0)
const listQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: props.projectId ?? (route.query.projectId ? Number(route.query.projectId) : undefined),
  name: (route.query.name as string) || '',
  status: route.query.status !== undefined ? Number(route.query.status) : (undefined as number | undefined)
})

const statusOptions = [
  { value: 0, label: '草稿' },
  { value: 1, label: '已外发' },
  { value: 2, label: '客户已确认' },
  { value: 3, label: '已作废' }
]
const statusLabel = (status?: number) => statusOptions.find((item) => item.value === status)?.label ?? ''
const statusTagType = (status?: number) =>
  status === 2 ? 'success' : status === 3 ? 'info' : status === 1 ? 'warning' : 'primary'
const typeLabel = (type: string) =>
  getStrDictOptions(DICT_TYPE.PMS_TRAINING_TYPE).find((dict) => dict.value === type)?.label ?? type

const load = async () => {
  loading.value = true
  try {
    const data = await TrainingApi.getTrainingPage(listQuery as any)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const printPreview = ref<InstanceType<typeof TrainingPrintPreview>>()
const printTemplateManager = ref<InstanceType<typeof TrainingPrintTemplates>>()
const printTemplates = ref<DynamicFormApi.DynamicFormSelectionVO[]>([])
const loadPrintTemplates = async () => {
  const all: DynamicFormApi.DynamicFormSelectionVO[] = []
  for (let pageNo = 1; ; pageNo++) {
    const page = await DynamicFormApi.getTemplateSelection({ pageNo, pageSize: 100 })
    all.push(...page.list)
    if (all.length >= page.total || !page.list.length) break
  }
  printTemplates.value = all.filter(item => item.categoryCode === PRINT_CATEGORY)
}
const confirmationTemplates = ref<DynamicFormApi.DynamicFormSelectionVO[]>([])
const loadConfirmationTemplates = async () => {
  const result = await DynamicFormApi.getTemplateSelection({ pageNo: 1, pageSize: 100 })
  confirmationTemplates.value = result.list.filter(item => item.categoryCode === 'CUSTOMER_CONFIRMATION')
}
const router = useRouter()
const openTemplateManagement = () => {
  const target = router.getRoutes().find(item => item.name === 'PmsDynamicFormTemplate')
  if (target) window.open(router.resolve({ name: target.name }).href, '_blank')
  else message.warning('当前账号未配置动态表单模板菜单，请联系管理员')
}
const formVisible = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref(false)
const detailRow = ref<TrainingVO>()
let detailRequest = 0
const downloadingIds = reactive(new Set<number>())
const detail = ref<TrainingVO>()
const additionalAnswers = computed(() => {
  if (!detail.value?.confirmationFormRules || !detail.value.confirmationValues) return []
  const coreFields = new Set(['skillRating', 'effectRating', 'satisfactionRating', 'signOpinion', 'signConfirmerName', 'signatureImageDataUrl'])
  const rules = JSON.parse(detail.value.confirmationFormRules) as Array<{ field: string; title: string; options?: Array<{ label: string; value: string }> }>
  const values = JSON.parse(detail.value.confirmationValues)
  return rules.filter(rule => !coreFields.has(rule.field) && values[rule.field] != null).map(rule => ({
    field: rule.field, title: rule.title,
    value: (Array.isArray(values[rule.field]) ? values[rule.field] : [values[rule.field]])
      .map((value: unknown) => rule.options?.find(option => option.value === value)?.label ?? String(value)).join('、')
  }))
})
const openDetail = async (row: TrainingVO) => {
  const request = ++detailRequest
  detailRow.value = row
  detailError.value = false
  detail.value = undefined
  detailVisible.value = true
  detailLoading.value = true
  try {
    const record = await TrainingApi.getTraining(row.id!)
    if (request === detailRequest) detail.value = record
  } catch {
    if (request === detailRequest) detailError.value = true
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
}
const saving = ref(false)
const formRef = ref()
const form = reactive<Record<string, any>>({})
const rules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  name: [{ required: true, message: '请填写培训名称', trigger: 'blur' }],
  trainingTypeList: [{ required: true, type: 'array', min: 1, message: '请选择培训类型', trigger: 'change' }],
  trainingTime: [{ required: true, message: '请选择培训时间', trigger: 'change' }]
}
const openForm = (row?: TrainingVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: undefined,
      name: '',
      contactName: '',
      contactPhone: '',
      trainingTypeList: [],
      trainingTime: '',
      trainerUserId: userStore.getUser.id || undefined,
      traineeCount: undefined,
      content: '',
      remark: '',
      confirmationTemplateId: 992209200001,
      printTemplateId: printTemplates.value[0]?.templateId,
      version: undefined
    },
    row
      ? {
          ...row,
          trainingTypeList: (row.trainingTypes || '').split(',').filter(Boolean)
        }
      : {}
  )
  if (props.projectId) form.projectId = props.projectId
  formVisible.value = true
  void loadConfirmationTemplates()
  void loadPrintTemplates().then(() => {
    if (formVisible.value && !form.id && !form.printTemplateId) form.printTemplateId = printTemplates.value[0]?.templateId
  })
}
const pickContact = async () => {
  const data = await ContactApi.getProjectPage(form.projectId!, { pageNo: 1, pageSize: 50 })
  if (!data.list?.length) {
    message.warning('该项目暂无客户联系人，请手动填写')
    return
  }
  const primary = data.list.find((item: any) => item.primaryFlag) ?? data.list[0]
  form.contactName = primary.name
  form.contactPhone = primary.mobile || primary.phone || ''
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    // 后端 SaveReqVO.trainingTypes 为 List<String>，回显时再由逗号串拆分
    const { trainingTypeList, ...rest } = form
    const payload = { ...rest, trainingTypes: trainingTypeList || [] } as unknown as TrainingApi.TrainingVO
    form.id ? await TrainingApi.updateTraining(payload) : await TrainingApi.createTraining(payload)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const issueVisible = ref(false)
const issueResult = ref<TrainingApi.TrainingIssueVO>()
const qrImage = ref('')
const publicBaseUrl = ref(import.meta.env.VITE_CUSTOMER_CONFIRM_BASE_URL || window.location.origin)
const publicUrl = computed(() => {
  if (!issueResult.value) return ''
  try {
    const base = new URL(publicBaseUrl.value)
    if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password) return ''
    return `${base.origin}${issueResult.value.signPath}?tenantId=${getTenantId() ?? 0}`
  } catch { return '' }
})
const issue = async (row: TrainingVO) => {
  await message.confirm(
    row.status === 1
      ? '重新外发将生成新链接，原链接立即失效，确认继续？'
      : '将生成客户确认链接与培训记录表文件，确认外发？'
  )
  issueResult.value = await TrainingApi.issueTraining(row.id!)
  issueVisible.value = true
  await load()
}

const copyLink = async () => {
  await navigator.clipboard.writeText(publicUrl.value)
  message.success('链接已复制')
}

const downloadRecord = async (row: TrainingVO) => {
  const id = row.id!
  if (downloadingIds.has(id)) return
  downloadingIds.add(id)
  try {
    const record = await TrainingApi.getTraining(id)
    if (record.status === 3) {
      message.warning('已作废的培训记录不允许下载 PDF')
      return
    }
    const snapshot = record.printLayoutSnapshot ? JSON.parse(record.printLayoutSnapshot) : undefined
    const project = record.projectId ? await ProjectApi.getProject(record.projectId) : undefined
    await printPreview.value?.open(record, project?.projectName || '', snapshot)

  } catch {
    message.error('培训记录 PDF 下载失败，请重试')
  } finally {
    downloadingIds.delete(id)
  }
}

const voidRow = async (row: TrainingVO) => {
  await message.confirm(`确认作废培训记录【${row.name}】？作废后确认链接即刻失效。`)
  await TrainingApi.voidTraining(row.id!)
  message.success('作废成功')
  await load()
}
const remove = async (row: TrainingVO) => {
  await message.delConfirm()
  await TrainingApi.deleteTraining(row.id!)
  message.success('删除成功')
  await load()
}

onMounted(load)
</script>

<style scoped>
.training-heading { margin-bottom: 20px; }
.training-heading h2, .training-detail h2 { margin: 0 0 6px; font-size: 16px; font-weight: 600; }
.training-heading p { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); }
.training-facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px 24px; margin: 24px 0; }
.training-facts dt { color: var(--el-text-color-secondary); font-size: 13px; margin-bottom: 6px; }
.training-facts dd { margin: 0; overflow-wrap: anywhere; }
.training-detail h3 { border-top: 1px solid var(--el-border-color-lighter); padding-top: 20px; font-size: 14px; }
.training-text { white-space: pre-wrap; line-height: 1.7; overflow-wrap: anywhere; }
.training-detail h2 { overflow-wrap: anywhere; line-height: 1.5; }
.training-detail-actions { display: flex; justify-content: flex-end; gap: 12px; border-top: 1px solid var(--el-border-color-lighter); padding-top: 16px; }
.training-detail-actions .el-button + .el-button { margin-left: 0; }
.training-opinion { padding: 16px; background: var(--el-fill-color-light); border-radius: 4px; }
.training-opinion > span { color: var(--el-text-color-secondary); font-size: 13px; }
.training-opinion p { margin: 8px 0 0; }
.training-signature img { display: block; width: 100%; max-height: 200px; object-fit: contain; margin: 12px 0; border: 1px solid var(--el-border-color-lighter); background: white; }
@media (max-width: 600px) { .training-facts { grid-template-columns: 1fr; } }
</style>

<style scoped>
.template-hint { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.7; margin-top: 8px; }
.confirmation-qr { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 20px 0; }
.confirmation-qr p { margin: 0; color: var(--el-text-color-regular); }
.confirmation-qr a { color: var(--el-color-primary); }
</style>
