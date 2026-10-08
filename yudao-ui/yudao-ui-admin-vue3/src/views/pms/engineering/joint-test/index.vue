<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item v-if="!props.projectId" label="项目编号" prop="projectId">
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
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_JOINT_TEST_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button v-if="!readonly" type="primary" @click="openForm()" v-hasPermi="['pms:imp-joint-test:create']"
          ><Icon icon="ep:plus" />新增联调</el-button
        >
      </el-form-item>
    </el-form>
    <el-alert title="联调记录可直接输入命令或选用已发布模板采集设备日志；日志自动回传到联调记录，联调结果和通过操作仍按原流程确认。自动对比未接入。" type="info" :closable="false" />
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="testCase" label="联调用例" min-width="200" show-overflow-tooltip><template #default="{ row }"><div v-dompurify-html="row.testCase" class="max-h-60px overflow-hidden"></div></template></el-table-column>
      <el-table-column prop="equipmentId" label="设备编号" width="100">
        <template #default="{ row }">
          <DeviceTag :device-id="row.equipmentId" />
        </template>
      </el-table-column>
      <el-table-column prop="testTime" label="联调时间" width="160" :formatter="dateFormatter" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_JOINT_TEST_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="470" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:imp-joint-test:query']"
            >{{ editableRecord(row) && !readonly ? '编辑' : '查看' }}</el-button
          >
          <el-button link type="primary" @click="collection?.open(row.id!)" v-hasPermi="['pms:imp-joint-test:query']">命令采集与日志</el-button>
          <el-button
            link
            type="success"
            v-if="!readonly && row.status === 0"
            @click="handleAction(row, 'start')"
            v-hasPermi="['pms:imp-joint-test:update']"
            >开始联调</el-button
          >
          <el-button
            link
            type="success"
            v-if="!readonly && row.status === 1"
            @click="handleAction(row, 'pass')"
            v-hasPermi="['pms:imp-joint-test:update']"
            >联调通过</el-button
          >
          <el-button
            link
            type="danger"
            v-if="!readonly && row.status === 1"
            @click="handleFail(row)"
            v-hasPermi="['pms:imp-joint-test:update']"
            >联调失败</el-button
          >
          <el-button v-if="!readonly && (row.status === 0 || row.status === 1)" link type="danger" @click="remove(row)" v-hasPermi="['pms:imp-joint-test:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? (readOnly ? '查看联调' : '编辑联调') : '新增联调'" width="min(880px, 95vw)">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" :disabled="readOnly">
      <div class="section-title">基础信息</div>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="项目编号" prop="projectId">
            <PmsEntitySelect
              v-model="form.projectId"
              @change="form.equipmentId = undefined"
              :api="ProjectApi.getProjectPage"
              label-field="projectName"
              value-field="id"
              query-field="projectName"
              placeholder="请选择项目"
              :disabled="!!form.id || !!props.projectId"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="关联设备" prop="equipmentId">
            <ProjectDeviceSelect v-model="form.equipmentId" :project-id="form.projectId" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联调人" prop="testerUserId"><el-input v-model="form.testerUserId" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联调时间" prop="testTime">
            <el-date-picker v-model="form.testTime" type="datetime" value-format="x" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="参与方" prop="participants"><el-input v-model="form.participants" /></el-form-item>
        </el-col>
      </el-row>

      <div class="section-title">设备登录信息（联调采集入口）</div>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="设备名称"><el-input v-model="deviceLogin.deviceName" placeholder="临时命名，手动输入" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备IP"><el-input v-model="deviceLogin.deviceIp" placeholder="手动输入" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="登录用户名"><el-input v-model="deviceLogin.username" placeholder="手动输入" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="登录方式">
            <el-radio-group v-model="deviceLogin.loginType">
              <el-radio value="SSH">SSH</el-radio>
              <el-radio value="TELNET">Telnet</el-radio>
              <el-radio value="SERIAL">串口</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="端口号"><el-input v-model="deviceLogin.port" placeholder="手动输入" /></el-form-item>
        </el-col>
        <el-col v-if="deviceLogin.loginType === 'SERIAL'" :span="12">
          <el-form-item label="波特率"><el-input v-model="deviceLogin.baudRate" placeholder="串口模式下填写" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label-width="110px">
            <el-button v-if="form.id" @click="collection?.open(form.id!)" v-hasPermi="['pms:imp-joint-test:query']">命令采集与日志</el-button>
            <span class="form-tip">请先保存业务记录，再从统一采集窗口输入命令或选择模板，并选择认证方式。上述非秘密连接信息仍可作为记录备注保存。</span>
          </el-form-item>
        </el-col>
      </el-row>

      <div class="section-title">设备清单</div>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="设备型号"><el-input :model-value="deviceInfo?.productModel || ''" readonly placeholder="选择关联设备后带入" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="序列号"><el-input :model-value="deviceInfo?.sn || ''" readonly placeholder="选择关联设备后带入" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="运行业务描述"><el-input v-model="jointMeta.businessDesc" placeholder="手动填写运行业务描述" /></el-form-item>
        </el-col>
      </el-row>

      <div class="section-title">联调记录</div>
      <el-row :gutter="16">
        <el-col :span="24">
          <el-form-item label="联调用例" prop="testCase">
            <Editor v-model="form.testCase" height="200px" :readonly="readOnly" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="联调结果" prop="result">
            <Editor v-model="form.result" height="200px" :readonly="readOnly" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="证据附件" prop="evidenceUrl"><NativeAttachments :key="String(form.id || 'new')" kind="jointTest" :entity-id="form.id"
              :readonly="readOnly" :legacy="form.evidenceUrl" @changed="attachmentOwnerChanged" /></el-form-item>
        </el-col>
        <el-col v-if="form.exceptionRecord" :span="24">
          <el-form-item label="异常记录"><el-input :model-value="form.exceptionRecord" type="textarea" :rows="4" readonly data-testid="joint-test-exception" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="jointMeta.note" type="textarea" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <BusinessCollectionLogs v-if="formVisible && form.id" ref="businessLogs" entry="joint-test" :object-id="form.id" />
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <Dialog v-model="failVisible" title="联调失败-记录异常" width="min(540px, 95vw)">
    <el-form :model="failForm" label-width="100px">
      <el-form-item label="异常记录" required>
        <el-input v-model="failForm.exceptionRecord" type="textarea" :rows="4" placeholder="失败项不能静默通过，必须记录异常或创建问题单" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="failVisible = false">取消</el-button>
      <el-button type="danger" :loading="saving" @click="confirmFail">确认失败</el-button>
    </template>
  </Dialog>
  <CollectionDialog ref="collection" entry="joint-test" @closed="businessLogs?.reload()" />
