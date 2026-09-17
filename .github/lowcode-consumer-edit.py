"""One-use, base-checked source edit for the explicitly authorized consumer fix."""
from pathlib import Path
import subprocess

F = Path('yudao-ui/yudao-ui-admin-vue3')
D = Path('pms-module-lowcode/src/main/java/cn/iocoder/yudao/module/pms/lowcode')
T = Path('pms-module-lowcode/src/test/java/cn/iocoder/yudao/module/pms/lowcode')
expected = {
 str(D / 'controller/DynamicEntityController.java'): '568ed939da02cebd625f0c228cbcc6f8130e0345',
 str(D / 'engine/DynamicEntityDataService.java'): '88970931031b2f451f4502089d7c9b53b54dc023',
 str(T / 'engine/DynamicEntityDataServiceTest.java'): '0c631f7420b43d05ba231cb00787b5ff97e60bb2',
 str(F / 'src/components/LowCodeListRenderer/index.vue'): '637e95e8cc78bff22325a0433fa392e2512cafbb',
 str(F / 'src/utils/request.ts'): 'b2e0f10526ecd06a6f9a4db41ccb194494c615e0',
 str(F / 'src/views/lowcode/form-designer/index.vue'): 'bbbdfab7f5c6677eabaf8ca24c9129049d3a591c',
 str(F / 'src/views/lowcode/render/index.vue'): 'd33643c2f927dd5806ac2f5b7cb6aac4b901cbd4'
}
for path, sha in expected.items():
    actual = subprocess.check_output(['git', 'hash-object', path], text=True).strip()
    if actual != sha:
        raise SystemExit(f'Source has changed, refusing to overwrite: {path}')

p = F / 'src/utils/request.ts'
s = p.read_text().replace("import router from '@/router'", "import router from '@/router'\nimport { lowcodeSessionHeaders } from './lowcodeSession'")
s = s.replace("    // 写操作（POST/PUT/DELETE/PATCH）注入", "    if (config.url?.startsWith('/api/lowcode/')) {\n      Object.assign(config.headers, lowcodeSessionHeaders())\n    }\n    // 写操作（POST/PUT/DELETE/PATCH）注入", 1)
p.write_text(s)

