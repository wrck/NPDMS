<template>
  <section class="execution-panel" aria-labelledby="inspection-execution-title">
    <div class="section-heading">
      <h2 id="inspection-execution-title">{{ isOffline ? '离线巡检执行' : '巡检执行结果' }}</h2>
      <el-tag v-if="currentAttempt"
        >第 {{ currentAttempt.attemptNo || '—' }} 次尝试 ·
        {{ attemptStatus(currentAttempt.statusCode) }}</el-tag
      >
    </div>
    <el-alert
      v-if="!currentAttempt"
      title="尚无执行尝试，请先完成巡检准备。"
      type="info"
      :closable="false"
      show-icon
    />
    <template v-if="isOffline">
      <el-alert
        :title="
          umcMode
            ? '上传后自动执行UMC巡检并登记正式结果。全部设备完成后，可进入报告确认；未完成的设备可重试或重新上传。'
            : '上传日志后获取UMC报告，全部规则和指标在下方巡检判定结果展示；DOC在报告环节下载。'
        "
        type="info"
        :closable="false"
        show-icon
      />
      <el-form label-position="top" class="upload-form" @submit.prevent="submitUpload">
        <el-form-item v-if="!umcMode" label="日志上传方式">
          <el-radio-group v-model="uploadMode" :disabled="disabled">
            <el-radio-button value="single">单设备日志</el-radio-button>
            <el-radio-button value="batch">多设备日志（按SN识别）</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="!umcMode && uploadMode === 'single'"
          label="日志来源设备IP"
          :required="!umcMode || (!!selectedFile && !inferredSourceHost)"
        >
          <el-input
            v-model="sourceHost"
            :disabled="disabled"
            :placeholder="
              inferredSourceHost
                ? `已从文件名识别 ${inferredSourceHost}，无需填写`
                : '填写日志来源设备的IPv4地址'
            "
          />
          <p v-if="umcMode" class="help-text">
            {{
              inferredSourceHost
                ? `文件名来源IP：${inferredSourceHost}；如另行填写，以填写值为准。`
                : '单设备文件名为IPv4或“设备名称(IPv4)”时可自动识别；其他文件请填写来源设备IP。'
            }}
          </p>
        </el-form-item>
        <template v-else>
          <el-form-item
            v-for="device in task.devices"
            :key="String(device.deviceId)"
            :label="`${device.serialNumber} 日志来源IP`"
          >
            <el-input
              v-model="sourceHosts[String(device.deviceId)]"
              :disabled="disabled"
              :placeholder="umcMode ? '文件名无法识别IP或同文件含多设备时填写' : '日志中包含该设备时填写'"
            />
          </el-form-item>
        </template>
        <p v-if="umcMode" class="help-text">
          多设备日志按SN自动识别，可一次选择多份日志或包含多台设备的日志。所有文件合计不超过20MB。
          优先从每份文件名识别IP（支持IPv4.txt、IPv4.log、设备名称(IP)），上传后按日志SN绑定设备。
          文件名IP优先于手填值；文件名无法识别IP或同文件含多设备时，请填写对应设备来源IP。
          同一设备的不同文件名IP冲突时整批拒绝；ZIP按包内每份日志文件名识别。
        </p>
        <p v-else-if="uploadMode === 'batch'" class="help-text">
          自动按SN拆分并匹配本次任务设备。任一设备不匹配时整批拒绝；请分别刷新各设备的解析结果。
        </p>
        <el-form-item v-if="!umcMode" :label="uploadMode === 'batch' ? '查看设备命令' : '巡检设备'">
          <el-select
            :model-value="String(selectedDeviceId || '')"
            :disabled="disabled"
            placeholder="选择要查看或上传日志的设备"
            @change="changeDevice"
          >
            <el-option
              v-for="device in task.devices"
              :key="String(device.deviceId)"
              :value="String(device.deviceId)"
              :label="deviceLabel(device.deviceId)"
            />
          </el-select>
        </el-form-item>
        <div class="upload-fields">
          <el-form-item label="巡检日志">
            <div
              class="file-drop-zone"
              :class="{ 'is-disabled': !canSelectFile }"
              @dragover.prevent
              @drop.prevent="dropFiles"
            >
              <button
                type="button"
                class="file-picker"
                :disabled="!canSelectFile"
                @click="fileInput?.click()"
              >
                <Icon icon="ep:document" :size="30" />
                <span>{{
                  umcMode ? '点击或拖拽选择多份巡检日志' : '点击或拖拽选择巡检日志文件'
                }}</span>
                <small
                  >{{
                    umcMode
                      ? '支持IPv4或设备名称(IPv4)原名、.log / .txt / .zip，单文件不超过'
                      : '支持 .log / .txt / .zip，单文件不超过'
                  }}
                  {{ umcMode || uploadMode === 'batch' ? 20 : 50 }}MB</small
                >
              </button>
              <input
                ref="fileInput"
                class="file-input"
                type="file"
                :accept="umcMode ? undefined : '.log,.txt,.zip'"
                :multiple="umcMode"
                aria-label="选择离线巡检日志文件"
                tabindex="-1"
                :disabled="!canSelectFile"
                @change="selectFile"
              />
            </div>
            <template v-if="umcMode">
              <div
                v-for="(file, index) in selectedFiles"
                :key="index"
                class="file-summary"
                role="status"
              >
                <span>{{ file.name }} · {{ fileSize(file.size) }}<template v-if="originalFilenameHost(file.name)"> · 文件名来源IP：{{ originalFilenameHost(file.name) }}（上传后按SN绑定）</template></span>
                <el-button
                  link
                  :disabled="disabled"
                  :aria-label="`移除日志 ${file.name}`"
                  @click="removeFile(index)"
                  >移除文件</el-button
                >
              </div>
              <p v-if="selectedFiles.length" class="help-text"
                >已选 {{ selectedFiles.length }} 份日志 · 合计
                {{ fileSize(selectedFiles.reduce((total, file) => total + file.size, 0))
                }}<span v-if="inferredSourceHost">
                  · 文件名来源IP：{{ inferredSourceHost }}，上传后按SN核对</span
                ></p
              >
            </template>
            <div v-else-if="selectedFile" class="file-summary" role="status">
              <span>{{ selectedFile.name }} · {{ fileSize(selectedFile.size) }}</span>
              <el-button link :disabled="disabled" @click="clearDraft">移除文件</el-button>
            </div>
            <p v-if="fileError" class="file-error" role="alert">{{ fileError }}</p>
            <p v-if="fileNotice" class="help-text" role="status">{{ fileNotice }}</p>
          </el-form-item>
          <el-form-item v-if="uploadMode === 'single' && !umcMode" label="命令提示（可选）">
            <el-select
              v-model="commandHint"
              clearable
              filterable
              :disabled="disabled || !selectedDeviceId"
              placeholder="仅从当前设备冻结命令中选择"
            >
              <el-option
                v-for="command in hintCommands"
                :key="command.content"
                :value="command.content"
                :label="`${command.inspectionItem} · ${command.content}`"
              />
            </el-select>
            <p class="help-text">只包含单条命令输出的日志可选择对应提示；多命令日志可留空。</p>
          </el-form-item>
        </div>
        <Teleport :to="actionsTarget || 'body'" :disabled="!actionsTarget">
          <el-button
            type="primary"
            :native-type="actionsTarget ? 'button' : 'submit'"
            :disabled="!canSelectFile || (umcMode ? !selectedFiles.length : !selectedFile)"
            @click="actionsTarget && submitUpload()"
            >{{ umcMode ? '上传并执行巡检' : '上传日志并生成报告' }}</el-button
          >
        </Teleport>
        <div v-if="!umcMode" class="section-heading">
          <h3 class="inspection-section-title">巡检命令清单</h3>
          <div class="section-actions">
            <el-button :disabled="!commands.length" @click="copyCommands">复制命令</el-button>
            <el-button :disabled="!commands.length" @click="downloadCommands"
              >下载 TXT 清单</el-button
            >
          </div>
        </div>
        <el-collapse v-if="!umcMode" class="command-list"
          ><el-collapse-item title="查看当前设备巡检命令" name="commands">
            <el-table
              :data="orderedCommands"
              :row-key="commandKey"
              empty-text="当前设备暂无冻结命令，请确认设备选择和巡检准备信息。"
            >
              <el-table-column prop="sequenceNo" label="顺序" width="70" />
              <el-table-column prop="detectionId" label="检测 ID" min-width="140" />
              <el-table-column
                prop="inspectionItem"
                label="检测项目"
                min-width="150"
                show-overflow-tooltip
              />
              <el-table-column prop="revisionNo" label="修订" width="70" />
              <el-table-column label="命令内容" min-width="280"
                ><template #default="{ row }">
                  <pre class="command-content">{{ row.content }}</pre>
                </template></el-table-column
              >
              <el-table-column prop="timeoutSeconds" label="规则超时配置（秒）" width="150" />
              <el-table-column label="规则继续配置" width="120"
                ><template #default="{ row }">{{
                  row.continueOnTimeout ? '是' : '否'
                }}</template></el-table-column
              >
            </el-table>
          </el-collapse-item></el-collapse
        >
      </el-form>
    </template>

    <template v-if="umcMode">
      <div v-if="hasExecutionHistory" class="section-heading history-heading">
        <h3 class="inspection-section-title">执行记录</h3>
        <el-checkbox v-model="showHistory">显示历史执行与尝试</el-checkbox>
      </div>
      <InspectionUmcExecutionResults
        :task="task"
        :executions="displayedExecutions"
        :results="displayResults"
        :disabled="disabled"
        @refresh="emit('refresh', $event)"
        @download-log="emit('downloadLog', $event)"
      />
    </template>
    <template v-else>
      <div class="section-heading history-heading"
        ><h3 class="inspection-section-title">执行记录</h3
        ><el-checkbox v-if="umcMode" v-model="showHistory">显示历史执行与尝试</el-checkbox
        ><span v-else class="help-text">当前与历史尝试均保留</span></div
      >
      <el-table
        :data="displayedExecutions"
        :row-key="(row: ExecutionView) => String(row.id)"
        empty-text="暂无执行记录"
      >
        <el-table-column type="expand" width="42"
          ><template #default="{ row }">
            <el-descriptions :column="smallScreen ? 1 : 2" class="record-details">
              <el-descriptions-item label="识别到的 SN">{{
                row.recognizedSerial || '未取得'
              }}</el-descriptions-item>
              <el-descriptions-item label="解析来源">{{
                row.parserRelease || '未取得'
              }}</el-descriptions-item>
              <el-descriptions-item label="用途">{{
                purposeLabel(row.purposeCode)
              }}</el-descriptions-item>
              <el-descriptions-item label="提交时间">{{
                row.submittedAt ? formatDate(row.submittedAt) : '—'
              }}</el-descriptions-item>
              <el-descriptions-item label="失败 / 异常说明">{{
                row.failureMessage || row.failureCode || '—'
              }}</el-descriptions-item>
            </el-descriptions>
          </template></el-table-column
        >
        <el-table-column v-if="!umcMode" label="执行记录" min-width="135"
          ><template #default="{ row }"
            >{{ row.id
            }}<el-tag v-if="isCurrent(row.attemptId)" size="small" class="current-tag"
              >当前尝试</el-tag
            ><p class="help-text">{{ attemptLabel(row.attemptId) }}</p></template
          ></el-table-column
        >
        <el-table-column label="设备 / SN" min-width="230"
          ><template #default="{ row }">{{ deviceLabel(row.deviceId) }}</template></el-table-column
        >
        <el-table-column v-if="umcMode" label="UMC识别型号" min-width="150"
          ><template #default="{ row }">{{
            umcForExecution(row)?.result?.devmodel || '等待识别'
          }}</template></el-table-column
        >
        <el-table-column label="状态" min-width="150"
          ><template #default="{ row }"
            ><el-tag :type="executionStatusType(row.statusCode)">{{
              umcMode
                ? umcExecutionStatus(row)
                : umcForExecution(row)?.statusCode === 'AVAILABLE'
                  ? 'UMC结果已获取'
                  : executionStatus(row.statusCode)
            }}</el-tag
            ><div
              v-if="!umcMode && umcForExecution(row)?.statusCode === 'AVAILABLE'"
              class="help-text"
              >平台解析：{{ executionStatus(row.statusCode) }}</div
            ></template
          ></el-table-column
        >
        <el-table-column prop="fileName" label="日志文件" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" min-width="180" :fixed="smallScreen ? false : 'right'">
          <template #default="{ row }">
            <el-button
              v-if="
                (row.purposeCode === 'OFFLINE' ||
                  (umcMode && row.purposeCode === 'COLLECT' && hasFrozenLog(row))) &&
                isCurrent(row.attemptId) &&
                (umcForExecution(row) || ['PENDING', 'RUNNING'].includes(row.statusCode))
              "
              link
              type="primary"
              :disabled="disabled"
              @click="emit('refresh', row.id)"
              >{{
                umcMode
                  ? row.statusCode === 'COMPLETE'
                    ? '更新结果'
                    : '获取 / 重试结果'
                  : umcForExecution(row)
                    ? '获取执行结果'
                    : '刷新解析'
              }}</el-button
            >
            <el-button
              v-if="hasFrozenLog(row)"
              link
              type="primary"
              @click="emit('downloadLog', row)"
              >下载冻结日志</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <div class="section-heading history-heading"
        ><h3 class="inspection-section-title">巡检判定结果</h3
        ><el-tag type="info"
          >{{ displayResults.length }} 条规则结果 · {{ umcTargetCount }} 项UMC指标</el-tag
        ></div
      >
      <el-alert
        title="未判定、判定错误或缺失输出均不表示通过。展开记录可查看来源，历史判定保留。"
        type="warning"
        :closable="false"
        show-icon
        class="mb-16px"
      />
      <div v-if="umcMode && displayResults.length" class="section-heading mb-12px">
        <span class="help-text"
          >完整保留UMC返回的全部规则。正常项可展开查看；命令原文按需打开。</span
        >
        <div
          ><el-button
            link
            @click="
              expandedResultKeys = displayResults
                .filter((r) => r.externalRule)
                .map((r) => String(r.id))
            "
            >展开全部明细</el-button
          ><el-button link @click="expandedResultKeys = []">收起明细</el-button></div
        >
      </div>
      <el-table
        :data="displayResults"
        data-testid="inspection-judgement-results"
        :row-key="(row: ResultView) => String(row.id)"
        :expand-row-keys="expandedResultKeys"
        @expand-change="updateExpandedResults"
        empty-text="暂无判定结果，不能视为巡检通过"
      >
        <el-table-column type="expand" width="42"
          ><template #default="{ row }">
            <el-descriptions :column="smallScreen ? 1 : 3" class="record-details">
              <el-descriptions-item label="结果记录">{{ row.id }}</el-descriptions-item>
              <el-descriptions-item label="执行记录">{{
                row.executionId ?? '未提供'
              }}</el-descriptions-item>
              <el-descriptions-item label="尝试">{{
                attemptLabel(row.attemptId)
              }}</el-descriptions-item>
            </el-descriptions>
            <template v-if="row.externalRule">
              <dl class="device-facts">
                <div
                  ><dt>UMC识别型号</dt><dd>{{ resultDevice(row)?.devmodel || '未提供' }}</dd></div
                >
                <div
                  ><dt>软件版本</dt><dd>{{ resultDevice(row)?.version || '未提供' }}</dd></div
                >
                <div
                  ><dt>设备IP</dt><dd>{{ resultDevice(row)?.deviceip || '未提供' }}</dd></div
                >
                <div
                  ><dt>设备说明</dt><dd>{{ resultDevice(row)?.info || '无' }}</dd></div
                >
              </dl>
              <InspectionUmcRuleDetail :rule="row.externalRule" />
            </template>
            <template v-else>
              <el-descriptions :column="smallScreen ? 1 : 3">
                <el-descriptions-item label="分类">{{
                  row.categoryName || row.categoryCode || '未提供'
                }}</el-descriptions-item>
                <el-descriptions-item label="严重级别">{{
                  row.severityName || severityLabel(row.severityCode)
                }}</el-descriptions-item>
                <el-descriptions-item label="实测值 / 单位"
                  >{{ row.measuredValue ?? '未取得' }}
                  {{ row.measuredUnit || '' }}</el-descriptions-item
                >
                <el-descriptions-item label="正则匹配">{{
                  row.regexMatched == null ? '未判定' : row.regexMatched ? '匹配' : '不匹配'
                }}</el-descriptions-item>
              </el-descriptions>
              <pre class="result-output">{{ row.outputSummary || '未提供输出' }}</pre>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="设备 / SN" min-width="210"
          ><template #default="{ row }"
            >{{ deviceLabel(row.deviceId) }}
            <div class="help-text"
              >{{ attemptLabel(row.attemptId) }} ·
              {{ isCurrent(row.attemptId) ? '当前尝试' : '历史尝试' }}</div
            >
          </template></el-table-column
        >
        <el-table-column label="规则 / 来源" min-width="210"
          ><template #default="{ row }"
            >{{ row.ruleName || '未提供规则名称'
            }}<div class="help-text">{{
              row.externalRule
                ? `UMC规则 ${row.externalRule.ruleid} · 报告${row.reportId}`
                : `平台冻结规则 · ${row.ruleRevisionId ?? '未提供修订'}`
            }}</div
            ><div v-if="row.externalRule" class="help-text"
              >{{ row.categoryName || '未分类' }} · {{ row.severityName }}</div
            ></template
          ></el-table-column
        >
        <el-table-column label="判定" width="110"
          ><template #default="{ row }"
            ><el-tag :type="outcomeType(row.outcome)">{{
              outcomeLabel(row.outcome)
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="判定说明" min-width="210"
          ><template #default="{ row }">{{
            row.detailMessage ||
            (row.sourceCode === 'UMC' ? 'UMC未提供判定说明' : row.detailCode || '未提供说明')
          }}</template></el-table-column
        >
      </el-table>
    </template>
    <el-alert
      v-for="saved in pendingUmcResults"
      :key="String(saved.reportId)"
      class="mt-12px"
      type="warning"
      :closable="false"
      :title="
        saved.failureMessage ||
        `执行记录${saved.executionId}的UMC结果尚未取得，请点击获取执行结果。`
      "
    />

    <el-alert
      v-if="currentAttempt && missingPlatformResults"
      class="mt-16px"
      type="warning"
      :closable="false"
      show-icon
      title="UMC结果已保存，但原流程所需的平台规则判定尚未形成，暂不能完成执行。"
    />
    <div v-if="currentAttempt" class="finish-actions">
      <p class="help-text">{{
        umcMode
          ? '每台设备均取得完整UMC正式判定后，可进入报告阶段。'
          : '检测结果完整且日志解析结束后，可进入报告阶段。'
      }}</p>
      <Teleport :to="actionsTarget || 'body'" :disabled="!actionsTarget">
        <el-button
          type="primary"
          :disabled="disabled || missingPlatformResults || (umcMode && !umcComplete)"
          @click="emit('finish')"
          >完成执行并进入报告</el-button
        >
      </Teleport>
    </div>
    <el-collapse class="attempt-history">
      <el-collapse-item
        v-if="task.collectionReceipts?.length"
        title="查看在线采集回传版本"
        name="receipts"
      >
        <p class="help-text">每个回传版本独立保留；后续版本不会替换已采用的巡检结果。</p>
        <el-table :data="task.collectionReceipts" :row-key="(row) => String(row.id)">
          <el-table-column prop="executionId" label="巡检执行记录" min-width="170" />
          <el-table-column prop="collectionTaskId" label="平台采集任务" min-width="220" />
          <el-table-column prop="resultVersion" label="结果版本" width="100" />
          <el-table-column prop="externalStatus" label="采集状态" min-width="130" />
          <el-table-column prop="failureCategory" label="失败分类" min-width="150" />
          <el-table-column label="接收时间" min-width="170"
            ><template #default="{ row }">{{
              formatDate(row.receivedAt)
            }}</template></el-table-column
          >
        </el-table>
      </el-collapse-item>
      <el-collapse-item title="查看全部执行尝试" name="attempts">
        <el-table
          :data="task.attempts"
          :row-key="(row: AttemptView) => String(row.id)"
          empty-text="暂无执行尝试"
        >
          <el-table-column label="尝试" min-width="150"
            ><template #default="{ row }">{{ attemptLabel(row.id) }}</template></el-table-column
          >
          <el-table-column label="方式" width="110"
            ><template #default="{ row }">{{
              row.mode === 'ONLINE' ? '在线巡检' : '离线巡检'
            }}</template></el-table-column
          >
          <el-table-column label="状态" min-width="110"
            ><template #default="{ row }">{{
              attemptStatus(row.statusCode)
            }}</template></el-table-column
          >
          <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
          <el-table-column label="开始时间" min-width="170"
            ><template #default="{ row }">{{
              row.startedAt ? formatDate(row.startedAt) : '—'
            }}</template></el-table-column
          >
          <el-table-column label="完成时间" min-width="170"
            ><template #default="{ row }">{{
              row.finishedAt ? formatDate(row.finishedAt) : '未完成'
            }}</template></el-table-column
          >
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import type { TagProps } from 'element-plus'
import type {
  TaskDetail,
  CommandView,
  ExecutionView,
  ResultView,
  AttemptView,
  WireLong
} from '@/api/pms/inspection/tasks'
import { formatDate } from '@/utils/formatTime'
import { getStrDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import InspectionUmcRuleDetail from './InspectionUmcRuleDetail.vue'
import InspectionUmcExecutionResults from './InspectionUmcExecutionResults.vue'

defineOptions({ name: 'InspectionExecutionPanel' })
const props = defineProps<{
  task: TaskDetail
  disabled: boolean
  commands: CommandView[]
  selectedDeviceId: WireLong | string
  umcMode?: boolean
  actionsTarget?: HTMLElement
}>()
const emit = defineEmits<{
  deviceChange: [deviceId: WireLong]
  upload: [input: { deviceId: WireLong; file: File; commandHint?: string; sourceHost: string }]
  uploadFiles: [input: { files: File[]; sourceHosts: { deviceId: WireLong; host: string }[] }]
  uploadBatch: [input: { file: File; sourceHosts: { deviceId: WireLong; host: string }[] }]
  refresh: [executionId: WireLong]
  finish: []
  downloadLog: [execution: ExecutionView]
  draftChanged: [dirty: boolean]
}>()
const message = useMessage()
const umcForExecution = (execution: ExecutionView) =>
  [...(props.task.umcResults || [])]
    .reverse()
    .find((row) => String(row.executionId) === String(execution.id))
const pendingUmcResults = computed(() =>
  (props.task.umcResults || []).filter((row) => row.statusCode !== 'AVAILABLE')
)
const umcComplete = computed(() => {
  const current = props.task.executions.filter(
    (row) => isCurrent(row.attemptId) && row.statusCode !== 'CANCELLED'
  )
  if (
    !props.task.devices.length ||
    current.some((row) =>
      ['PENDING', 'RUNNING', 'COLLECTED', 'CANCEL_REQUESTED'].includes(row.statusCode)
    )
  )
    return false
  return props.task.devices.every((device) => {
    const latest = current
      .filter(
        (row) => String(row.deviceId) === String(device.deviceId) && row.purposeCode !== 'PRECHECK'
      )
      .sort((a, b) => (BigInt(String(a.id)) > BigInt(String(b.id)) ? -1 : 1))[0]
    if (!latest || !['COMPLETE', 'PARTIAL', 'FAILED'].includes(latest.statusCode)) return false
    const saved = umcForExecution(latest)
    const rules = saved?.result?.rulelist
    return (
      saved?.statusCode === 'AVAILABLE' &&
      Array.isArray(rules) &&
      props.task.results.filter(
        (row) =>
          row.sourceCode === 'UMC' &&
          String(row.executionId) === String(latest.id) &&
          String(row.reportId) === String(saved.reportId) &&
          String(row.attemptId) === String(latest.attemptId)
      ).length === rules.length
    )
  })
})
// Explain an existing server-side blocker rather than presenting UMC availability as lifecycle completion.
const missingPlatformResults = computed(
  () =>
    !props.umcMode &&
    (props.task.umcResults || []).some(
      (saved) => isCurrent(saved.attemptId) && saved.statusCode === 'AVAILABLE'
    ) &&
    !props.task.results.some((result) => isCurrent(result.attemptId))
)
const showHistory = ref(false)
const latestExecutions = computed(() => {
  const latest = new Map<string, ExecutionView>()
  for (const row of props.task.executions) {
    if (
      String(row.attemptId) !== String(props.task.currentAttemptId) ||
      row.statusCode === 'CANCELLED' ||
      row.purposeCode === 'PRECHECK'
    )
      continue
    const old = latest.get(String(row.deviceId))
    if (!old || BigInt(String(row.id)) > BigInt(String(old.id)))
      latest.set(String(row.deviceId), row)
  }
  return latest
})
const latestIds = computed(
  () => new Set([...latestExecutions.value.values()].map((r) => String(r.id)))
)
const umcExecutionStatus = (row: ExecutionView) => {
  const saved = umcForExecution(row)
  if (
    ['COMPLETE', 'PARTIAL', 'FAILED'].includes(row.statusCode) &&
    saved?.statusCode === 'AVAILABLE' &&
    Array.isArray(saved.result?.rulelist) &&
    props.task.results.filter(
      (r) =>
        r.sourceCode === 'UMC' &&
        String(r.executionId) === String(row.id) &&
        String(r.reportId) === String(saved.reportId) &&
        String(r.attemptId) === String(row.attemptId)
    ).length === saved.result.rulelist.length
  )
    return '判定完成'
  if (['PARTIAL', 'FAILED', 'CANCELLED'].includes(row.statusCode))
    return executionStatus(row.statusCode)
  if (saved?.failureMessage) return '未完成，需重试'
  return saved?.statusCode === 'AVAILABLE' ? '报告已保存，待登记判定' : '等待UMC结果'
}
const currentExecutions = computed(() =>
  props.task.executions.filter(
    (row) =>
      String(row.attemptId) === String(props.task.currentAttemptId) &&
      (latestIds.value.has(String(row.id)) ||
        ['PENDING', 'RUNNING', 'COLLECTED', 'CANCEL_REQUESTED'].includes(row.statusCode))
  )
)
const hasExecutionHistory = computed(() => {
  const current = new Set(currentExecutions.value.map((row) => String(row.id)))
  return props.task.executions.some((row) => !current.has(String(row.id)))
})
watch(hasExecutionHistory, (available) => {
  if (!available) showHistory.value = false
})
const displayedExecutions = computed(() =>
  !props.umcMode || showHistory.value ? props.task.executions : currentExecutions.value
)
const allResults = computed<ResultView[]>(() => [
  ...props.task.results,
  ...(props.task.umcResults || [])
    .filter(
      (saved) =>
        !props.task.results.some(
          (row) => row.sourceCode === 'UMC' && String(row.reportId) === String(saved.reportId)
        )
    )
    .flatMap((saved) =>
      (saved.result?.rulelist || []).map((rule) => ({
        id: `umc:${saved.reportId}:${rule.id}`,
        attemptId: saved.attemptId,
        executionId: saved.executionId,
        deviceId: saved.deviceId,
        reportId: saved.reportId,
        sourceCode: 'UMC',
        externalRule: rule,
        ruleName: rule.name,
        categoryName: rule.type,
        severityName: rule.level == null ? '未提供' : `UMC ${rule.level}`,
        outcome:
          rule.support === false
            ? 'UNJUDGED'
            : rule.error
              ? 'ERROR'
              : rule.result === true
                ? 'ABNORMAL'
                : rule.result === false
                  ? 'PASS'
                  : 'UNJUDGED',
        outputSummary: rule.output,
        detailMessage: [rule.error, rule.info, rule.suggest].filter(Boolean).join('；')
      }))
    )
])
const displayResults = computed(() =>
  !props.umcMode || showHistory.value
    ? allResults.value
    : allResults.value.filter(
        (row) =>
          String(row.attemptId) === String(props.task.currentAttemptId) &&
          latestIds.value.has(String(row.executionId))
      )
)
const resultDevice = (row: ResultView) =>
  props.task.umcResults?.find((saved) => String(saved.reportId) === String(row.reportId))?.result
const umcTargetCount = computed(() =>
  displayResults.value.reduce((sum, row) => sum + (row.externalRule?.targetlist?.length || 0), 0)
)
const expandedResultKeys = ref<string[]>([])
watch(
  () => displayResults.value.map((row) => String(row.id)).join(','),
  () => {
    expandedResultKeys.value = displayResults.value
      .filter((row) => row.externalRule && (!props.umcMode || row.outcome !== 'PASS'))
      .map((row) => String(row.id))
  },
  { immediate: true }
)
const updateExpandedResults = (_row: ResultView, expanded: ResultView[]) => {
  expandedResultKeys.value = expanded.map((row) => String(row.id))
}
const smallScreen = useMediaQuery('(max-width: 767px)')
const fileInput = ref<HTMLInputElement>()
const selectedFile = ref<File | null>(null)
const selectedFiles = ref<File[]>([])
const uploadMode = ref<'single' | 'batch'>('single')
const canSelectFile = computed(
  () =>
    !props.disabled &&
    isOffline.value &&
    (props.umcMode || uploadMode.value === 'batch' || !!props.selectedDeviceId)
)
const commandHint = ref('')
const sourceHost = ref('')
const sourceHosts = reactive<Record<string, string>>({})
const originalFilenameHost = (name: string) => {
  if (
    name.length > 255 ||
    /[\\/:*?"<>|]/.test(name) ||
    [...name].some((char) => char.charCodeAt(0) < 32 || char.charCodeAt(0) === 127)
  )
    return ''
  const stem = name.replace(/\.(txt|log)$/i, '')
  const match = /^([^()（）]*?)[(（]([^()（）]+)[)）]$/.exec(stem)
  const host = match ? match[2] : stem
  return /^[0-9]{1,3}(\.[0-9]{1,3}){3}$/.test(host) &&
    host.split('.').every((part) => Number(part) <= 255 && String(Number(part)) === part)
    ? host
    : ''
}
const inferredSourceHost = computed(() =>
  props.umcMode && props.task.devices.length === 1 && selectedFiles.value.length === 1
    ? originalFilenameHost(selectedFiles.value[0].name)
    : ''
)
const fileError = ref('')
const fileNotice = ref('')
const currentAttempt = computed(() =>
  props.task.attempts.find((attempt) => String(attempt.id) === String(props.task.currentAttemptId))
)
const isOffline = computed(() => currentAttempt.value?.mode === 'OFFLINE')
const isCurrent = (attemptId?: WireLong) =>
  attemptId != null &&
  props.task.currentAttemptId != null &&
  String(attemptId) === String(props.task.currentAttemptId)
const orderedCommands = computed(() => props.commands)
const commandText = computed(() =>
  orderedCommands.value.map((command) => command.content).join('\n\n')
)
const commandKey = (command: CommandView) => String(command.sequenceNo)
const hintCommands = computed(() =>
  orderedCommands.value.filter(
    (command, index, all) =>
      all.findIndex((candidate) => candidate.content === command.content) === index
  )
)
const deviceLabel = (id: WireLong) => {
  const device = props.task.devices.find((item) => String(item.deviceId) === String(id))
  return device
    ? [device.serialNumber || '未提供 SN', device.deviceName, device.productModel]
        .filter(Boolean)
        .join(' · ')
    : `设备 ${id}`
}
const attemptLabel = (id?: WireLong) => {
  if (id == null) return '未提供尝试'
  const attempt = props.task.attempts.find((item) => String(item.id) === String(id))
  return attempt?.attemptNo != null ? `第 ${attempt.attemptNo} 次 · ${id}` : String(id)
}
const attemptStatuses: Record<string, string> = {
  ACTIVE: '进行中',
  RUNNING: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  FAILED: '失败'
}
const executionStatuses: Record<string, string> = {
  PENDING: '待处理',
  RUNNING: '处理中',
  COLLECTED: '已采集，待解析',
  PASSED: '预检通过',
  CANCEL_REQUESTED: '取消待确认',
  COMPLETE: '已完成',
  PARTIAL: '部分失败',
  FAILED: '失败',
  CANCELLED: '已取消',
  SUCCEEDED: '成功',
  TIMED_OUT: '超时'
}
const purposes: Record<string, string> = {
  OFFLINE: '离线上传',
  PRECHECK: '连接预检',
  COLLECT: '在线采集'
}
const attemptStatus = (status: string) => attemptStatuses[status] || status
const executionStatus = (status: string) => executionStatuses[status] || status
const purposeLabel = (purpose?: string) => (purpose ? purposes[purpose] || purpose : '未提供')
const executionStatusType = (status: string): TagProps['type'] =>
  ['FAILED', 'TIMED_OUT'].includes(status)
    ? 'danger'
    : status === 'PARTIAL'
      ? 'warning'
      : ['COMPLETE', 'SUCCEEDED'].includes(status)
        ? 'success'
        : 'info'
const outcomes: Record<string, string> = {
  PASS: '通过',
  ABNORMAL: '异常',
  UNJUDGED: '未判定',
  ERROR: '判定错误'
}
const outcomeLabel = (outcome?: string) => outcomes[outcome || ''] || outcome || '未判定'
const outcomeType = (outcome?: string): TagProps['type'] =>
  outcome === 'PASS'
    ? 'success'
    : outcome === 'ERROR' || outcome === 'ABNORMAL'
      ? 'danger'
      : 'warning'
const severities = computed(() => getStrDictOptions('pms_inspection_rule_severity'))
const severityLabel = (code?: string) =>
  severities.value.find((item) => item.value === code)?.label || code || '未提供'
const hasFrozenLog = (execution: ExecutionView) =>
  execution.artifactId != null && execution.fileVersionNo != null && !!execution.fileReferenceKey
const fileSize = (size: number) =>
  size >= 1024 * 1024 ? `${(size / (1024 * 1024)).toFixed(1)} MB` : `${(size / 1024).toFixed(1)} KB`

const clearDraft = () => {
  selectedFile.value = null
  selectedFiles.value = []
  commandHint.value = ''
  sourceHost.value = ''
  Object.keys(sourceHosts).forEach((key) => delete sourceHosts[key])
  fileError.value = ''
  fileNotice.value = ''
  if (fileInput.value) fileInput.value.value = ''
  emit('draftChanged', false)
}
const changeDevice = (deviceId: WireLong) => {
  clearDraft()
  emit('deviceChange', deviceId)
}
const acceptFile = (file: File | undefined) => {
  if (!canSelectFile.value) return
  selectedFile.value = null
  fileError.value = ''
  fileNotice.value = ''
  if (fileInput.value) fileInput.value.value = ''
  if (!file) return
  if (
    !/\.(log|txt|zip)$/i.test(file.name) &&
    !(props.umcMode && uploadMode.value === 'single' && originalFilenameHost(file.name))
  )
    fileError.value =
      props.umcMode && uploadMode.value === 'single'
        ? '请选择 .log、.txt、.zip，或以IPv4、设备名称(IPv4)命名的原始日志文件。'
        : '仅支持 .log、.txt、.zip 文件。'
  else if (!file.size) fileError.value = '日志文件不能为空。'
  else if (file.size > (uploadMode.value === 'batch' ? 20 : 50) * 1024 * 1024)
    fileError.value = `单文件不能超过 ${uploadMode.value === 'batch' ? 20 : 50}MB，请重新选择。`
  if (fileError.value) {
    return
  }
  selectedFile.value = file
}
const addFiles = (files: File[]) => {
  if (!canSelectFile.value) return
  fileError.value = ''
  fileNotice.value = ''
  if (fileInput.value) fileInput.value.value = ''
  if (!files.length) return
  const combined = [...selectedFiles.value]
  const duplicates = new Set<string>()
  for (const file of files) {
    if (!/\.(log|txt|zip)$/i.test(file.name) && !originalFilenameHost(file.name)) {
      fileError.value = '请选择 .log、.txt、.zip，或以IPv4、设备名称(IPv4)命名的原始日志文件。'
      return
    }
    if (!file.size || file.size > 20 * 1024 * 1024) {
      fileError.value = '日志不能为空，单份日志不能超过20MB。'
      return
    }
    if (
      combined.some(
        (item) =>
          item.name === file.name &&
          item.size === file.size &&
          item.lastModified === file.lastModified
      )
    ) {
      duplicates.add(file.name)
      continue
    }
    combined.push(file)
  }
  if (
    combined.length > 1024 ||
    combined.reduce((total, file) => total + file.size, 0) > 20 * 1024 * 1024
  ) {
    fileError.value = '所有日志合计不能超过20MB，最多选择1024份。'
    return
  }
  selectedFiles.value = combined
  if (duplicates.size)
    fileNotice.value = `已跳过重复文件：${[...duplicates].join('、')}。其他文件可继续上传。`
}
const removeFile = (index: number) => {
  if (props.disabled) return
  selectedFiles.value = selectedFiles.value.filter((_file, position) => position !== index)
  fileError.value = ''
  fileNotice.value = ''
}
const selectFile = (event: Event) => {
  const files = (event.target as HTMLInputElement).files
  if (!canSelectFile.value) return
  if (props.umcMode) {
    addFiles(Array.from(files || []))
    return
  }
  if (files && files.length > 1) {
    acceptFile(undefined)
    fileError.value = '一次只能选择一个日志文件。'
    return
  }
  acceptFile(files?.[0])
}
const dropFiles = (event: DragEvent) => {
  if (!canSelectFile.value) return
  const files = event.dataTransfer?.files
  if (props.umcMode) {
    addFiles(Array.from(files || []))
    return
  }
  if (!files || files.length !== 1) {
    acceptFile(undefined)
    fileError.value = '一次只能选择一个日志文件。'
    return
  }
  acceptFile(files[0])
}
const submitUpload = () => {
  if (props.umcMode) {
    if (
      props.disabled ||
      !isOffline.value ||
      !currentAttempt.value ||
      !selectedFiles.value.length ||
      !canSelectFile.value
    )
      return
    const bindings = props.task.devices
      .filter((device) => sourceHosts[String(device.deviceId)]?.trim())
      .map((device) => ({
        deviceId: device.deviceId,
        host: sourceHosts[String(device.deviceId)].trim()
      }))
    if (!bindings.length && !selectedFiles.value.every((file) => originalFilenameHost(file.name) || /\.zip$/i.test(file.name))) {
      fileError.value = '请为本次日志涉及的设备填写来源IP。'
      return
    }
    if (
      bindings.some(
        (binding) =>
          !originalFilenameHost(binding.host) || originalFilenameHost(binding.host) !== binding.host
      )
    ) {
      fileError.value = '日志来源IP必须是合法的IPv4地址。'
      return
    }
    fileError.value = ''
    emit('uploadFiles', { files: [...selectedFiles.value], sourceHosts: bindings })
    return
  }
  if (
    props.disabled ||
    !isOffline.value ||
    !currentAttempt.value ||
    !selectedFile.value ||
    !canSelectFile.value
  )
    return
  if (uploadMode.value === 'batch') {
    const bindings = props.task.devices
      .filter((d) => sourceHosts[String(d.deviceId)]?.trim())
      .map((d) => ({ deviceId: d.deviceId, host: sourceHosts[String(d.deviceId)].trim() }))
    if (!bindings.length) {
      fileError.value = '请为日志中的设备填写来源IP。'
      return
    }
    emit('uploadBatch', { file: selectedFile.value, sourceHosts: bindings })
    return
  }
  if (!sourceHost.value.trim() && !inferredSourceHost.value) {
    fileError.value = '请填写日志来源设备IP。'
    return
  }
  if (
    commandHint.value &&
    !props.commands.some((command) => command.content === commandHint.value)
  ) {
    fileError.value = '命令提示已不在当前冻结清单中，请重新选择。'
    return
  }
  emit('upload', {
    deviceId: props.selectedDeviceId,
    file: selectedFile.value,
    sourceHost: sourceHost.value.trim(),
    ...(commandHint.value ? { commandHint: commandHint.value } : {})
  })
}
const copyCommands = async () => {
  if (!commandText.value) return
  try {
    await navigator.clipboard.writeText(commandText.value)
    message.success('冻结命令已复制')
  } catch {
    message.warning('复制失败，请从命令清单中手动复制。')
  }
}
const downloadCommands = () => {
  if (!commandText.value) return
  const url = URL.createObjectURL(
    new Blob([commandText.value], { type: 'text/plain;charset=utf-8' })
  )
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${props.task.taskCode}-${String(props.selectedDeviceId)}-命令清单.txt`.replace(
    /[<>:"/\\|?*]/g,
    '_'
  )
  anchor.click()
  URL.revokeObjectURL(url)
}
watch(() => [String(props.task.id), String(props.task.version)], clearDraft)
watch(uploadMode, clearDraft)
watch(
  () => String(props.selectedDeviceId),
  () => {
    if (!props.umcMode) clearDraft()
  }
)
watch(
  () => props.commands,
  () => {
    if (
      commandHint.value &&
      !props.commands.some((command) => command.content === commandHint.value)
    )
      commandHint.value = ''
  }
)
watch([selectedFile, selectedFiles, commandHint, sourceHost, sourceHosts], () =>
  emit(
    'draftChanged',
    !!selectedFile.value ||
      !!selectedFiles.value.length ||
      !!commandHint.value ||
      !!sourceHost.value ||
      Object.values(sourceHosts).some(Boolean)
  )
)
</script>

<style scoped>
.execution-panel {
  min-width: 0;
}
.device-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  margin: 12px 16px;
  overflow-wrap: anywhere;
}
.device-facts div {
  display: flex;
  gap: 8px;
}
.device-facts dt {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}
.device-facts dd {
  margin: 0;
}
.result-output {
  padding: 12px 16px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.section-heading h2,
.section-heading h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.section-heading h3 {
  font-size: 14px;
}
.inspection-section-title {
  display: inline-block;
  padding-bottom: 6px;
  color: #1a2332;
  border-bottom: 2px solid var(--inspection-blue, #4a90e2);
}
.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.upload-form {
  margin-top: 16px;
}
.upload-form :deep(.el-select) {
  width: 100%;
}
.upload-fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
  margin-top: 16px;
}
.upload-form :deep(.el-form-item) {
  margin-bottom: 14px;
}
.command-list {
  margin-bottom: 16px;
  --el-collapse-border-color: #f0f2f5;
}
.upload-form > .section-heading {
  margin-top: 24px;
}
.command-content {
  margin: 0;
  font-family: Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.help-text {
  margin: 8px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
.file-drop-zone {
  position: relative;
  width: 100%;
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
}
.file-drop-zone:not(.is-disabled):hover {
  color: #4a90e2;
  border-color: #4a90e2;
}
.file-picker {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  min-height: 100px;
  padding: 12px;
  color: #888;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  background: #fff;
  border: 0;
  border-radius: 4px;
}
.file-picker small {
  font-size: 12px;
}
.file-picker:disabled {
  cursor: not-allowed;
  background: #fafbfc;
}
.record-details {
  padding: 12px 20px;
}
.file-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  margin-top: 8px;
  overflow-wrap: anywhere;
}
.file-error {
  width: 100%;
  margin: 8px 0;
  color: var(--el-color-danger);
}
.history-heading {
  margin-top: 20px;
}
.current-tag {
  margin-left: 8px;
}
.finish-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 24px;
}
.attempt-history {
  margin-top: 24px;
}
@media (max-width: 767px) {
  .device-facts {
    grid-template-columns: minmax(0, 1fr);
  }
  .section-heading,
  .finish-actions {
    align-items: flex-start;
    flex-direction: column;
  }
  .upload-fields {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .section-actions {
    flex-wrap: wrap;
  }
}
</style>
