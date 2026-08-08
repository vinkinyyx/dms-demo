export function pad2(n) { return String(n).padStart(2, '0') }

export function formatDateTime(value, withSeconds = true) {
  if (value === null || value === undefined || value === '') return '-'
  const d = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const date = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
  const time = withSeconds
    ? `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
    : `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
  return `${date} ${time}`
}

export function formatDate(value) {
  if (value === null || value === undefined || value === '') return '-'
  const d = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2})?(\.\d+)?(Z|[+-]\d{2}:?\d{2})?$/

export function looksLikeDateKey(key) {
  return /(^|_)(at|time|date)$|At$|Time$|Date$/.test(String(key || ''))
}

export function formatAuto(value, key = '') {
  if (value === null || value === undefined || value === '') return '-'
  if (looksLikeDateKey(key) && ISO_DATE_RE.test(String(value))) return formatDateTime(value)
  return value
}

export async function copyText(text) {
  const value = typeof text === 'string' ? text : JSON.stringify(text, null, 2)
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value)
    return
  }
  const textarea = document.createElement('textarea')
  textarea.value = value
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}
