<!--
  系统用户弹窗选择器（V2，支持单选/多选）

  对齐 MdVendorSelectDialog 架构模式 + userSelect 左侧部门树
  搜索字段 & 展示字段：用户名称、用户昵称、部门、手机号码

  Props:
    multiple — true 多选（checkbox），false 单选（radio）；默认 true
    deptId   — 部门 ID
  Events:
    selected(rows: UserVO[]) — 确认选择后触发，单选时数组长度为 1
  Expose:
    open(selectedIds?: SystemUserId[]) — 打开弹窗，可传入已选 ID 用于预选高亮
-->
<template>
  <Dialog :title="title" v-model="dialogVisible" width="80%" align-center append-to-body>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false"
      ><template #default
        ><el-button link type="primary" @click="getList">重新加载用户</el-button></template
      ></el-alert
    >
    <el-row class="h-[calc(100vh-196px)]" :gutter="15">
      <!-- 左侧部门树 -->
      <el-col class="h-full" :span="5" :xs="24">
        <ContentWrap class="h-full" :body-style="{ height: '100%', '--el-card-padding': '0px' }">
          <DeptTreeSelect ref="deptTreeRef" @node-click="handleDeptNodeClick" />
        </ContentWrap>
      </el-col>
      <!-- 右侧：搜索表单 + 用户表格 -->
      <el-col class="h-full overflow-auto" :span="19" :xs="24">
        <ContentWrap>
          <el-form
            :disabled="restoring"
            class="-mb-[15px]"
            :inline="true"
            :model="queryParams"
            label-width="72px"
          >
            <el-form-item label="用户名称">
              <el-input
                v-model="queryParams.username"
                placeholder="请输入用户名称"
                clearable
                @keyup.enter="handleQuery"
                class="!w-240px"
              />
            </el-form-item>
            <el-form-item label="用户昵称">
              <el-input
                v-model="queryParams.nickname"
                placeholder="请输入用户昵称"
                clearable
                @keyup.enter="handleQuery"
                class="!w-240px"
              />
            </el-form-item>
            <el-form-item label="手机号码">
              <el-input
                v-model="queryParams.mobile"
                placeholder="请输入手机号码"
                clearable
                @keyup.enter="handleQuery"
                class="!w-240px"
              />
            </el-form-item>
            <el-form-item label="状态">
              <el-select
                v-model="queryParams.status"
                placeholder="请选择状态"
                clearable
                class="!w-240px"
              >
                <el-option
                  v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="showCreateTime" label="创建时间">
              <el-date-picker
                v-model="queryParams.createTime"
                type="datetimerange"
                value-format="YYYY-MM-DD HH:mm:ss"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                class="!w-240px"
              />
            </el-form-item>
            <el-form-item>
              <el-button @click="handleQuery">
                <Icon icon="ep:search" class="mr-5px" /> 搜索
              </el-button>
              <el-button @click="resetQuery">
                <Icon icon="ep:refresh" class="mr-5px" /> 重置
              </el-button>
            </el-form-item>
          </el-form>
        </ContentWrap>
        <!-- 数据表格：单选 radio / 多选 checkbox 统一在一个 table 内 -->
        <ContentWrap
          class="h-[calc(100%-var(--content-wrap-padding,10px)*2-var(--content-wrap-margin,15px)*2-32px*2-3px*2-2px)] !mb-0"
          :body-style="{ height: '100%', padding: 'var(--content-wrap-padding,10px)' }"
        >
          <el-table
            ref="tableRef"
            class="!h-[calc(100%-32px-30px+var(--content-wrap-padding,10px))]"
            v-loading="loading || restoring"
            :data="list"
            :stripe="true"
            :show-overflow-tooltip="true"
            row-key="id"
            :highlight-current-row="!multiple"
            @selection-change="handleSelectionChange"
            @row-click="handleRowClick"
            @row-dblclick="handleRowDblClick"
          >
            <!-- 多选：checkbox（reserve-selection 保证跨页勾选不丢失） -->
            <el-table-column
              v-if="multiple"
              type="selection"
              :selectable="selectable"
              :reserve-selection="true"
              width="50"
              align="center"
            />
            <!-- 单选：radio -->
            <el-table-column v-else width="50" align="center">
              <template #default="{ row }">
                <el-radio
                  v-model="selectedRadioId"
                  :value="row.id"
                  class="radio-no-label"
                  :disabled="row.disabled || loading || restoring"
                  @change="handleRadioChange(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="用户编号" align="center" prop="id" width="150" />
            <el-table-column label="用户名称" align="center" prop="username" width="150" />
            <el-table-column label="用户昵称" align="left" prop="nickname" min-width="150" />
            <el-table-column label="部门" align="center" prop="deptName" width="150" />
            <el-table-column label="手机号码" align="center" prop="mobile" width="130" />
            <el-table-column label="状态" align="center" prop="status" width="80">
              <template #default="scope">
                <dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="scope.row.status" />
              </template>
            </el-table-column>
            <el-table-column
              label="创建时间"
              align="center"
              prop="createTime"
              :formatter="dateFormatter"
              width="180"
            />
          </el-table>
          <Pagination
            :total="total"
            v-model:page="queryParams.pageNo"
            v-model:limit="queryParams.pageSize"
            @pagination="getList"
          />
        </ContentWrap>
      </el-col>
    </el-row>
    <template #footer>
      <el-button type="primary" :disabled="loading || restoring" @click="confirmSelect"
        >确 定</el-button
      >
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { CommonStatusEnum } from '@/utils/constants'
import {
  systemUserSelection,
  sameSystemUserId,
  type SystemUserSelectionUser,
  type SystemUserId
} from '@/api/system/user/selection'
import DeptTreeSelect from '@/views/system/dept/components/DeptTreeSelect.vue'
import { dateFormatter } from '@/utils/formatTime'

