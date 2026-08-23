<template>
  <div>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="项目" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择项目"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="编号" prop="code">
        <el-input v-model="query.code" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="公告" prop="announcementId">
        <PmsEntitySelect
          v-model="query.announcementId"
          :api="AnnouncementApi.getAnnouncementPage"
          label-field="title"
          value-field="id"
          query-field="title"
          placeholder="请选择公告"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="设备型号" prop="deviceModel">
        <el-input v-model="query.deviceModel" clearable class="!w-160px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="匹配结果" prop="matchResult">
        <el-select v-model="query.matchResult" clearable class="!w-120px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_ANN_CHECK_MATCH)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="EOS/EOM" prop="eomStatus">
        <el-select v-model="query.eomStatus" clearable class="!w-120px">
          <el-option v-for="item in eomOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-120px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_ANN_CHECK_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:eng-announcement-check:create']"
          ><Icon icon="ep:plus" />新建检查</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无预检查记录">
      <el-table-column prop="code" label="编号" width="150" />
      <el-table-column prop="projectId" label="项目" min-width="160">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="announcementId" label="公告" min-width="180">
        <template #default="{ row }">
          <AnnouncementTag :announcement-id="row.announcementId" />
        </template>
      </el-table-column>
      <el-table-column prop="deviceModel" label="设备型号" width="130" show-overflow-tooltip />
      <el-table-column prop="deviceSerial" label="序列号" width="130" show-overflow-tooltip />
      <el-table-column prop="deviceVersion" label="设备版本" width="100" />
      <el-table-column prop="matchResult" label="匹配结果" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ANN_CHECK_MATCH" :value="row.matchResult" />
        </template>
      </el-table-column>
      <el-table-column prop="eomStatus" label="EOS/EOM" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.eomStatus && row.eomStatus !== 'NONE'" type="danger">{{ row.eomStatus }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ANN_CHECK_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:eng-announcement-check:query']"
            >明细</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0"
            @click="openEdit(row)"
            v-hasPermi="['pms:eng-announcement-check:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 0"
            @click="handlePerformCheck(row)"
            v-hasPermi="['pms:eng-announcement-check:update']"
            >执行检查</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 1"
            @click="openHandle(row, 'HANDLE')"
            v-hasPermi="['pms:eng-announcement-check:handle']"
            >处置</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 1"
            @click="openHandle(row, 'IGNORE')"
            v-hasPermi="['pms:eng-announcement-check:handle']"
            >忽略</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0"
            @click="remove(row)"
            v-hasPermi="['pms:eng-announcement-check:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑预检查记录' : '新建预检查记录'" width="800px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="编号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 PCH-2026-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="项目" prop="projectId">
            <PmsEntitySelect
              v-model="form.projectId"
              :api="ProjectApi.getProjectPage"
              label-field="name"
              value-field="id"
              query-field="name"
              placeholder="请选择项目"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="关联公告" prop="announcementId">
            <PmsEntitySelect
              v-model="form.announcementId"
              :api="AnnouncementApi.getAnnouncementPage"
              label-field="title"
              value-field="id"
              query-field="title"
              placeholder="请选择技术公告"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备型号" prop="deviceModel"><el-input v-model="form.deviceModel" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备序列号" prop="deviceSerial"><el-input v-model="form.deviceSerial" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备版本" prop="deviceVersion"><el-input v-model="form.deviceVersion" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="检查人" prop="checkerUserId">
            <PmsEntitySelect
              v-model="form.checkerUserId"
              :api="UserApi.getUserPage"
              label-field="nickname"
              value-field="id"
              query-field="nickname"
              placeholder="请选择检查人"
            />
          </el-form-item>
        </el-col>
        <el-col v-if="form.id" :span="12">
          <el-form-item label="版本号" prop="version">
            <el-input-number v-model="form.version" :min="0" class="!w-full" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="处置建议" prop="handlingSuggestion">
            <el-input v-model="form.handlingSuggestion" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <!-- 明细对话框 -->
  <Dialog v-model="detailVisible" title="预检查记录明细" width="800px">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="编号">{{ current.code }}</el-descriptions-item>
      <el-descriptions-item label="项目">
        <ProjectTag v-if="current.projectId" :project-id="current.projectId" />
      </el-descriptions-item>
      <el-descriptions-item label="关联公告">
        <AnnouncementTag v-if="current.announcementId" :announcement-id="current.announcementId" />
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_ANN_CHECK_STATUS" :value="current.status" />
      </el-descriptions-item>
      <el-descriptions-item label="设备型号">{{ current.deviceModel || '-' }}</el-descriptions-item>
      <el-descriptions-item label="设备序列号">{{ current.deviceSerial || '-' }}</el-descriptions-item>
      <el-descriptions-item label="设备版本">{{ current.deviceVersion || '-' }}</el-descriptions-item>
      <el-descriptions-item label="匹配结果">
        <dict-tag :type="DICT_TYPE.PMS_ANN_CHECK_MATCH" :value="current.matchResult" />
      </el-descriptions-item>
      <el-descriptions-item label="EOS/EOM">{{ current.eomStatus || '-' }}</el-descriptions-item>
      <el-descriptions-item label="检查人">
        <UserTag v-if="current.checkerUserId" :user-id="current.checkerUserId" />
      </el-descriptions-item>
      <el-descriptions-item label="检查时间">{{ current.checkTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="处理时间">{{ current.handleTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="处置建议" :span="2">{{ current.handlingSuggestion || '-' }}</el-descriptions-item>
      <el-descriptions-item label="处理意见" :span="2">{{ current.handleOpinion || '-' }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ current.remark || '-' }}</el-descriptions-item>
    </el-descriptions>
  </Dialog>

  <!-- 处置对话框 -->
  <Dialog v-model="handleVisible" :title="handleTitle" width="560px">
    <el-form :model="handleForm" label-width="100px">
      <el-form-item label="检查编号">{{ handleForm.code }}</el-form-item>
      <el-form-item label="处理意见" prop="handleOpinion">
        <el-input v-model="handleForm.handleOpinion" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="confirmHandle">确认</el-button>
    </template>
  </Dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as AnnouncementCheckApi from '@/api/pms/engineering/announcement-check'
import * as AnnouncementApi from '@/api/pms/engineering/announcement'
import * as ProjectApi from '@/api/pms/project/project'
import * as UserApi from '@/api/system/user'
import type { AnnouncementCheckVO } from '@/api/pms/engineering/announcement-check'
import ProjectTag from '@/components/ProjectTag/index.vue'
import UserTag from '@/components/UserTag/index.vue'
import AnnouncementTag from '@/components/AnnouncementTag/index.vue'

defineOptions({ name: 'PmsEngAnnouncementCheck' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<AnnouncementCheckVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  announcementId: undefined as number | undefined,
  code: '',
  deviceModel: '',
  deviceSerial: '',
  matchResult: '',
  eomStatus: '',
  status: undefined as number | undefined
})

const eomOptions = [
  { value: 'EOS', label: 'EOS' },
  { value: 'EOM', label: 'EOM' },
  { value: 'NONE', label: 'NONE' }
]

const load = async () => {
  loading.value = true
  try {
    const data = await AnnouncementCheckApi.getAnnouncementCheckPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 新建/编辑
const formVisible = ref(false)
const formRef = ref()
const form = reactive<AnnouncementCheckVO>({
  code: '',
  projectId: undefined,
  announcementId: undefined,
  deviceModel: '',
  deviceSerial: '',
  deviceVersion: '',
  handlingSuggestion: '',
  checkerUserId: undefined,
  remark: ''
})
const rules = {
  code: [{ required: true, message: '请输入编号' }],
  projectId: [{ required: true, message: '请选择项目' }],
  announcementId: [{ required: true, message: '请选择关联公告' }]
}

const openCreate = () => {
  Object.assign(form, {
    id: undefined,
    code: '',
    projectId: undefined,
    announcementId: undefined,
    deviceModel: '',
    deviceSerial: '',
    deviceVersion: '',
    handlingSuggestion: '',
    checkerUserId: undefined,
    remark: '',
    version: 0
  })
  formVisible.value = true
}
const openEdit = async (row: AnnouncementCheckVO) => {
  const detail = await AnnouncementCheckApi.getAnnouncementCheck(row.id!)
  Object.assign(form, detail)
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await AnnouncementCheckApi.updateAnnouncementCheck(form)
      message.success('更新成功')
    } else {
      await AnnouncementCheckApi.createAnnouncementCheck(form)
      message.success('创建成功')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 明细
const detailVisible = ref(false)
const current = ref<AnnouncementCheckVO>({})
const openDetail = async (row: AnnouncementCheckVO) => {
  current.value = await AnnouncementCheckApi.getAnnouncementCheck(row.id!)
  detailVisible.value = true
}

// 执行检查
const handlePerformCheck = async (row: AnnouncementCheckVO) => {
  await message.confirm('确认执行匹配检查？系统将根据公告与设备信息自动输出匹配结果。')
  await AnnouncementCheckApi.performCheck(row.id!)
  message.success('检查完成')
  await load()
}

// 处置对话框
const handleVisible = ref(false)
const handleTitle = ref('')
const handleForm = reactive({
  id: undefined as number | undefined,
  code: '',
  handleAction: 'HANDLE',
  handleOpinion: '',
  version: 0
})
const openHandle = (row: AnnouncementCheckVO, action: string) => {
  handleTitle.value = action === 'HANDLE' ? '处置检查记录' : '忽略检查记录'
  Object.assign(handleForm, {
    id: row.id,
    code: row.code,
    handleAction: action,
    handleOpinion: '',
    version: row.version
  })
  handleVisible.value = true
}
const confirmHandle = async () => {
  saving.value = true
  try {
    await AnnouncementCheckApi.handleCheck(handleForm as any)
    message.success('处理成功')
    handleVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 删除
const remove = async (row: AnnouncementCheckVO) => {
  await message.delConfirm()
  await AnnouncementCheckApi.deleteAnnouncementCheck(row.id!)
  message.success('删除成功')
  await load()
}

onMounted(load)
</script>
