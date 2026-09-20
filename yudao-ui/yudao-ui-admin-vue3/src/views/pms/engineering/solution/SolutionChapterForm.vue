<template>
  <div class="solution-chapter-form">
    <el-alert
      title="按项目交付 Demo 4.1 编写实施方案的九章结构组织：前序阶段（需求分析 2.3、团队 1.2、产品清单 1.1、施工计划 3.1、序列号 1.1.1）可通过“引用/同步”带入正文；正文随方案记录保存。"
      type="info"
      :closable="false"
      class="mb-12px"
    />

    <!-- 基础信息 -->
    <section class="chapter">
      <div class="chapter-title">0. 方案基础信息</div>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="方案名称" prop="name"><el-input v-model="form.name" :disabled="readOnly || !!form.id" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="方案类型" prop="solutionType"><el-input v-model="form.solutionType" :disabled="readOnly" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="版本标签" prop="versionLabel"><el-input v-model="form.versionLabel" :disabled="readOnly" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="审核级别" prop="reviewLevel">
            <el-select v-model="form.reviewLevel" class="!w-full" :disabled="readOnly">
              <el-option
                v-for="dict in getIntDictOptions(DICT_TYPE.PMS_REVIEW_LEVEL)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户方案">
            <el-radio-group v-model="meta.hasCustomerPlan" :disabled="readOnly">
              <el-radio value="yes">已有客户方案</el-radio>
              <el-radio value="no">无</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col v-if="meta.hasCustomerPlan === 'yes'" :span="12">
          <el-form-item label="上传方案文件">
            <UploadFile v-model="meta.customerPlanUrl" :disabled="readOnly" />
          </el-form-item>
        </el-col>
        <el-col v-if="meta.hasCustomerPlan === 'yes'" :span="24">
          <el-button disabled title="客户方案系统识别未接入，请下载后人工核对再录入正文">提交系统识别</el-button>
        </el-col>
      </el-row>
    </section>

    <!-- 第1章 项目概述 -->
    <section class="chapter">
      <div class="chapter-title">1. 项目概述</div>
      <div class="ref-block">
        <div class="ref-head">
          <span>1.1 项目背景（引用 2.3 需求分析）</span>
          <el-button link type="primary" :disabled="readOnly || !reqBackground" @click="form.background = reqBackground">引用到正文</el-button>
        </div>
        <div class="ref-body">{{ reqBackground ? stripHtml(reqBackground) : '需求分析暂无项目背景' }}</div>
      </div>
      <el-form-item label="1.1 项目背景" label-width="140px">
        <Editor v-model="form.background" height="180px" :readonly="readOnly" />
      </el-form-item>
      <div class="ref-block">
        <div class="ref-head">
          <span>1.2 项目目标（引用 2.3 需求分析）</span>
          <el-button link type="primary" :disabled="readOnly || !reqObjective" @click="form.target = reqObjective">引用到正文</el-button>
        </div>
        <div class="ref-body">{{ reqObjective ? stripHtml(reqObjective) : '需求分析暂无项目目标' }}</div>
      </div>
      <el-form-item label="1.2 项目目标" label-width="140px">
        <Editor v-model="form.target" height="180px" :readonly="readOnly" />
      </el-form-item>

      <div class="ref-block">
        <div class="ref-head">
          <span>1.3 项目团队（调用 1.2 团队成员）</span>
          <el-button link type="primary" :disabled="readOnly || !members.length" @click="tables.team = memberRows()">引用到正文</el-button>
        </div>
        <el-table :data="members" size="small" border max-height="200">
          <el-table-column label="角色" width="150">
            <template #default="{ row }">{{ memberRoleLabel(row.memberRole) }}</template>
          </el-table-column>
          <el-table-column prop="memberName" label="姓名" width="120" />
          <el-table-column prop="employeeNo" label="工号" width="120" />
          <el-table-column prop="status" label="状态" width="100" />
        </el-table>
        <el-empty v-if="!members.length" description="暂无团队成员" :image-size="50" />
      </div>
      <el-form-item label="1.3 项目团队" label-width="140px">
        <div class="table-editor">
          <el-table :data="tables.team" size="small" border>
            <el-table-column label="角色" min-width="130"><template #default="{ row }"><el-input v-model="row.role" :disabled="readOnly" placeholder="如：项目经理" /></template></el-table-column>
            <el-table-column label="姓名" min-width="110"><template #default="{ row }"><el-input v-model="row.name" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="联系方式" min-width="130"><template #default="{ row }"><el-input v-model="row.contact" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="备注" min-width="120"><template #default="{ row }"><el-input v-model="row.remark" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="操作" width="70" fixed="right">
              <template #default="{ $index }"><el-button link type="danger" :disabled="readOnly" @click="tables.team.splice($index, 1)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <el-button class="mt-4px" size="small" :disabled="readOnly" @click="tables.team.push({ role: '', name: '', contact: '', remark: '' })">添加成员</el-button>
        </div>
      </el-form-item>

      <div class="ref-block">
        <div class="ref-head">
          <span>1.4 项目清单（调用 1.1 产品信息清单）</span>
          <el-button link type="primary" :disabled="readOnly || !scopeRows.length" @click="tables.inventory = scopeProductRows()">引用到正文</el-button>
        </div>
        <el-table :data="scopeRows" size="small" border max-height="200">
          <el-table-column prop="productCode" label="产品编码" min-width="130" />
          <el-table-column prop="deviceTypeCode" label="产品型号" min-width="110" />
          <el-table-column prop="allocatedQuantity" label="数量" width="80" />
        </el-table>
        <el-empty v-if="!scopeRows.length" description="交付范围暂无产品明细" :image-size="50" />
      </div>
      <el-form-item label="1.4 项目清单" label-width="140px">
        <div class="table-editor">
          <el-table :data="tables.inventory" size="small" border>
            <el-table-column label="序号" type="index" width="55" />
            <el-table-column label="产品编码" min-width="130"><template #default="{ row }"><el-input v-model="row.productCode" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="产品型号" min-width="110"><template #default="{ row }"><el-input v-model="row.model" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="产品描述" min-width="140"><template #default="{ row }"><el-input v-model="row.desc" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="项目数量" width="90"><template #default="{ row }"><el-input v-model="row.projectQty" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="发货数量" width="90"><template #default="{ row }"><el-input v-model="row.shippedQty" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="未发货数量" width="95"><template #default="{ row }"><el-input v-model="row.unshippedQty" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="序列号" min-width="120"><template #default="{ row }"><el-input v-model="row.sn" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="操作" width="70" fixed="right">
              <template #default="{ $index }"><el-button link type="danger" :disabled="readOnly" @click="tables.inventory.splice($index, 1)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <el-button class="mt-4px" size="small" :disabled="readOnly" @click="tables.inventory.push(emptyInventoryRow())">添加行</el-button>
        </div>
      </el-form-item>

      <div class="ref-block">
        <div class="ref-head">
          <span>1.5 项目进度计划（调用 3.1 施工计划）</span>
          <el-button link type="primary" :disabled="readOnly || !stagePlanItems.length" @click="tables.plan = stagePlanRows()">引用到正文</el-button>
        </div>
        <el-table :data="stagePlanItems" size="small" border max-height="200">
          <el-table-column prop="phaseName" label="项目阶段" min-width="110" />
          <el-table-column prop="planStart" label="计划开始" min-width="140" />
          <el-table-column prop="planEnd" label="计划结束" min-width="140" />
          <el-table-column prop="remark" label="备注" min-width="120" />
        </el-table>
        <el-empty v-if="!stagePlanItems.length" description="暂无阶段施工计划批次" :image-size="50" />
      </div>
      <el-form-item label="1.5 进度计划" label-width="140px">
        <div class="table-editor">
          <el-table :data="tables.plan" size="small" border>
            <el-table-column label="项目阶段" min-width="110"><template #default="{ row }"><el-input v-model="row.stage" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="计划开始" min-width="150"><template #default="{ row }"><el-input v-model="row.start" :disabled="readOnly" placeholder="yyyy-MM-dd HH:mm" /></template></el-table-column>
            <el-table-column label="计划结束" min-width="150"><template #default="{ row }"><el-input v-model="row.end" :disabled="readOnly" placeholder="yyyy-MM-dd HH:mm" /></template></el-table-column>
            <el-table-column label="工期建议" min-width="120"><template #default="{ row }"><el-input v-model="row.suggest" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="备注" min-width="110"><template #default="{ row }"><el-input v-model="row.remark" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="操作" width="70" fixed="right">
              <template #default="{ $index }"><el-button link type="danger" :disabled="readOnly" @click="tables.plan.splice($index, 1)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <el-button class="mt-4px" size="small" :disabled="readOnly" @click="tables.plan.push({ stage: '', start: '', end: '', suggest: '', remark: '' })">添加行</el-button>
        </div>
      </el-form-item>
    </section>

    <!-- 第2章 现网现状分析 -->
    <section class="chapter">
      <div class="chapter-title">2. 现网现状分析</div>
      <el-form-item label="2.1 现网拓扑图" label-width="140px">
        <UploadFile v-model="topology.asIsUrl" :disabled="readOnly" />
      </el-form-item>
      <div class="ref-block">
        <div class="ref-head"><span>2.2 现网资源确认（引用 2.2 工勘 + 2.3 传输/流量/业务）</span></div>
        <div class="ref-body">
          <div>传输现状：{{ reqTransmission.join('、') || '—' }}</div>
          <div>流量现状：新建 {{ reqTraffic.new || '—' }} / 并发 {{ reqTraffic.concurrent || '—' }} / 吞吐 {{ reqTraffic.throughput || '—' }}</div>
          <div>工勘 10 项确认明细见详情页「2.2 工勘分工」环节。</div>
        </div>
      </div>
    </section>

    <!-- 第3章 总体方案设计 -->
    <section class="chapter">
      <div class="chapter-title">3. 总体方案设计</div>
      <el-form-item label="3.1 建设后拓扑" label-width="140px">
        <el-radio-group v-model="topology.toBeChanged" :disabled="readOnly">
          <el-radio value="no">与需求分析无变化</el-radio>
          <el-radio value="yes">有变化</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="topology.toBeChanged === 'no'" label-width="140px">
        <div class="ref-body standalone">直接调用 2.3 需求分析中的网络拓扑：{{ stripHtml(reqTopology) || '需求分析暂无网络拓扑' }}</div>
      </el-form-item>
      <el-form-item v-if="topology.toBeChanged === 'yes'" label="上传最新拓扑" label-width="140px">
        <UploadFile v-model="topology.toBeUrl" :disabled="readOnly" />
      </el-form-item>

      <el-form-item label="3.2 部署位置规划" label-width="140px">
        <div class="table-editor">
          <el-table :data="tables.deploy" size="small" border>
            <el-table-column label="设备名称" min-width="130"><template #default="{ row }"><el-input v-model="row.deviceName" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="设备类型" min-width="110"><template #default="{ row }"><el-input v-model="row.deviceType" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="设备序列号" min-width="130"><template #default="{ row }"><el-input v-model="row.sn" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="安装位置（机房/机柜/U数）" min-width="180"><template #default="{ row }"><el-input v-model="row.location" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="备注" min-width="110"><template #default="{ row }"><el-input v-model="row.remark" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="操作" width="70" fixed="right">
              <template #default="{ $index }"><el-button link type="danger" :disabled="readOnly" @click="tables.deploy.splice($index, 1)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <el-button class="mt-4px" size="small" :disabled="readOnly" @click="tables.deploy.push({ deviceName: '', deviceType: '', sn: '', location: '', remark: '' })">添加行</el-button>
          <el-button class="mt-4px" size="small" type="primary" plain :disabled="readOnly || !devices.length" @click="tables.deploy = deviceDeployRows()">从 1.1.1 序列号详情同步</el-button>
        </div>
      </el-form-item>

      <el-form-item label="3.3 接口互联规划" label-width="140px">
        <div class="table-editor">
          <el-table :data="tables.interface" size="small" border>
            <el-table-column label="本端设备" min-width="120"><template #default="{ row }"><el-input v-model="row.localDevice" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="本端接口" min-width="110"><template #default="{ row }"><el-input v-model="row.localPort" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="对端接口" min-width="110"><template #default="{ row }"><el-input v-model="row.peerPort" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="接口类型" min-width="100"><template #default="{ row }"><el-input v-model="row.portType" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="线缆/光模块型号" min-width="140"><template #default="{ row }"><el-input v-model="row.cableModel" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="备注" min-width="100"><template #default="{ row }"><el-input v-model="row.remark" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="操作" width="70" fixed="right">
              <template #default="{ $index }"><el-button link type="danger" :disabled="readOnly" @click="tables.interface.splice($index, 1)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <el-button class="mt-4px" size="small" :disabled="readOnly" @click="tables.interface.push({ localDevice: '', localPort: '', peerPort: '', portType: '', cableModel: '', remark: '' })">添加行</el-button>
        </div>
      </el-form-item>

      <el-form-item label="3.4 IP与vlan规划" label-width="140px">
        <div class="table-editor">
          <el-table :data="tables.ip" size="small" border>
            <el-table-column label="设备名称" min-width="120"><template #default="{ row }"><el-input v-model="row.deviceName" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="接口" min-width="100"><template #default="{ row }"><el-input v-model="row.port" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="IP地址" min-width="130"><template #default="{ row }"><el-input v-model="row.ip" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="掩码" min-width="110"><template #default="{ row }"><el-input v-model="row.mask" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="VRRP地址" min-width="120"><template #default="{ row }"><el-input v-model="row.vrrp" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="网关" min-width="110"><template #default="{ row }"><el-input v-model="row.gateway" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="备注" min-width="100"><template #default="{ row }"><el-input v-model="row.remark" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="操作" width="70" fixed="right">
              <template #default="{ $index }"><el-button link type="danger" :disabled="readOnly" @click="tables.ip.splice($index, 1)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <el-button class="mt-4px" size="small" :disabled="readOnly" @click="tables.ip.push({ deviceName: '', port: '', ip: '', mask: '', vrrp: '', gateway: '', remark: '' })">添加行</el-button>
        </div>
      </el-form-item>

      <el-form-item label="3.5 软件版本" label-width="140px">
        <div class="table-editor">
          <el-table :data="tables.software" size="small" border>
            <el-table-column label="序列号" min-width="140"><template #default="{ row }"><el-input v-model="row.sn" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="设备型号" min-width="110"><template #default="{ row }"><el-input v-model="row.model" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="软件版本" min-width="120"><template #default="{ row }"><el-input v-model="row.version" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="备注" min-width="110"><template #default="{ row }"><el-input v-model="row.remark" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="操作" width="70" fixed="right">
              <template #default="{ $index }"><el-button link type="danger" :disabled="readOnly" @click="tables.software.splice($index, 1)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <el-button class="mt-4px" size="small" :disabled="readOnly" @click="tables.software.push({ sn: '', model: '', version: '', remark: '' })">添加行</el-button>
          <el-button class="mt-4px" size="small" type="primary" plain :disabled="readOnly || !devices.length" @click="tables.software = deviceSoftwareRows()">从 1.1.1 序列号详情同步</el-button>
        </div>
      </el-form-item>
    </section>

    <!-- 第4章 配置脚本 -->
    <section class="chapter">
      <div class="chapter-title">4. 配置脚本</div>
      <el-form-item v-for="item in scriptItems" :key="item.key" :label="item.label" label-width="140px">
        <el-input
          v-model="scripts[item.key]"
          type="textarea"
          :rows="5"
          :disabled="readOnly"
          class="script-input"
          :placeholder="item.placeholder"
        />
      </el-form-item>
    </section>

    <!-- 第5章 实施步骤 -->
    <section class="chapter">
      <div class="chapter-title">5. 实施步骤</div>
      <el-form-item label="5.1 硬件实施" label-width="140px">
        <el-radio-group v-model="meta.hardwareInvolved" :disabled="readOnly">
          <el-radio value="yes">涉及硬件实施</el-radio>
          <el-radio value="no">不涉及</el-radio>
        </el-radio-group>
      </el-form-item>
      <div v-if="meta.hardwareInvolved === 'yes'" class="doc-content">
        <h4>施工环境检查规范（按照实际情况填写）</h4>
        <p>机房环境、承重、供电、空调、接地等施工条件逐项检查确认。</p>
        <h4>产品安装规范</h4>
        <p>设备上架、光模块/线缆插拔、标签标识按产品安装手册执行。</p>
        <h4>设备供电规范</h4>
        <p>双电源分路供电，电源线缆规格与空开容量满足设备功耗要求。</p>
      </div>
      <el-form-item label="5.2 软件调试" label-width="140px">
        <el-input v-model="meta.softwareDebug" type="textarea" :rows="3" :disabled="readOnly" placeholder="软件调试步骤说明" />
      </el-form-item>
      <el-form-item label="5.3 业务配置" label-width="140px">
        <div class="table-editor">
          <el-table :data="tables.business" size="small" border>
            <el-table-column label="设备名称" min-width="110"><template #default="{ row }"><el-input v-model="row.deviceName" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="序列号" min-width="120"><template #default="{ row }"><el-input v-model="row.sn" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="承载业务名称" min-width="120"><template #default="{ row }"><el-input v-model="row.businessName" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="业务网段" min-width="120"><template #default="{ row }"><el-input v-model="row.network" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="业务重要等级" min-width="105"><template #default="{ row }"><el-input v-model="row.level" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="出入接口" min-width="100"><template #default="{ row }"><el-input v-model="row.ports" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="客户侧负责人" min-width="110"><template #default="{ row }"><el-input v-model="row.owner" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="备注" min-width="95"><template #default="{ row }"><el-input v-model="row.remark" :disabled="readOnly" /></template></el-table-column>
            <el-table-column label="操作" width="70" fixed="right">
              <template #default="{ $index }"><el-button link type="danger" :disabled="readOnly" @click="tables.business.splice($index, 1)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <el-button class="mt-4px" size="small" :disabled="readOnly" @click="tables.business.push(emptyBusinessRow())">添加行</el-button>
          <el-button class="mt-4px" size="small" type="primary" plain :disabled="readOnly || !reqBusinessRows.length" @click="tables.business = reqBusinessRows.map((r) => ({ ...r }))">从 2.3 需求分析引用</el-button>
        </div>
      </el-form-item>
    </section>

    <!-- 第6章 可选方案模块 -->
    <section class="chapter">
      <div class="chapter-title">6. 可选方案模块</div>
      <el-checkbox-group v-model="modules" :disabled="readOnly">
        <el-checkbox value="quality">6.1 质量保障方案</el-checkbox>
        <el-checkbox value="risk">6.2 风险管控与应急预案</el-checkbox>
        <el-checkbox value="oAndM">6.3 运维交付与售后服务</el-checkbox>
        <el-checkbox value="archive">6.4 项目问题闭环与文档归档</el-checkbox>
      </el-checkbox-group>
      <template v-if="modules.includes('quality')">
        <div class="doc-content">
          <h4>质量保障方案模板</h4>
          <p>前期物资质量管控 / 现场施工过程质量管控 / 调试与测试质量管控 / 验收交付质量管控 / 售后长效质量保障。</p>
        </div>
        <el-form-item label="6.1 质量保障" label-width="140px">
          <Editor v-model="form.quality" height="160px" :readonly="readOnly" />
        </el-form-item>
        <el-button size="small" :disabled="readOnly" @click="appendTemplate('quality', TEMPLATE_QUALITY)">添加到实施方案</el-button>
      </template>
      <template v-if="modules.includes('risk')">
        <div class="doc-content">
          <h4>风险管控与应急预案模板</h4>
          <p>风险管控措施；应急处置预案：物资供货 / 现场环境 / 设备故障 / 施工安全 / 系统运行应急。</p>
        </div>
        <el-form-item label="6.2 风险应急" label-width="140px">
          <Editor v-model="form.risk" height="160px" :readonly="readOnly" />
        </el-form-item>
        <el-button size="small" :disabled="readOnly" @click="appendTemplate('risk', TEMPLATE_RISK)">添加到实施方案</el-button>
      </template>
      <template v-if="modules.includes('oAndM')">
        <div class="doc-content">
          <h4>运维交付与售后服务模板</h4>
          <p>资料移交 / 专属对接人 / 分级响应 / 巡检与增值服务；项目问题台账闭环机制与文档标准化归档。</p>
        </div>
        <el-form-item label="6.3 运维售后" label-width="140px">
          <Editor v-model="form.oAndM" height="160px" :readonly="readOnly" />
        </el-form-item>
        <el-button size="small" :disabled="readOnly" @click="appendTemplate('oAndM', TEMPLATE_OANDM)">添加到实施方案</el-button>
      </template>
      <template v-if="modules.includes('archive')">
        <div class="doc-content">
          <h4>项目问题闭环与文档归档模板</h4>
          <p>问题台账闭环机制：问题登记—责任分派—处理跟踪—验证关闭；文档标准化归档：方案、配置、报告按目录归档。</p>
        </div>
        <el-form-item label="6.4 问题闭环归档" label-width="140px">
          <Editor v-model="archiveModule" height="160px" :readonly="readOnly" />
        </el-form-item>
        <el-button size="small" :disabled="readOnly" @click="appendTemplate('archive', TEMPLATE_ARCHIVE)">添加到实施方案</el-button>
      </template>
    </section>

    <!-- 第7章 项目培训及资料移交 -->
    <section class="chapter">
      <div class="chapter-title">7. 项目培训及资料移交</div>
      <el-form-item label="7.1 培训目的" label-width="140px">
        <el-input v-model="meta.trainingPurpose" type="textarea" :rows="3" :disabled="readOnly" placeholder="按照实际情况填写" />
      </el-form-item>
      <el-form-item label="7.2 培训内容" label-width="140px">
        <el-input v-model="meta.trainingContent" type="textarea" :rows="4" :disabled="readOnly" />
        <div class="form-tip">模板要点：项目整体架构讲解 / 设备基础操作 / 日常运维与巡检 / 业务配置实操 / 常见故障处理 / 安全规范与账号管理</div>
      </el-form-item>
    </section>

    <!-- 第8章 售后服务（只读模板） -->
    <section class="chapter">
      <div class="chapter-title">8. 售后服务</div>
      <div class="doc-content standalone">
        <h4>8.1 400热线</h4>
        <p>迪普科技设有7×24小时客户服务热线，受理故障申报与技术咨询。客户服务热线：400-610-0598。</p>
        <h4>8.2 现场服务</h4>
        <p>重大故障按服务等级约定安排工程师现场支持。</p>
        <h4>8.3 软件更新</h4>
        <p>在维保期内提供软件版本升级与补丁服务，License 授权变更按合同约定执行。</p>
        <h4>8.4 网站论坛</h4>
        <p>官网：http://www.dptech.com，提供产品资料与知识库查询。</p>
      </div>
    </section>

    <!-- 第9章 操作 -->
    <section class="chapter">
      <div class="chapter-title">9. 操作</div>
      <el-button type="primary" :disabled="readOnly" :loading="saving" @click="emit('save')">保存草稿</el-button>
      <el-button :disabled="readOnly" @click="generateFromSources">生成方案（引用前序数据）</el-button>
      <el-button disabled title="实施方案文件生成与下载未接入；方案审批通过后自动归档至 6.4 交付件">下载方案</el-button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { SolutionVO } from '@/api/pms/engineering/solution'
