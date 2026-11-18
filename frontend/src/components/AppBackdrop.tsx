import { useEffect, useState } from 'react'
import { loadWallpaper } from '@/lib/glassTheme'

interface AppBackdropProps {
  className?: string
}

export function AppBackdrop({ className = '' }: AppBackdropProps) {
  const [wallpaper, setWallpaper] = useState(() => loadWallpaper())
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const handleUpdate = (e: Event) => {
      const customEvent = e as CustomEvent<string>
      if (customEvent.detail) {
        setWallpaper(customEvent.detail)
        setFailed(false)
      }
    }
    window.addEventListener('takka-wallpaper-changed', handleUpdate)
    return () => window.removeEventListener('takka-wallpaper-changed', handleUpdate)
  }, [])

  return (
    <div
      aria-hidden="true"
      className={`pointer-events-none fixed inset-0 -z-30 overflow-hidden ${className}`}
    >
      {wallpaper && !failed ? (
        <img
          src={wallpaper}
          alt=""
          className="absolute inset-0 size-full object-cover object-center motion-safe:transition-opacity motion-safe:duration-700"
          onError={() => setFailed(true)}
        />
      ) : (
        <div className="app-backdrop absolute inset-0 size-full" />
      )}
      <div
        className="absolute inset-0 bg-black/40 backdrop-contrast-105 dark:bg-black/55"
        style={{
          backgroundImage:
            'radial-gradient(circle at center, transparent 30%, rgba(0,0,0,0.4) 100%)',
        }}
      />
    </div>
  )
}