p = F / 'src/views/lowcode/render/index.vue'
s = p.read_text().replace('onMounted, ref, watch', 'onBeforeUnmount, ref, watch').replace("import axios from 'axios'\n", '')
s = s.replace("import { TOKEN_KEY } from '@/utils/request'", "import { get, post, put } from '@/utils/request'")
s = s.replace("const formMode = computed(() => (route.query.mode as string) || 'view')", "const formMode = computed(() => route.query.mode === 'create' || route.query.mode === 'edit' ? route.query.mode : 'view')\nconst recordId = computed(() => typeof route.query.id === 'string' ? route.query.id : '')\nconst validating = ref(false)\nconst saving = ref(false)\nlet loadSequence = 0\nlet validationSequence = 0")
s = s.replace("  const code = pageCode.value\n", "  if (typeof config.value?.entityCode === 'string' && config.value.entityCode) return config.value.entityCode\n  const code = pageCode.value\n", 1)
a = s.index('async function load() {'); b = s.index('\nfunction goBack()', a)
s = s[:a] + '''async function load() {
  const sequence = ++loadSequence
  const type = pageType.value
  const code = pageCode.value
  const mode = formMode.value
  const id = recordId.value
  state.value = 'loading'
  config.value = null
  pageName.value = ''
  formDataModel.value = {}
  rendererRef.value = null
  if (!VALID_PAGE_TYPES.has(type) || !code || (type === 'form' && mode === 'edit' && !id)) {
    state.value = 'not-found'
    return
  }
  try {
    const allowed = await checkLowCodePermission(type, code)
    if (sequence !== loadSequence) return
    if (!allowed) {
      state.value = 'forbidden'
      return
    }
    const result = await fetchConfig(type, code)
    if (sequence !== loadSequence) return
    if (!result) {
      state.value = 'not-found'
      return
    }
    config.value = result.config
    pageName.value = result.name
    if (result.name) document.title = `${result.name} - 网络设备工程项目管理系统`
    if (type === 'form' && mode !== 'create' && id) {
      const data = await get<Record<string, unknown>>(
        `/api/lowcode/data/${encodeURIComponent(entityCode.value)}/${encodeURIComponent(id)}`
      )
      if (sequence !== loadSequence) return
      formDataModel.value = data
    }
    state.value = 'done'
  } catch {
    if (sequence === loadSequence) state.value = 'error'
  }
}

watch([pageType, pageCode, formMode, recordId], load, { immediate: true })
onBeforeUnmount(() => { loadSequence++ })
''' + s[b:]
a = s.index('async function handleFormSubmit('); b = s.index('\n</script>', a)
s = s[:a] + '''async function handleFormSubmit(submittedData: Record<string, unknown>) {
  if (state.value !== 'done' || pageType.value !== 'form' || isFormReadOnly.value || saving.value) return
  if (validationSequence !== loadSequence) return
  const sequence = loadSequence
  const mode = formMode.value
  const id = recordId.value
  if (mode === 'edit' && !id) return
  saving.value = true
  try {
    formDataModel.value = { ...submittedData }
    const formData = Object.fromEntries(Object.entries(submittedData).map(([key, value]) =>
      [key, value === '' || value === undefined ? null : value]
    ))
    const baseUrl = `/api/lowcode/data/${encodeURIComponent(entityCode.value)}`
    if (mode === 'edit') await put(`${baseUrl}/${encodeURIComponent(id)}`, formData)
    else await post(baseUrl, formData)
    if (sequence === loadSequence) {
      ElMessage.success('保存成功')
      router.back()
    }
  } catch {
    // The shared transport reports business errors as failures, even for HTTP 200.
  } finally {
    saving.value = false
  }
}

/** Validation remains mandatory for both renderer versions. */
async function requestFormSubmit() {
  if (state.value !== 'done' || pageType.value !== 'form' || isFormReadOnly.value || validating.value || saving.value) return
  if (!rendererRef.value?.submit) {
    ElMessage.warning('表单尚未加载完成，请稍后再试')
    return
  }
  validationSequence = loadSequence
  validating.value = true
  try {
    await rendererRef.value.submit()
  } finally {
    validating.value = false
  }
}
''' + s[b:]
s = s.replace('<el-button type="primary" @click="requestFormSubmit">', '<el-button type="primary" :loading="validating || saving" @click="requestFormSubmit">')
p.write_text(s)

p = F / 'src/views/lowcode/form-designer/index.vue'
s = p.read_text(); a = s.index('/** 保存草稿（创建或更新） */'); b = s.index('/** 归档 */', a)
s = s[:a] + '''/** Save/publish share one validation and persistence path; lock before validation. */
async function persistForm(publish: boolean): Promise<void> {
  if (loading.value || !metaFormRef.value) return
  if (publish && !metaForm.id) {
    ElMessage.warning('请先保存草稿')
    return
  }
  loading.value = true
  try {
    const valid = await metaFormRef.value.validate().catch(() => false)
    if (!valid) return
    if (formConfig.fields.length === 0) {
      ElMessage.warning('请至少添加一个字段')
      return
    }
    syncFormConfigToStr()
    const payload = { ...metaForm }
    const isNew = !payload.id
    const saved = payload.id ? await updateForm(payload.id, payload) : await createForm(payload)
    // Refresh persisted identity/version without overwriting edits made during the request.
    metaForm.id = saved.id
    metaForm.version = saved.version
    metaForm.status = saved.status
    metaForm.createTime = saved.createTime
    metaForm.updateTime = saved.updateTime
    if (isNew && saved.id) {
      await router.replace({ query: { ...route.query, id: String(saved.id) } })
    }
    if (publish && saved.id) {
      await publishForm(saved.id)
      metaForm.status = 'PUBLISHED'
      ElMessage.success('发布成功')
    } else {
      ElMessage.success(isNew ? '创建成功' : '保存成功')
    }
  } catch {
    /* handled by interceptor; never report a failed save/publish as successful */
  } finally {
    loading.value = false
  }
}

async function handleSave() { await persistForm(false) }
async function handlePublish() { await persistForm(true) }

''' + s[b:]
s = s.replace(':icon="\'Promotion\'" @click="handlePublish"', ':icon="\'Promotion\'" :disabled="loading" @click="handlePublish"')
s = s.replace('    const parsed = JSON.parse(metaForm.formConfig) as VersionedFormConfig\n', '    const parsed = JSON.parse(metaForm.formConfig) as VersionedFormConfig\n    Object.assign(formConfig, parsed)\n', 1)
p.write_text(s)

