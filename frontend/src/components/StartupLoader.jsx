export default function StartupLoader() {
  return (
    <div role="status" className="fixed inset-0 z-[100] flex items-center justify-center bg-black">
      <span className="sr-only">Loading Cinelode …</span>
      <img
        src="/cinelode-loader.gif"
        alt=""
        width="414"
        height="414"
        className="h-auto w-[min(414px,80vmin)]"
      />
    </div>
  )
}
