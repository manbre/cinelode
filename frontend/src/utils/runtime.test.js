import assert from 'node:assert/strict'
import test from 'node:test'
import { isNativeWebView } from './runtime.js'

test('does not classify a regular browser as the native WebView', () => {
  assert.equal(isNativeWebView(), false)
})

test('detects the WebView message bridge', () => {
  globalThis.window = {
    ReactNativeWebView: { postMessage() {} },
  }
  try {
    assert.equal(isNativeWebView(), true)
  } finally {
    delete globalThis.window
  }
})
