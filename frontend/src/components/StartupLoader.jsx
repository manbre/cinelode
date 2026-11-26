import { useEffect, useState } from 'react'

const GIF_RUN_MS = 2520

export default function StartupLoader({ onMinimumElapsed }) {
  const [imageSettled, setImageSettled] = useState(false)

  useEffect(() => {
    if (!imageSettled) return
    const timer = window.setTimeout(() => onMinimumElapsed(true), GIF_RUN_MS)
    return () => window.clearTimeout(timer)
  }, [imageSettled, onMinimumElapsed])

  return (
    <div role="status" className="fixed inset-0 z-[100] flex items-center justify-center bg-black">
      <span className="sr-only">Loading Cinelode …</span>
      <img
        src="/cinelode-loader.gif"
        alt=""
        onLoad={() => setImageSettled(true)}
        onError={() => onMinimumElapsed(true)}
        width="414"
        height="414"
        className="h-auto w-[min(414px,80vmin)]"
      />
    </div>
  )
}
