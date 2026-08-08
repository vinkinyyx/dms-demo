from pathlib import Path
s=Path('frontend-vue/src/components/CrudView.vue').read_text(encoding='utf-8')
start=s.index('function rowActionVisible')
end=s.index('\nfunction onToolbarButtonClick', start)
print(repr(s[start:end]))
