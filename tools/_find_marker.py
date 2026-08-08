from pathlib import Path
p=Path('frontend-vue/src/components/CrudView.vue')
lines=p.read_text(encoding='utf-8').splitlines()
for i,l in enumerate(lines[:360],1):
    if 'el-popover' in l or 'crud-filter-popover' in l or l.strip()=='</el-table>':
        print(i, l)