defineOptions({ name: 'SystemUserDialog' })

type UserSelectRow = SystemUserSelectionUser & {
  disabled?: boolean
}

const props = withDefaults(
  defineProps<{
    title?: string
    multiple?: boolean // true 多选（checkbox），false 单选（radio）
    deptId?: number // 部门 ID
    defaultStatus?: number | 'all'
    showCreateTime?: boolean
    allowEmpty?: boolean
    retainDraftOnReopen?: boolean
  }>(),
  {
    title: '人员选择',
    multiple: true,
    defaultStatus: CommonStatusEnum.ENABLE,
    showCreateTime: false,
    allowEmpty: false,
    retainDraftOnReopen: false
  }
)

const message = useMessage()
const emit = defineEmits<{
  selected: [rows: SystemUserSelectionUser[], activityId?: any]
}>()

const dialogVisible = ref(false) // 弹窗是否展示
const loadError = ref('')
let loadSequence = 0
let openSequence = 0
let hasOpened = false
onBeforeUnmount(() => {
  loadSequence++
  openSequence++
})
const restoring = ref(false)
const loading = ref(false) // 列表加载中
const list = ref<UserSelectRow[]>([]) // 用户列表
const total = ref(0) // 总条数
const activityId = ref()

// ==================== 部门树 ====================
const deptTreeRef = ref() // 部门树 Ref

/** 部门节点点击 */
const handleDeptNodeClick = (deptId: number | undefined) => {
  queryParams.deptId = deptId
  handleQuery()
}

// ==================== 选中状态 ====================
const tableRef = ref() // 表格 Ref
const selectedRows = ref<UserSelectRow[]>([]) // 多选模式：选中行
const selectedRadioId = ref<SystemUserId>() // 单选模式：选中 ID
const currentRadioRow = ref<UserSelectRow>() // 单选模式：选中行对象
const preSelectedIds = ref<SystemUserId[]>([]) // 打开弹窗时传入的已选 ID
const preDisabledIds = ref<SystemUserId[]>([]) // 打开弹窗时传入的禁选 ID

/** 多选：是否可以选中 */
const selectable = (row: UserSelectRow) => {
  return (
    !loading.value &&
    !restoring.value &&
    !preDisabledIds.value.some((id) => sameSystemUserId(id, row.id))
  )
}

/** 多选：checkbox 变化 */
const handleSelectionChange = (rows: UserSelectRow[]) => {
  if (props.multiple && !loading.value && !restoring.value) {
    selectedRows.value = rows
    preSelectedIds.value = rows.map((row) => row.id)
  }
}

/** 单选：radio 变化 */
const handleRadioChange = (row: UserSelectRow) => {
  if (loading.value || restoring.value || row.disabled) return
  preSelectedIds.value = [row.id]
  currentRadioRow.value = row
}

/** 单击行：单选模式下点击整行即选中（降低操作成本），多选不处理（避免和 dblclick 冲突） */
const handleRowClick = (row: UserSelectRow) => {
  if (row.disabled || loading.value || restoring.value) {
    return
  }
  if (props.multiple) {
    return
  }
  preSelectedIds.value = [row.id]
  selectedRadioId.value = row.id
  currentRadioRow.value = row
}

/** 双击行：多选模式切换勾选，单选模式直接确认 */
const handleRowDblClick = (row: UserSelectRow) => {
  if (row.disabled || loading.value || restoring.value) {
    return
  }
  if (props.multiple) {
    tableRef.value?.toggleRowSelection(row)
    return
  }
  preSelectedIds.value = [row.id]
  selectedRadioId.value = row.id
  currentRadioRow.value = row
  confirmSelect()
}

// ==================== 用户查询 ====================
const queryParams = reactive({
  pageNo: 1, // 页码
  pageSize: 10, // 每页条数
  username: undefined as string | undefined, // 用户名称
  nickname: undefined as string | undefined, // 用户昵称
  mobile: undefined as string | undefined, // 手机号码
  status: (props.defaultStatus === 'all' ? undefined : props.defaultStatus) as number | undefined,
  deptId: undefined as number | undefined, // 部门 ID（从左侧树选择）
  createTime: props.showCreateTime ? ([] as string[]) : undefined
})