import * as RequirementAnalysisApi from '@/api/pms/engineering/requirement-analysis'
import * as ProjectsApi from '@/api/pms/project/projects'
import * as StagePlanApi from '@/api/pms/engineering/stage-plan'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import { getDeliveryScopePage } from '@/api/pms/commerce'
import { DICT_TYPE, getDictLabel, getIntDictOptions } from '@/utils/dict'

defineOptions({ name: 'SolutionChapterForm' })

const props = defineProps<{ readOnly: boolean; saving?: boolean }>()
const emit = defineEmits<{ save: [] }>()
const form = defineModel<SolutionVO>({ required: true })

// ---------- JSON 信封映射说明 ----------
// 九章正文映射到既有 sol_solution 文本列（后端未拆列前的序列化边界，后续拆列仅需调整此处）：
//   background/target = 1.1/1.2 正文；team/inventory/plan/interfacePlan/ipPlan = 对应章节行 JSON；
//   topology = {asIsUrl,toBeChanged,toBeUrl,deploy[],software[]}（2.1/3.1/3.2/3.5）；
//   script = {network,businessModule,ha,full}（4.x）；quality/risk/oAndM = 6.x 正文；
//   remark = {hasCustomerPlan,customerPlanUrl,hardwareInvolved,softwareDebug,trainingPurpose,trainingContent,archive(6.4),business[]}。
type Row = Record<string, string>
const parseRows = (raw: unknown): Row[] => (Array.isArray(raw) ? (raw as Row[]) : [])
const parseObject = (raw: string | undefined | null): Record<string, unknown> => {
  try {
    const v = raw ? JSON.parse(raw) : null
    return v && typeof v === 'object' && !Array.isArray(v) ? v : {}
  } catch {
    return {}
  }
}

