<template>
  <div>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="编号" prop="code">
        <el-input v-model="query.code" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="标题" prop="title">
        <el-input v-model="query.title" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="类型" prop="announcementType">
        <el-select v-model="query.announcementType" clearable class="!w-140px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_ANNOUNCEMENT_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="设备型号" prop="productModel">
        <el-input v-model="query.productModel" clearable class="!w-160px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="严重等级" prop="severity">
        <el-select v-model="query.severity" clearable class="!w-120px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_ANNOUNCEMENT_SEVERITY)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-120px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_ANNOUNCEMENT_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:eng-announcement:create']"
          ><Icon icon="ep:plus" />新建公告</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无技术公告">
      <el-table-column prop="code" label="编号" width="150" />
      <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="announcementType" label="类型" width="120">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ANNOUNCEMENT_TYPE" :value="row.announcementType" />
        </template>
      </el-table-column>
      <el-table-column prop="productModel" label="适用型号" width="140" show-overflow-tooltip />
      <el-table-column prop="severity" label="严重等级" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ANNOUNCEMENT_SEVERITY" :value="row.severity" />
        </template>
      </el-table-column>
      <el-table-column prop="publishDate" label="发布日期" width="120" />
      <el-table-column prop="effectiveDate" label="生效日期" width="120" />
      <el-table-column prop="expireDate" label="失效日期" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ANNOUNCEMENT_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:eng-announcement:query']"
            >明细</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0"
            @click="openEdit(row)"
            v-hasPermi="['pms:eng-announcement:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handlePublish(row)"
            v-hasPermi="['pms:eng-announcement:publish']"
            >发布</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 1"
            @click="handleDisable(row)"
            v-hasPermi="['pms:eng-announcement:disable']"
            >停用</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0"
            @click="remove(row)"
            v-hasPermi="['pms:eng-announcement:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑技术公告' : '新建技术公告'" width="900px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="编号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 TA-2026-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="公告类型" prop="announcementType">
            <el-select v-model="form.announcementType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_ANNOUNCEMENT_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="适用设备型号" prop="productModel"><el-input v-model="form.productModel" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="严重等级" prop="severity">
            <el-select v-model="form.severity" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_ANNOUNCEMENT_SEVERITY)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="发布日期" prop="publishDate">
            <el-date-picker v-model="form.publishDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="生效日期" prop="effectiveDate">
            <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="失效日期" prop="expireDate">
            <el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="影响版本" prop="affectedVersions">
            <el-input
              v-model="form.affectedVersions"
              type="textarea"
              :rows="2"
              placeholder='影响版本范围JSON数组，如 ["v1.0","v1.1"]'
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="公告内容" prop="content">
            <Editor v-model="form.content" height="220px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="处置建议" prop="handlingSuggestion">
            <el-input v-model="form.handlingSuggestion" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="附件" prop="fileUrl">
            <UploadFile v-model="form.fileUrl" />
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
  <Dialog v-model="detailVisible" title="技术公告明细" width="900px">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="编号">{{ current.code }}</el-descriptions-item>
      <el-descriptions-item label="标题">{{ current.title }}</el-descriptions-item>
      <el-descriptions-item label="公告类型">
        <dict-tag :type="DICT_TYPE.PMS_ANNOUNCEMENT_TYPE" :value="current.announcementType" />
      </el-descriptions-item>
      <el-descriptions-item label="严重等级">
        <dict-tag :type="DICT_TYPE.PMS_ANNOUNCEMENT_SEVERITY" :value="current.severity" />
      </el-descriptions-item>
      <el-descriptions-item label="适用型号">{{ current.productModel || '-' }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_ANNOUNCEMENT_STATUS" :value="current.status" />
      </el-descriptions-item>
      <el-descriptions-item label="发布日期">{{ current.publishDate || '-' }}</el-descriptions-item>
      <el-descriptions-item label="生效日期">{{ current.effectiveDate || '-' }}</el-descriptions-item>
      <el-descriptions-item label="失效日期">{{ current.expireDate || '-' }}</el-descriptions-item>
      <el-descriptions-item label="附件">
        <span v-if="current.fileName">{{ current.fileName }}</span>
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item label="影响版本" :span="2">{{ current.affectedVersions || '-' }}</el-descriptions-item>
      <el-descriptions-item label="处置建议" :span="2">{{ current.handlingSuggestion || '-' }}</el-descriptions-item>
      <el-descriptions-item label="公告内容" :span="2">
        <div v-html="current.content"></div>
      </el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ current.remark || '-' }}</el-descriptions-item>
    </el-descriptions>
  </Dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as AnnouncementApi from '@/api/pms/engineering/announcement'
import type { AnnouncementVO } from '@/api/pms/engineering/announcement'

defineOptions({ name: 'PmsEngAnnouncement' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<AnnouncementVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  code: '',
  title: '',
  announcementType: '',
  productModel: '',
  severity: '',
  status: undefined as number | undefined
})

const load = async () => {
  loading.value = true
  try {
    const data = await AnnouncementApi.getAnnouncementPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 新建/编辑
const formVisible = ref(false)
const formRef = ref()
const form = reactive<AnnouncementVO>({
  code: '',
  title: '',
  announcementType: 'TECH_NOTICE',
  productModel: '',
  affectedVersions: '',
  publishDate: '',
  effectiveDate: '',
  expireDate: '',
  severity: 'MEDIUM',
  content: '',
  handlingSuggestion: '',
  fileUrl: '',
  remark: ''
})
const rules = {
  code: [{ required: true, message: '请输入编号' }],
  title: [{ required: true, message: '请输入标题' }],
  announcementType: [{ required: true, message: '请选择公告类型' }],
  severity: [{ required: true, message: '请选择严重等级' }]
}

const openCreate = () => {
  Object.assign(form, {
    id: undefined,
    code: '',
    title: '',
    announcementType: 'TECH_NOTICE',
    productModel: '',
    affectedVersions: '',
    publishDate: '',
    effectiveDate: '',
    expireDate: '',
    severity: 'MEDIUM',
    content: '',
    handlingSuggestion: '',
    fileUrl: '',
    remark: '',
    version: 0
  })
  formVisible.value = true
}
const openEdit = async (row: AnnouncementVO) => {
  const detail = await AnnouncementApi.getAnnouncement(row.id!)
  Object.assign(form, detail)
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await AnnouncementApi.updateAnnouncement(form)
      message.success('更新成功')
    } else {
      await AnnouncementApi.createAnnouncement(form)
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
const current = ref<Partial<AnnouncementVO>>({})
const openDetail = async (row: AnnouncementVO) => {
  current.value = await AnnouncementApi.getAnnouncement(row.id!)
  detailVisible.value = true
}

// 状态操作
const handlePublish = async (row: AnnouncementVO) => {
  await message.confirm('确认发布此技术公告？发布后不可修改。')
  await AnnouncementApi.publishAnnouncement(row.id!)
  message.success('发布成功')
  await load()
}
const handleDisable = async (row: AnnouncementVO) => {
  await message.confirm('确认停用此技术公告？')
  await AnnouncementApi.disableAnnouncement(row.id!)
  message.success('停用成功')
  await load()
}
const remove = async (row: AnnouncementVO) => {
  await message.delConfirm()
  await AnnouncementApi.deleteAnnouncement(row.id!)
  message.success('删除成功')
  await load()
}

onMounted(load)
</script>
