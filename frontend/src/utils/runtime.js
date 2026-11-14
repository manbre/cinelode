export function isNativeWebView() {
  return typeof window !== 'undefined'
    && typeof window.ReactNativeWebView?.postMessage === 'function'
}
