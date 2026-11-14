import assert from 'node:assert/strict'
import test from 'node:test'
import { devLogin, getSession, login, logout } from './auth.js'

test('getSession treats unauthenticated responses as a missing session', async () => {
  const session = await getSession(async () => new Response(null, { status: 401 }))
  assert.equal(session, null)
})

test('login sends the CSRF token and loads the authenticated session', async () => {
  const requests = []
  const fetchImpl = async (url, options) => {
    requests.push({ url, options })
    if (url === '/api/auth/csrf') {
      return Response.json({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' })
    }
    if (url === '/api/auth/login') {
      return new Response(null, { status: 204 })
    }
    return Response.json({ username: 'test-user' })
  }

  const session = await login('test-user', 'test-password', fetchImpl)

  assert.deepEqual(session, { username: 'test-user' })
  assert.equal(requests[1].options.headers['X-CSRF-TOKEN'], 'csrf-token')
  assert.deepEqual(JSON.parse(requests[1].options.body), {
    username: 'test-user',
    password: 'test-password',
  })
  assert.equal(requests[2].options.credentials, 'same-origin')
})

test('devLogin creates a CSRF-protected session without credentials', async () => {
  const requests = []
  const fetchImpl = async (url, options) => {
    requests.push({ url, options })
    if (url === '/api/auth/csrf') {
      return Response.json({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' })
    }
    if (url === '/api/auth/dev-login') {
      return new Response(null, { status: 204 })
    }
    return Response.json({ username: 'test-user' })
  }

  const session = await devLogin(fetchImpl)

  assert.deepEqual(session, { username: 'test-user' })
  assert.equal(requests[1].url, '/api/auth/dev-login')
  assert.equal(requests[1].options.method, 'POST')
  assert.equal(requests[1].options.headers['X-CSRF-TOKEN'], 'csrf-token')
  assert.equal(requests[1].options.credentials, 'same-origin')
  assert.equal(requests[2].url, '/api/auth/session')
})

test('logout sends a CSRF-protected same-origin request', async () => {
  const requests = []
  const fetchImpl = async (url, options) => {
    requests.push({ url, options })
    return url === '/api/auth/csrf'
      ? Response.json({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' })
      : new Response(null, { status: 204 })
  }

  await logout(fetchImpl)

  assert.equal(requests[1].url, '/api/auth/logout')
  assert.equal(requests[1].options.method, 'POST')
  assert.equal(requests[1].options.headers['X-CSRF-TOKEN'], 'csrf-token')
  assert.equal(requests[1].options.credentials, 'same-origin')
})
