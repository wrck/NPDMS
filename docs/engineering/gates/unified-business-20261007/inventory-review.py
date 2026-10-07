from pathlib import Path
import re,json,hashlib
root=Path(__file__).resolve().parents[4]
rows=[]
def split_args(s):
 out=[]; start=0; depth=0; quote=None; esc=False
 for i,c in enumerate(s):
  if quote:
   if esc: esc=False
   elif c=='\\': esc=True
   elif c==quote: quote=None
  elif c in '\"\'': quote=c
  elif c in '([{': depth+=1
  elif c in ')]}': depth-=1
  elif c==',' and depth==0: out.append(s[start:i].strip());start=i+1
 out.append(s[start:].strip());return out
for p in root.glob('pms-module-*/**/src/main/java/**/*BusinessModelContributor.java'):
 s=p.read_text()
 for m in re.finditer(r'new BusinessModelDescriptor\s*\(',s):
  i=m.end();depth=1;quote=None;esc=False;j=i
  while depth and j<len(s):
   c=s[j]
   if quote:
    if esc:esc=False
    elif c=='\\':esc=True
    elif c==quote:quote=None
   elif c in '\"\'':quote=c
   elif c=='(':depth+=1
   elif c==')':depth-=1
   j+=1
  a=split_args(s[i:j-1])
  rows.append(dict(owner=a[0].strip('"'),entity=a[1].strip('"'),stable_code=a[2].strip('"'),operation_expression=a[9],scope_expression=a[12] if len(a)>12 else 'null (legacy constructor)',path=str(p.relative_to(root)),line=s[:m.start()].count('\n')+1,args=len(a)))
report={'baseline':'834a6a5b2246a9ba1d291dec4ee3157bffd35426','method':'Static constructor enumeration, not runtime acceptance and not a count of business roots','descriptor_constructor_count':len(rows),'empty_operations':sum(r['operation_expression']=='List.of()' for r in rows),'implicit_null_scope':sum(r['args']==12 for r in rows),'models':rows}
p=root/'docs/engineering/gates/unified-business-20261007/model-inventory.json';p.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print({k:v for k,v in report.items() if k!='models'})
print([(r['owner'],r['entity']) for r in rows if r['operation_expression']!='List.of()'])
