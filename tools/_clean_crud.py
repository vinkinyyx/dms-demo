from pathlib import Path
p=Path('frontend-vue/src/components/CrudView.vue')
lines=p.read_text(encoding='utf-8').splitlines()
# Remove duplicated fragment between first el-table close and the actual popover block.
first_table_close = next(i for i,l in enumerate(lines) if l.strip()=='</el-table>')
popover = next(i for i,l in enumerate(lines) if '<el-popover' in l)
new_lines = lines[:first_table_close+1] + [''] + lines[popover-1:]
p.write_text('\n'.join(new_lines)+'\n', encoding='utf-8', newline='\n')
