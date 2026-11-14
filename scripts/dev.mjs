import { spawn } from 'node:child_process'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const envFile = path.join(projectRoot, 'backend', '.env')
const lanMode = process.argv[2] === 'lan'
process.env.CINELODE_DEV_LOGIN = lanMode ? 'false' : 'true'

function hasPasswordHash(contents) {
  return contents.split(/\r?\n/).some((line) => {
    const match = line.match(/^\s*CINELODE_PASSWORD_HASH\s*=\s*(.*?)\s*$/)
    return match && match[1].length > 0
  })
}

function run(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: projectRoot,
      stdio: 'inherit',
      shell: process.platform === 'win32',
    })
    child.once('error', reject)
    child.once('exit', (code, signal) => {
      if (signal) {
        resolve(1)
      } else {
        resolve(code ?? 1)
      }
    })
  })
}

async function ensurePasswordHash() {
  if (process.env.CINELODE_PASSWORD_HASH?.trim()) return

  let contents = ''
  try {
    contents = await readFile(envFile, 'utf8')
  } catch (error) {
    if (error.code !== 'ENOENT') throw error
  }
  if (hasPasswordHash(contents)) return

  console.log('No login password is configured. Set one now to start Cinelode.')
  const command = process.platform === 'win32' ? 'npm.cmd' : 'npm'
  const exitCode = await run(command, ['run', 'setup:password'])
  if (exitCode !== 0) process.exit(exitCode)

  contents = await readFile(envFile, 'utf8')
  if (!hasPasswordHash(contents)) {
    throw new Error(`Password setup completed without writing ${envFile}.`)
  }
}

await ensurePasswordHash()
const script = lanMode ? 'dev:lan:services' : 'dev:services'
const command = process.platform === 'win32' ? 'npm.cmd' : 'npm'
process.exitCode = await run(command, ['run', script])
