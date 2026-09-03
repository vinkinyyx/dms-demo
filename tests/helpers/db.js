/**
 * 数据库回读工具（黑盒测试的「数据库 SQL 回读」维度）。
 * 通过环境变量配置被测库；未配置时 query() 返回 null，调用方据此 skip 回读断言。
 *   PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD（默认指向测试环境映射端口）
 */
let poolPromise = null

function configured() {
  return !!process.env.PGHOST
}

async function getPool() {
  if (!configured()) return null
  if (poolPromise) return poolPromise
  const { Pool } = require('pg')
  poolPromise = Promise.resolve(new Pool({
    host: process.env.PGHOST,
    port: Number(process.env.PGPORT || 5432),
    database: process.env.PGDATABASE || 'dms',
    user: process.env.PGUSER || 'dms',
    password: process.env.PGPASSWORD || '',
    ssl: false,
    connectionTimeoutMillis: 5000,
    max: 2
  }))
  return poolPromise
}

/** 执行只读查询，返回行数组；未配置数据库时返回 null（测试应 skip 而非 fail）。 */
async function query(text, params = []) {
  const pool = await getPool()
  if (!pool) return null
  const res = await pool.query(text, params)
  return res.rows
}

/** 取单行（或 null）。 */
async function one(text, params = []) {
  const rows = await query(text, params)
  return rows && rows.length ? rows[0] : null
}

async function close() {
  if (poolPromise) {
    const pool = await poolPromise
    await pool.end().catch(() => {})
    poolPromise = null
  }
}

module.exports = { configured, query, one, close }
