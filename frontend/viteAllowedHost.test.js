import assert from 'node:assert/strict'
import test from 'node:test'
import { getAllowedDevHosts } from './viteAllowedHost.js'

test('allows no additional host by default', () => {
  assert.deepEqual(getAllowedDevHosts(undefined), [])
})

test('allows the configured exact DNS hostname', () => {
  assert.deepEqual(getAllowedDevHosts(' CINElode.DuckDNS.org '), ['cinelode.duckdns.org'])
})

test('rejects broad and malformed host values', () => {
  for (const host of ['', '.example.com', '*.example.com', 'example.com:5175', 'https://example.com', 'a..b', 'a,b', '-host', 'a'.repeat(64)]) {
    assert.throws(() => getAllowedDevHosts(host), /exact DNS hostname/)
  }
})