</template>

<script setup lang="ts">
import NativeAttachments from '../attachment/NativeAttachments.vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as JointTestApi from '@/api/pms/engineering/joint-test'
import type { JointTestVO } from '@/api/pms/engineering/joint-test'
import * as ProjectApi from '@/api/pms/project/projects'
import ProjectDeviceSelect from '@/components/ProjectDeviceSelect/index.vue'
import CollectionDialog from '@/components/DeviceCollection/CollectionDialog.vue'
import BusinessCollectionLogs from '@/components/DeviceCollection/BusinessCollectionLogs.vue'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import DeviceTag from '@/components/DeviceTag/index.vue'
import { checkPermi } from '@/utils/permission'
import { dateFormatter } from '@/utils/formatTime'

defineOptions({ name: 'PmsEngJointTest' })
const props = defineProps<{ projectId?: number; /** 内嵌于未进入阶段的任务工作区时强制只读：隐藏业务写操作，查看照常 */ readonly?: boolean }>()
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<JointTestVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, projectId: props.projectId ?? '', status: undefined })
const formVisible = ref(false)
const formRef = ref()
type JointTestForm = Omit<JointTestVO, 'testTime'> & { testTime?: number | string | null }
const form = ref<JointTestForm>({ projectId: props.projectId ?? 0, testCase: '', status: 0 })

