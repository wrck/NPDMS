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
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:imp-joint-test:create']"
          ><Icon icon="ep:plus" />新增联调</el-button
        >
        <el-button disabled title="外部采集仅保留扩展入口，当前不连接设备">一键收集未接入</el-button>
      </el-form-item>
    </el-form>
    <el-alert title="本页面保存本地联调用例、结果与手工附件；远程配置收集及自动对比未接入，不作为项目联调里程碑完成依据。" type="info" :closable="false" />
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="testCase" label="联调用例" min-width="200" show-overflow-tooltip><template #default="{ row }"><div v-dompurify-html="row.testCase" class="max-h-60px overflow-hidden"></div></template></el-table-column>
      <el-table-column prop="equipmentId" label="设备编号" width="100">
        <template #default="{ row }">
          <EquipmentTag :equipment-id="row.equipmentId" />
        </template>
      </el-table-column>
      <el-table-column prop="testTime" label="联调时间" width="160" :formatter="dateFormatter" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_JOINT_TEST_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:imp-joint-test:query']"
            >{{ editableRecord(row) ? '编辑' : '查看' }}</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'start')"
            v-hasPermi="['pms:imp-joint-test:update']"
            >开始联调</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 1"
            @click="handleAction(row, 'pass')"
            v-hasPermi="['pms:imp-joint-test:update']"
            >联调通过</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 1"
            @click="handleFail(row)"
            v-hasPermi="['pms:imp-joint-test:update']"
            >联调失败</el-button
          >
          <el-button v-if="row.status === 0 || row.status === 1" link type="danger" @click="remove(row)" v-hasPermi="['pms:imp-joint-test:delete']"
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
            <PmsEntitySelect
              v-model="form.equipmentId"
              :api="DeviceArchiveApi.getDeviceArchivePage"
              :label-field="['sn', 'name']"
              value-field="id"
              query-field="sn"
              placeholder="请选择设备"
            />
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
          <el-form-item label="登录密码">
            <el-input
              v-model="loginSecret"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="仅本次采集使用，不保存"
            />
          </el-form-item>
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
            <el-button disabled title="在线采集未接入：请通过 5.3 配置调试上传 Log 或以附件提供采集结果">一键收集配置信息未接入</el-button>
            <el-button disabled title="加密凭证保存需绑定平台采集命令模板（SSH/Telnet），待采集域接入后启用；当前密码不落库、不传输">保存加密凭证未接入</el-button>
            <span class="form-tip">设备登录信息（除密码）随联调记录保存；密码不落库，平台加密凭证机制接入后可在采集域统一管理。</span>
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
          <el-form-item label="证据附件" prop="evidenceUrl"><UploadFile v-model="form.evidenceUrl!" :disabled="readOnly" :file-type="['log', 'txt', 'cfg', 'conf', 'doc', 'xls', 'ppt', 'pdf']" /></el-form-item>
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
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as JointTestApi from '@/api/pms/engineering/joint-test'
import type { JointTestVO } from '@/api/pms/engineering/joint-test'
import * as ProjectApi from '@/api/pms/project/projects'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import EquipmentTag from '@/components/EquipmentTag/index.vue'
import { checkPermi } from '@/utils/permission'
import { dateFormatter } from '@/utils/formatTime'

defineOptions({ name: 'PmsEngJointTest' })
const props = defineProps<{ projectId?: number }>()
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
const loginSecret = ref('')
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
const readOnly = computed(() => form.value.id ? !editableRecord(form.value) : !checkPermi(['pms:imp-joint-test:create']))
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
      // Keep UploadFile on the existing string API contract for NULL legacy evidence.
      evidenceUrl: row?.evidenceUrl ?? ''
  }
  loginSecret.value = ''
  syncMetaFromForm()
  loadDeviceInfo()
  formVisible.value = true
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