// ---------- 引用数据（前序阶段真实来源） ----------
const reqValues = ref<Record<string, unknown>>({})
const members = ref<ProjectsApi.ProjectMemberAssignmentVO[]>([])
const scopeRows = ref<{ productCode: string; deviceTypeCode: string; allocatedQuantity: number }[]>([])
const stagePlanItems = ref<StagePlanApi.StagePlanItemVO[]>([])
const devices = ref<DeviceArchiveApi.DeviceArchiveVO[]>([])

const stripHtml = (v: unknown) => String(v ?? '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
const reqBackground = computed(() => String(reqValues.value['PROJECT_BACKGROUND'] ?? ''))
const reqObjective = computed(() => String(reqValues.value['PROJECT_OBJECTIVE'] ?? ''))
const reqTopology = computed(() => String(reqValues.value['NETWORK_TOPOLOGY'] ?? ''))
const reqTransmission = computed(() => {
  const v = reqValues.value['TRANSMISSION_CURRENT_OPTIONS']
  return Array.isArray(v) ? v.map(String) : []
})
const reqTraffic = computed(() => ({
  new: String(reqValues.value['TRAFFIC_NEW_CONNECTIONS'] ?? ''),
  concurrent: String(reqValues.value['TRAFFIC_CONCURRENCY'] ?? ''),
  throughput: String(reqValues.value['TRAFFIC_THROUGHPUT'] ?? '')
}))
const reqBusinessRows = computed<Row[]>(() => {
  const v = reqValues.value['BUSINESS_DEVICE_DETAILS']
  if (!Array.isArray(v)) return []
  return v.map((item) => {
    const r = (item ?? {}) as Record<string, unknown>
    return {
      deviceName: String(r['deviceName'] ?? r['设备名称'] ?? ''),
      sn: String(r['sn'] ?? r['序列号'] ?? ''),
      businessName: String(r['businessName'] ?? r['承载业务名称'] ?? ''),
      network: String(r['network'] ?? r['业务网段'] ?? ''),
      level: String(r['level'] ?? r['业务重要等级'] ?? ''),
      ports: String(r['ports'] ?? r['出入接口'] ?? ''),
      owner: String(r['owner'] ?? r['客户侧业务负责人'] ?? ''),
      remark: String(r['remark'] ?? r['备注'] ?? '')
    }
  })
})

const memberRoleLabel = (role: string) => {
  const label = getDictLabel(DICT_TYPE.PMS_PROJECT_MEMBER_ROLE, role)
  return label || role || '—'
}
const memberRows = (): Row[] =>
  members.value.map((m) => ({ role: memberRoleLabel(m.memberRole), name: m.memberName || '', contact: m.employeeNo || '', remark: '' }))
const scopeProductRows = (): Row[] =>
  scopeRows.value.map((s) => ({
    productCode: s.productCode || '',
    model: s.deviceTypeCode || '',
    desc: '',
    projectQty: String(s.allocatedQuantity ?? ''),
    shippedQty: '',
    unshippedQty: '',
    sn: ''
  }))
const stagePlanRows = (): Row[] =>
  stagePlanItems.value.map((i) => ({
    stage: i.phaseName || i.phaseCode || '',
    start: i.planStart || i.suggestedStart || '',
    end: i.planEnd || i.suggestedEnd || '',
    suggest: '',
    remark: i.remark || ''
  }))
const deviceDeployRows = (): Row[] =>
  devices.value.map((d) => ({
    deviceName: d.name || '',
    deviceType: d.productModel || '',
    sn: d.sn || '',
    location: d.locationSnapshot || '',
    remark: ''
  }))
const deviceSoftwareRows = (): Row[] =>
  devices.value.map((d) => ({ sn: d.sn || '', model: d.productModel || '', version: '', remark: '' }))
const emptyInventoryRow = (): Row => ({ productCode: '', model: '', desc: '', projectQty: '', shippedQty: '', unshippedQty: '', sn: '' })
const emptyBusinessRow = (): Row => ({ deviceName: '', sn: '', businessName: '', network: '', level: '', ports: '', owner: '', remark: '' })

// ---------- 章节本地态（写回 form 字段） ----------
const tables = reactive({
  team: [] as Row[],
  inventory: [] as Row[],
  plan: [] as Row[],
  deploy: [] as Row[],
  interface: [] as Row[],
  ip: [] as Row[],
  software: [] as Row[],
  business: [] as Row[]
})
const scripts = reactive({ network: '', businessModule: '', ha: '', full: '' })
const topology = reactive({ asIsUrl: '', toBeChanged: '', toBeUrl: '' })
const meta = reactive({
  hasCustomerPlan: '',
  customerPlanUrl: '',
  hardwareInvolved: '',
  softwareDebug: '',
  trainingPurpose: '',
  trainingContent: ''
})
const modules = ref<string[]>([])
// Demo 6.4 问题闭环与文档归档：无独立列，随 remark 信封序列化
const archiveModule = ref('')

const scriptItems = [
  { key: 'network', label: '4.1 网络配置脚本', placeholder: '网络配置脚本' },
  { key: 'businessModule', label: '4.2 业务模块功能配置脚本', placeholder: '业务模块功能配置脚本' },
  { key: 'ha', label: '4.3 高可用配置脚本', placeholder: '高可用配置脚本' },
  { key: 'full', label: '4.4 全量配置脚本', placeholder: '全量配置脚本' }
] as const

const syncFromForm = () => {
  tables.team = parseRows(safeParse(form.value.team))
  tables.inventory = parseRows(safeParse(form.value.inventory))
  tables.plan = parseRows(safeParse(form.value.plan))
  tables.interface = parseRows(safeParse(form.value.interfacePlan))
  tables.ip = parseRows(safeParse(form.value.ipPlan))
  const topo = parseObject(form.value.topology)
  topology.asIsUrl = String(topo['asIsUrl'] ?? '')
  topology.toBeChanged = String(topo['toBeChanged'] ?? '')
  topology.toBeUrl = String(topo['toBeUrl'] ?? '')
  tables.deploy = parseRows(topo['deploy'])
  tables.software = parseRows(topo['software'])
  const scriptObj = parseObject(form.value.script)
  scripts.network = String(scriptObj['network'] ?? '')
  scripts.businessModule = String(scriptObj['businessModule'] ?? '')
  scripts.ha = String(scriptObj['ha'] ?? '')
  scripts.full = String(scriptObj['full'] ?? '')
  const metaObj = parseObject(form.value.remark)
  meta.hasCustomerPlan = String(metaObj['hasCustomerPlan'] ?? '')
  meta.customerPlanUrl = String(metaObj['customerPlanUrl'] ?? '')
  meta.hardwareInvolved = String(metaObj['hardwareInvolved'] ?? '')
  meta.softwareDebug = String(metaObj['softwareDebug'] ?? '')
  meta.trainingPurpose = String(metaObj['trainingPurpose'] ?? '')
  meta.trainingContent = String(metaObj['trainingContent'] ?? '')
  tables.business = parseRows(metaObj['business'])
  archiveModule.value = String(metaObj['archive'] ?? '')
  modules.value = [
    ...(['quality', 'risk', 'oAndM'] as const).filter((k) => String(form.value[k] ?? '').trim()),
    ...(String(metaObj['archive'] ?? '').trim() ? ['archive' as const] : [])
  ]
}
// 兼容 JSON 数组或 {rows:[]} 两种历史包装
const safeParse = (raw: string | undefined | null): unknown => {
  try {
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

watch(tables, () => {
  form.value.team = JSON.stringify(tables.team)
  form.value.inventory = JSON.stringify(tables.inventory)
  form.value.plan = JSON.stringify(tables.plan)
  form.value.interfacePlan = JSON.stringify(tables.interface)
  form.value.ipPlan = JSON.stringify(tables.ip)
}, { deep: true })
watch(
  [topology, () => tables.deploy, () => tables.software],
  () => {
    form.value.topology = JSON.stringify({
      asIsUrl: topology.asIsUrl,
      toBeChanged: topology.toBeChanged,
      toBeUrl: topology.toBeUrl,
      deploy: tables.deploy,
      software: tables.software
    })
  },
  { deep: true }
)
watch(scripts, () => { form.value.script = JSON.stringify(scripts) }, { deep: true })
watch(
  [meta, archiveModule, () => tables.business],
  () => {
    form.value.remark = JSON.stringify({ ...meta, archive: archiveModule.value, business: tables.business })
  },
  { deep: true }
)

watch(
  () => [form.value.id, form.value.projectId],
  () => syncFromForm(),
  { immediate: true }
)

onMounted(async () => {
  const projectId = form.value.projectId
  if (!projectId) return
  const [overview, memberList, scopePage, planPage, devicePage] = await Promise.all([
    RequirementAnalysisApi.getCurrent(projectId).catch(() => null),
    ProjectsApi.getProjectMembers(projectId).catch(() => []),
    getDeliveryScopePage({ projectId, pageNo: 1, pageSize: 200, includeHistory: false }).catch(() => ({ list: [] })),
    StagePlanApi.getStagePlanBatchPage({ projectId, pageNo: 1, pageSize: 10 }).catch(() => ({ list: [] })),
    DeviceArchiveApi.getDeviceArchivePage({ projectId, pageNo: 1, pageSize: 200 }).catch(() => ({ list: [] }))
  ])
  reqValues.value = ((overview as any)?.currentEffective ?? (overview as any)?.draft)?.values ?? {}
  members.value = (memberList as ProjectsApi.ProjectMemberAssignmentVO[]) || []
  scopeRows.value = ((scopePage as any).list || []).flatMap((scope: any) =>
    (scope.details || []).map((d: any) => ({
      productCode: d.productCode || '',
      deviceTypeCode: d.deviceTypeCode || '',
      allocatedQuantity: d.allocatedQuantity ?? scope.allocatedQuantity ?? 0
    }))
  )
  stagePlanItems.value = (planPage as any).list?.[0]?.items || []
  devices.value = ((devicePage as any).list || []) as DeviceArchiveApi.DeviceArchiveVO[]
})

// ---------- 生成方案（引用前序数据，只填空字段） ----------
const generateFromSources = () => {
  if (props.readOnly) return
  if (reqBackground.value && !String(form.value.background ?? '').trim()) form.value.background = reqBackground.value
  if (reqObjective.value && !String(form.value.target ?? '').trim()) form.value.target = reqObjective.value
  if (members.value.length && !tables.team.length) tables.team = memberRows()
  if (scopeRows.value.length && !tables.inventory.length) tables.inventory = scopeProductRows()
  if (stagePlanItems.value.length && !tables.plan.length) tables.plan = stagePlanRows()
  if (devices.value.length && !tables.deploy.length) tables.deploy = deviceDeployRows()
  if (devices.value.length && !tables.software.length) tables.software = deviceSoftwareRows()
  if (reqBusinessRows.value.length && !tables.business.length) tables.business = reqBusinessRows.value.map((r) => ({ ...r }))
}

// ---------- 可选模块模板 ----------
const TEMPLATE_QUALITY = '<p>1. 前期物资质量管控：到货开箱验收、物料一致性核对。</p><p>2. 现场施工过程质量管控：安装工艺、布线规范、标签标识。</p><p>3. 调试与测试质量管控：配置基线核对、业务验证。</p><p>4. 验收交付质量管控：按验收标准逐项交付。</p><p>5. 售后长效质量保障：定期巡检与问题闭环。</p>'
const TEMPLATE_RISK = '<p>风险管控措施：开工前风险识别与交底、施工过程双人复核。</p><p>应急处置预案：</p><p>1. 物资供货应急；2. 现场环境应急；3. 设备故障应急；4. 施工安全应急；5. 系统运行应急。</p>'
const TEMPLATE_OANDM = '<p>1. 资料移交：竣工文档、配置备份、账号清单。</p><p>2. 专属对接人：明确售后对接人与升级路径。</p><p>3. 分级响应：按故障等级约定响应与恢复时限。</p><p>4. 巡检与增值服务：定期巡检与优化建议。</p>'
const TEMPLATE_ARCHIVE = '<p>1. 问题台账闭环机制：问题登记—责任分派—处理跟踪—验证关闭，台账定期复盘。</p><p>2. 文档标准化归档：方案、配置脚本、测试与验收报告按目录规范归档并同步交付件。</p>'
const appendTemplate = (field: 'quality' | 'risk' | 'oAndM' | 'archive', template: string) => {
  if (field === 'archive') {
    const current = archiveModule.value.trim()
    archiveModule.value = current ? `${current}${template}` : template
    return
  }
  const current = String(form.value[field] ?? '').trim()
  form.value[field] = current ? `${current}${template}` : template
}
</script>

<style lang="scss" scoped>
.solution-chapter-form {
  max-height: 62vh;
  padding-right: 4px;
  overflow-y: auto;
}

.chapter {
  padding: 12px 0 4px;
  margin-bottom: 8px;
  border-bottom: 1px dashed var(--el-border-color-lighter);

  &:last-child {
    border-bottom: none;
  }
}

.chapter-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.ref-block {
  padding: 8px 10px;
  margin: 0 0 10px 140px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.ref-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 600;
}

.ref-body {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
  white-space: pre-wrap;
  word-break: break-all;

  &.standalone {
    margin: 0;
  }
}

.table-editor {
  width: 100%;
}

.script-input {
  :deep(textarea) {
    font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  }
}

.doc-content {
  padding: 10px 12px;
  margin: 0 0 10px 140px;
  font-size: 13px;
  line-height: 1.8;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;

  h4 {
    margin: 6px 0 2px;
    font-size: 13px;
  }

  p {
    margin: 2px 0;
    color: var(--el-text-color-regular);
  }

  &.standalone {
    margin-left: 0;
  }
}

.form-tip {
  width: 100%;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
