from pathlib import Path
b=Path('frontend-vue/src/views/DealerProfileList.vue').read_bytes()
print(b[:200])
print(b.decode('utf-8')[:200])
