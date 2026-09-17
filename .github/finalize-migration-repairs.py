"""Correct two findings from the first repair verification without weakening checks."""
from pathlib import Path
import subprocess

root = Path('yudao-ui/yudao-ui-admin-vue3')
p = root / 'src/views/lowcode/microflow-designer/index.vue'
actual = subprocess.check_output(['git', 'hash-object', str(p)], text=True).strip()
if actual != '2e557868be9022e2bdabb9be2a8948a0352515bc':
    raise SystemExit('Unexpected intermediate microflow source; refusing overwrite')
# Rebuild this one mechanical edit from the reviewed source, not a cross-tag regex.
s = subprocess.check_output(['git', 'show', f'e1a4f923f6c66373caa7ee47c37429f26223059b:{p}'], text=True)
old = '          @dragover.prevent\n        />'
if s.count(old) != 1:
    raise SystemExit('Microflow canvas boundary changed')
p.write_text(s.replace(old, '          @dragover.prevent\n        ></div>'))

p = root / 'src/views/pms/delivery-business/site-survey/siteSurveyProjectContext.runtime.spec.ts'
actual = subprocess.check_output(['git', 'hash-object', str(p)], text=True).strip()
if actual != 'e2262c02fdfd84e2bcc24dc1eec1bfb955f33f13':
    raise SystemExit('Survey runtime test changed')
s = p.read_text()
old = 'expect(state.readonly).toBe(true)'
if s.count(old) != 1:
    raise SystemExit('Expected exactly one internal binding assertion')
p.write_text(s.replace(old, 'expect(state.formReadonly).toBe(true)'))
subprocess.run(['git', 'add', '--', str(root / 'src/views/lowcode/microflow-designer/index.vue'), str(p)], check=True)
subprocess.run(['git', 'diff', '--cached', '--check'], check=True)