p = F / 'src/components/LowCodeListRenderer/index.vue'
s = p.read_text().replace("import { TOKEN_KEY } from '@/utils/request'", "import { TOKEN_KEY, get, post } from '@/utils/request'\nimport { lowcodeSessionHeaders } from '@/utils/lowcodeSession'")
s = s.replace('`/lowcode/form/${effectiveFormCode.value}`', '`/lowcode/form/${encodeURIComponent(effectiveFormCode.value)}`')
a = s.index('    const token = localStorage.getItem(TOKEN_KEY)', s.index('async function fetchData')); b = s.index('    innerData.value = page?', a)
s = s[:a] + '''    let page
    if (api.startsWith('/api/lowcode/')) {
      // The project transport validates the business envelope and current tenant.
      page = method === 'POST' ? await post<any>(api, query) : await get<any>(api, query)
    } else {
      const token = localStorage.getItem(TOKEN_KEY) || ''
      const headers = { Authorization: `Bearer ${token}` }
      const response = method === 'POST'
        ? await axios.post(api, query, { headers })
        : await axios.get(api, { params: query, headers })
      const payload = response.data
      if (payload && typeof payload.code === 'number' && payload.code !== 0 && payload.code !== 200) {
        throw new Error(payload.msg || payload.message || '查询失败')
      }
      page = payload?.data ?? payload
    }
''' + s[b:]
s = s.replace('headers: { Authorization: `Bearer ${token}` }', "headers: url.startsWith('/api/lowcode/') ? lowcodeSessionHeaders() : { Authorization: `Bearer ${token}` }", 1)
s = s.replace('      headers: { Authorization: `Bearer ${token}` }\n', "      headers: exp.api.startsWith('/api/lowcode/') ? lowcodeSessionHeaders() : { Authorization: `Bearer ${token}` }\n", 1)
p.write_text(s)

p = D / 'controller/DynamicEntityController.java'
p.write_text(p.read_text().replace('@ss.hasPermi(', '@ss.hasPermission('))
p = D / 'engine/DynamicEntityDataService.java'
s = p.read_text().replace('import org.springframework.jdbc.core.JdbcTemplate;', 'import org.springframework.jdbc.core.JdbcTemplate;\nimport org.springframework.jdbc.support.GeneratedKeyHolder;\nimport java.sql.Statement;')
s = s.replace('''        jdbcTemplate.update(sql, filtered.values().toArray());

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);''', '''        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int index = 1;
            for (Object value : filtered.values()) statement.setObject(index++, value);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("新增记录未返回主键");
        return key.longValue();''')
a = s.index('    public void update(')
x, y = s[:a], s[a:]
y = y.replace('''        Map<String, Object> filtered = data.entrySet().stream()
                .filter(e -> validFields.contains(e.getKey()) && e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));''', '''        Map<String, Object> filtered = new LinkedHashMap<>();
        data.forEach((field, value) -> {
            if (validFields.contains(field)) filtered.put(field, value);
        });''', 1)
p.write_text(x + y)
p = T / 'engine/DynamicEntityDataServiceTest.java'
s = p.read_text().replace('import org.springframework.jdbc.core.JdbcTemplate;', 'import org.springframework.jdbc.core.JdbcTemplate;\nimport org.springframework.jdbc.core.PreparedStatementCreator;\nimport org.springframework.jdbc.support.KeyHolder;\nimport java.sql.Connection;\nimport java.sql.PreparedStatement;\nimport java.sql.Statement;')
s = s.replace('void create_success() {', 'void create_success() throws Exception {')
s = s.replace('''        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT LAST_INSERT_ID()"), eq(Long.class)))
                .thenReturn(10L);''', '''        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(statement);
        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(invocation -> {
            invocation.<PreparedStatementCreator>getArgument(0).createPreparedStatement(connection);
            invocation.<KeyHolder>getArgument(1).getKeyList().add(Map.of("id", 10L));
            return 1;
        });''')
s = s.replace('        verify(jdbcTemplate).update(contains("INSERT INTO"), any(Object[].class));', '''        verify(connection).prepareStatement("INSERT INTO `pms_lc_device` (`device_name`) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
        verify(statement).setObject(1, "Switch");''')
p.write_text(s)
subprocess.run(['git', 'add', '--', *expected], check=True)
subprocess.run(['git', 'diff', '--cached', '--check'], check=True)