// Demo 5.4 设备登录信息与运行业务描述：随联调记录 remark 以 JSON 信封保存（密码除外，密码不落库不传输）
interface DeviceLoginMeta {
  deviceName: string
  deviceIp: string
  username: string
  loginType: string
  port: string
  baudRate: string
}
const deviceLogin = reactive<DeviceLoginMeta>({ deviceName: '', deviceIp: '', username: '', loginType: 'SSH', port: '', baudRate: '' })
const collection = ref<InstanceType<typeof CollectionDialog>>()
const businessLogs = ref<InstanceType<typeof BusinessCollectionLogs>>()
const jointMeta = reactive({ businessDesc: '', note: '' })
const deviceInfo = ref<DeviceArchiveApi.DeviceArchiveVO | null>(null)
const parseRemark = (raw: string | undefined | null) => {
  try {
    const v = raw ? JSON.parse(raw) : null
    if (v && typeof v === 'object' && !Array.isArray(v)) return v as Record<string, unknown>
  } catch {
    // 历史纯文本备注降级为 note
  }
  return { note: raw || '' }
}
const syncMetaFromForm = () => {
  const parsed = parseRemark(form.value.remark)
  const login = (parsed['deviceLogin'] ?? {}) as Partial<DeviceLoginMeta>
  Object.assign(deviceLogin, {
    deviceName: String(login.deviceName ?? ''),
    deviceIp: String(login.deviceIp ?? ''),
    username: String(login.username ?? ''),
    loginType: String(login.loginType ?? 'SSH'),
    port: String(login.port ?? ''),
    baudRate: String(login.baudRate ?? '')
  })
  jointMeta.businessDesc = String(parsed['businessDesc'] ?? '')
  jointMeta.note = String(parsed['note'] ?? '')
}
const writeMetaToForm = () => {
  form.value.remark = JSON.stringify({ deviceLogin: { ...deviceLogin }, businessDesc: jointMeta.businessDesc, note: jointMeta.note })
}
const loadDeviceInfo = async () => {
  const id = form.value.equipmentId
  deviceInfo.value = null
  if (!id) return
  try {
    deviceInfo.value = (await DeviceArchiveApi.getDeviceArchiveRecord(id as number)) as DeviceArchiveApi.DeviceArchiveVO
  } catch {
    deviceInfo.value = null
  }
}
watch(() => form.value.equipmentId, loadDeviceInfo)

const editableRecord = (row: Pick<JointTestVO, 'status'>) => (row.status === 0 || row.status === 1) && checkPermi(['pms:imp-joint-test:update'])
const readOnly = computed(() => !!props.readonly || (form.value.id ? !editableRecord(form.value) : !checkPermi(['pms:imp-joint-test:create'])))
const rules = {
  projectId: [{ required: true, message: '请选择项目' }, { validator: (_rule: unknown, value: number | string, callback: (error?: Error) => void) => callback(Number(value) > 0 ? undefined : new Error('请选择项目')) }],
  testCase: [{ required: true, message: '请输入联调用例' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await JointTestApi.getJointTestPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: JointTestVO) => {
  form.value = {
      id: undefined,
      projectId: props.projectId ?? 0,
      testCase: '',
      equipmentId: undefined,
      participants: '',
      testTime: undefined,
      testerUserId: undefined,
      result: '',
      exceptionRecord: '',
      remark: '',
      version: undefined,
      status: 0,
      ...row,
      // Keep empty legacy metadata when reopening a record without evidence.
      evidenceUrl: row?.evidenceUrl ?? ''
  }
  syncMetaFromForm()
  loadDeviceInfo()
  formVisible.value = true
}
const attachmentOwnerChanged = (saved: { version?: number; status?: number; configLogUrl?: string }) => {
  form.value.version = saved.version
  form.value.status = saved.status
}
const save = async () => {
  if (readOnly.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    writeMetaToForm()
    const data: JointTestVO = { ...form.value, testTime: form.value.testTime == null || form.value.testTime === '' ? undefined : Number(form.value.testTime) }
    data.id ? await JointTestApi.updateJointTest(data) : await JointTestApi.createJointTest(data)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: JointTestVO) => {
  if (row.status === 2 || row.status === 3) return
  await message.delConfirm()
  await JointTestApi.deleteJointTest(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: JointTestVO, action: 'start' | 'pass') => {
  const actionText = { start: '开始联调', pass: '联调通过' }[action]
  await message.confirm(`确认${actionText}联调记录【${row.testCase}】？`)
  if (action === 'start') await JointTestApi.startJointTest(row.id!)
  if (action === 'pass') await JointTestApi.passJointTest(row.id!)
  message.success(`${actionText}成功`)
  await load()
}
const failVisible = ref(false)
const failForm = reactive({ id: 0, exceptionRecord: '' })
const handleFail = (row: JointTestVO) => {
  failForm.id = row.id!
  failForm.exceptionRecord = ''
  failVisible.value = true
}
const confirmFail = async () => {
  if (!failForm.exceptionRecord.trim()) {
    message.warning('请输入异常记录')
    return
  }
  saving.value = true
  try {
    await JointTestApi.failJointTest(failForm.id, failForm.exceptionRecord.trim())
    message.success('已记录联调失败')
    failVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<style lang="scss" scoped>
.section-title {
  padding: 6px 0 8px;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.form-tip {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
