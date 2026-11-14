import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const packageScripts = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8')).scripts

function runDev(t, script, { passwordHash, setupScript } = {}) {
  const fixture = mkdtempSync(path.join(os.tmpdir(), 'cinelode-dev-test-'))
  t.after(() => rmSync(fixture, { recursive: true, force: true }))
  mkdirSync(path.join(fixture, 'scripts'))
  mkdirSync(path.join(fixture, 'backend'))
  copyFileSync(path.join(root, 'scripts', 'dev.mjs'), path.join(fixture, 'scripts', 'dev.mjs'))
  writeFileSync(path.join(fixture, 'package.json'), JSON.stringify({
    type: 'commonjs',
    scripts: {
      dev: packageScripts.dev,
      'dev:lan': packageScripts['dev:lan'],
      'dev:services': 'node -e "console.log(\'LOCAL_SERVICES_STARTED\'); console.log(\'DEV_LOGIN=\' + process.env.CINELODE_DEV_LOGIN)"',
      'dev:lan:services': 'node -e "console.log(\'LAN_SERVICES_STARTED\'); console.log(\'DEV_LOGIN=\' + process.env.CINELODE_DEV_LOGIN)"',
      'setup:password': setupScript ?? 'node -e "require(\'node:fs\').writeFileSync(\'backend/.env\', \'CINELODE_PASSWORD_HASH=fixture-hash\')"',
    },
  }))
  const env = { ...process.env }
  delete env.CINELODE_PASSWORD_HASH
  if (passwordHash !== undefined) env.CINELODE_PASSWORD_HASH = passwordHash
  const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm'
  const result = spawnSync(npm, ['run', script], {
    cwd: fixture,
    env,
    encoding: 'utf8',
    timeout: 30000,
    shell: process.platform === 'win32',
  })
  assert.ifError(result.error)
  return result
}

test('local and LAN development start their respective services', async (t) => {
  for (const [script, expected] of [['dev', 'LOCAL_SERVICES_STARTED'], ['dev:lan', 'LAN_SERVICES_STARTED']]) {
    await t.test(script, (t) => {
      const result = runDev(t, script, { passwordHash: 'fixture-hash' })
      assert.equal(result.status, 0, result.stderr)
      assert.match(result.stdout, new RegExp(expected))
      assert.match(result.stdout, new RegExp(script === 'dev' ? 'DEV_LOGIN=true' : 'DEV_LOGIN=false'))
      assert.doesNotMatch(result.stdout, /No login password is configured/)
    })
  }
})

test('development runs password setup before starting services when needed', (t) => {
  const result = runDev(t, 'dev')
  assert.equal(result.status, 0, result.stderr)
  assert.match(result.stdout, /No login password is configured/)
  assert.match(result.stdout, /LOCAL_SERVICES_STARTED/)
})

test('failed password setup prevents services from starting', (t) => {
  const result = runDev(t, 'dev', { setupScript: 'node -e "process.exit(7)"' })
  assert.equal(result.status, 7, result.stderr)
  assert.doesNotMatch(result.stdout, /LOCAL_SERVICES_STARTED/)
})

test('LAN persistent login is enabled only with the HTTPS launcher', () => {
  assert.match(packageScripts['dev:lan:services'], /CINELODE_REMEMBER_ME=true/)
  assert.match(packageScripts['dev:lan:services'], /CINELODE_DEV_TLS_ENABLED=true/)
  assert.match(packageScripts['dev:lan:services'], /CINELODE_DEV_LOGIN=false/)
  assert.match(packageScripts['dev:lan:http'], /CINELODE_REMEMBER_ME=false/)
  assert.match(packageScripts['dev:lan:http'], /CINELODE_DEV_LOGIN=false/)
})
