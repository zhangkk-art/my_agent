const UNITS = [
  { max: 60, unit: 'second', div: 1 },
  { max: 3600, unit: 'minute', div: 60 },
  { max: 86400, unit: 'hour', div: 3600 },
  { max: 604800, unit: 'day', div: 86400 },
  { max: 2592000, unit: 'week', div: 604800 },
  { max: Infinity, unit: 'month', div: 2592000 }
]

export function timeAgo(dateStr) {
  const now = Date.now()
  const then = new Date(dateStr).getTime()
  const diff = Math.floor((now - then) / 1000)
  if (diff < 10) return 'just now'

  for (const { max, unit, div } of UNITS) {
    if (diff < max) {
      const n = Math.floor(diff / div)
      return n + ' ' + unit + (n !== 1 ? 's' : '') + ' ago'
    }
  }
}