/** 查询用户列表 */
const getList = async () => {
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  list.value = []
  try {
    const data = await systemUserSelection.page(queryParams)
    if (sequence !== loadSequence) return
    list.value = data.list
    total.value = data.total
    list.value.forEach((row) => {
      row.disabled = preDisabledIds.value.some((id) => sameSystemUserId(id, row.id))
    })
    await nextTick()
    applyPreSelection()
  } catch {
    if (sequence === loadSequence) loadError.value = '用户候选加载失败，请重试。'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

/** 恢复预选状态（当前页可见范围内） */
const applyPreSelection = () => {
  if (preSelectedIds.value.length === 0) {
    return
  }
  if (props.multiple) {
    const table = tableRef.value
    if (!table) {
      return
    }
    list.value.forEach((row) => {
      if (preSelectedIds.value.some((id) => sameSystemUserId(id, row.id))) {
        table.toggleRowSelection(row, true)
      }
    })
  } else {
    const match = list.value.find((row) =>
      preSelectedIds.value.some((id) => sameSystemUserId(id, row.id))
    )
    if (match) {
      selectedRadioId.value = match.id
      currentRadioRow.value = match
    }
  }
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

/** 重置查询条件 */
const resetQuery = () => {
  queryParams.username = undefined
  queryParams.nickname = undefined
  queryParams.mobile = undefined
  queryParams.status = props.defaultStatus === 'all' ? undefined : props.defaultStatus
  queryParams.createTime = props.showCreateTime ? [] : undefined
  queryParams.deptId = undefined
  // 清空部门树选中
  deptTreeRef.value?.reset()
  handleQuery()
}

/** 确认选择 */
const confirmSelect = () => {
  if (loading.value || restoring.value) return
  if (props.multiple) {
    if (selectedRows.value.length === 0 && !props.allowEmpty) {
      message.warning('请至少选择一条数据')
      return
    }
    emit('selected', selectedRows.value, activityId.value)
  } else {
    if (!currentRadioRow.value) {
      message.warning('请选择一条数据')
      return
    }
    emit('selected', [currentRadioRow.value], activityId.value)
  }
  dialogVisible.value = false
}

// ==================== 打开弹窗 ====================

/** 打开弹窗，可传入已选 ID 用于预选高亮 */
const open = async (
  selectedIds?: SystemUserId[],
  disabledIds?: SystemUserId[],
  _activityId?: any
) => {
  const opening = ++openSequence
  if (
    props.retainDraftOnReopen &&
    hasOpened &&
    selectedIds === undefined &&
    disabledIds === undefined &&
    _activityId === undefined
  ) {
    const retainedRows = [...selectedRows.value]
    dialogVisible.value = true
    restoring.value = true
    await getList()
    if (opening !== openSequence) return
    // Dialog destroys its table when closed; rebuild reserve-selection for every draft row,
    // including people outside the current page, before accepting new selection events.
    if (props.multiple) {
      selectedRows.value = retainedRows
      retainedRows.forEach((row) => tableRef.value?.toggleRowSelection(row, true))
    }
    restoring.value = false
    return
  }
  hasOpened = true
  restoring.value = true
  preDisabledIds.value = disabledIds ?? []
  activityId.value = _activityId
  dialogVisible.value = true
  // 重置查询条件 + 页码，避免二次打开继承上次过滤上下文
  queryParams.username = undefined
  queryParams.nickname = undefined
  queryParams.mobile = undefined
  queryParams.status = props.defaultStatus === 'all' ? undefined : props.defaultStatus
  queryParams.createTime = props.showCreateTime ? [] : undefined
  queryParams.deptId = props.deptId
  queryParams.pageNo = 1
  // 清空上一次的选中状态
  selectedRows.value = []
  selectedRadioId.value = undefined
  currentRadioRow.value = undefined
  preSelectedIds.value = (selectedIds ?? []).filter(
    (id) => !preDisabledIds.value.some((disabled) => sameSystemUserId(disabled, id))
  )
  // 清空部门树选中 + 多选模式清空跨页缓存的勾选
  await nextTick()
  deptTreeRef.value?.reset()
  tableRef.value?.clearSelection()
  await getList()
  if (opening !== openSequence) return
  if (preSelectedIds.value.length) {
    try {
      const selected = await systemUserSelection.resolve(preSelectedIds.value)
      if (opening !== openSequence) return
      const eligible = selected.filter(
        (row) => !preDisabledIds.value.some((id) => sameSystemUserId(id, row.id))
      )
      if (props.multiple) {
        selectedRows.value = eligible
        eligible.forEach((row) => tableRef.value?.toggleRowSelection(row, true))
      } else {
        currentRadioRow.value = eligible[0]
        selectedRadioId.value = eligible[0]?.id
      }
    } catch {
      loadError.value = '已选用户回显失败，原编号保留，请重新打开重试。'
    }
  }
  if (queryParams.deptId) {
    deptTreeRef.value?.setCurrent(queryParams.deptId)
  }
  if (opening === openSequence) restoring.value = false
}
defineExpose({ open })
</script>

<style lang="scss" scoped>
/* 隐藏 radio 的 label 文字，只保留圆圈 */
.radio-no-label {
  :deep(.el-radio__label) {
    display: none;
  }
}
</style>
