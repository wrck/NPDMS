<template>
  <el-drawer v-model="visible" :title="`${site?.name || ''} · 位置树`" size="620px">
    <div class="location-toolbar">
      <div>
        <div class="text-15px font-600">站点内部位置</div>
        <div class="text-12px text-[var(--el-text-color-secondary)]"
          >园区→楼栋→楼层→机房→机柜→U位，层级不限。</div
        >
      </div>
      <el-button
        type="primary"
        plain
        v-hasPermi="['pms:asset-location:update']"
        @click="openNodeForm()"
        ><Icon icon="ep:plus" class="mr-5px" />新增根位置</el-button
      >
    </div>
    <el-empty
      v-if="!loading && tree.length === 0"
      description="尚未维护位置，从根位置开始建立空间树。"
    />
    <el-tree
      v-else
      v-loading="loading"
      :data="tree"
      node-key="id"
      default-expand-all
      :expand-on-click-node="false"
    >
      <template #default="{ data }">
        <div class="location-node">
          <span class="location-depth">L{{ data.treeDepth }}</span>
          <span class="font-500">{{ data.name }}</span>
          <el-tag size="small" type="info">{{ data.locationType }}</el-tag>
          <span class="text-12px text-[var(--el-text-color-secondary)]">{{ data.code }}</span>
          <span class="location-actions">
            <el-button link type="primary" @click.stop="openNodeForm(data)">修订</el-button>
            <el-button link type="primary" @click.stop="openNodeForm(undefined, data.id)"
              >新增下级</el-button
            >
            <el-button v-if="data.status === 0" link type="danger" @click.stop="disableNode(data)"
              >停用</el-button
            >
          </span>
        </div>
      </template>
    </el-tree>

    <Dialog
      v-model="nodeDialogVisible"
      :title="nodeForm.id ? '修订位置' : '新增位置'"
      width="520px"
    >
      <el-form ref="nodeFormRef" :model="nodeForm" :rules="nodeRules" label-width="88px">
        <el-form-item label="上级位置" prop="parentId">
          <el-tree-select
            v-model="nodeForm.parentId"
            :data="tree"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            check-strictly
            clearable
            class="!w-100%"
            placeholder="空表示根位置"
          />
        </el-form-item>
        <el-form-item label="位置编码" prop="code"
          ><el-input v-model="nodeForm.code"
        /></el-form-item>
        <el-form-item label="位置名称" prop="name"
          ><el-input v-model="nodeForm.name"
        /></el-form-item>
        <el-form-item label="位置类型" prop="locationType"
          ><el-input v-model="nodeForm.locationType" placeholder="例如：ROOM / RACK / U_POSITION"
        /></el-form-item>
        <el-form-item label="同级排序" prop="treeSort"
          ><el-input-number v-model="nodeForm.treeSort" :min="0"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button type="primary" @click="saveNode">保存</el-button
        ><el-button @click="nodeDialogVisible = false">取消</el-button></template
      >
    </Dialog>
  </el-drawer>
</template>

<script lang="ts" setup>
import { handleTree } from '@/utils/tree'
import * as LocationApi from '@/api/pms/asset/location'

const emit = defineEmits(['changed'])
const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const site = ref<LocationApi.SiteVO>()
const tree = ref<LocationApi.SiteLocationVO[]>([])
const nodeDialogVisible = ref(false)
const nodeFormRef = ref()
const emptyNode = (): LocationApi.SiteLocationVO => ({
  code: '',
  name: '',
  locationType: '',
  treeSort: 0
})
const nodeForm = ref<LocationApi.SiteLocationVO>(emptyNode())
const nodeRules = {
  code: [{ required: true, message: '位置编码不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '位置名称不能为空', trigger: 'blur' }],
  locationType: [{ required: true, message: '位置类型不能为空', trigger: 'blur' }]
}

const loadTree = async () => {
  if (!site.value?.id) return
  loading.value = true
  try {
    tree.value = handleTree(await LocationApi.getSiteLocationTree(site.value.id), 'id', 'parentId')
  } finally {
    loading.value = false
  }
}
const open = async (value: LocationApi.SiteVO) => {
  site.value = value
  visible.value = true
  await loadTree()
}
defineExpose({ open })
const openNodeForm = (node?: LocationApi.SiteLocationVO, parentId?: number) => {
  nodeForm.value = node ? { ...node, expectedVersion: node.version } : { ...emptyNode(), parentId }
  nodeDialogVisible.value = true
  nextTick(() => nodeFormRef.value?.clearValidate())
}
const saveNode = async () => {
  if (!(await nodeFormRef.value?.validate()) || !site.value?.id) return
  await LocationApi.maintainLocation({
    site: { id: site.value.id, expectedVersion: site.value.version },
    siteLocation: {
      id: nodeForm.value.id,
      expectedVersion: nodeForm.value.version,
      parentId: nodeForm.value.parentId,
      code: nodeForm.value.code,
      name: nodeForm.value.name,
      locationType: nodeForm.value.locationType,
      treeSort: nodeForm.value.treeSort
    }
  })
  message.success('位置修订已保存')
  nodeDialogVisible.value = false
  await loadTree()
  emit('changed')
}
const disableNode = async (node: LocationApi.SiteLocationVO) => {
  await message.confirm('停用后不能再用于新的安装位置，是否继续？')
  await LocationApi.disableSiteLocation({ id: node.id!, version: node.version! })
  message.success('位置已停用')
  await loadTree()
}
</script>

<style scoped>
.location-toolbar {
  display: flex;
  padding-bottom: 16px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--el-border-color-light);
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.location-node {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 36px;
}

.location-depth {
  width: 28px;
  font-family: var(--el-font-family);
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.location-actions {
  display: inline-flex;
  padding-right: 12px;
  margin-left: auto;
  gap: 4px;
}

@media (width <= 768px) {
  .location-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .location-actions {
    display: none;
  }
}
</style>
