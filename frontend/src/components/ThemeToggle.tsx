import { Monitor, Moon, Sun } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { nextTheme, THEME_LABELS, useTheme } from '@/lib/theme'

const ICONS = { light: Sun, dark: Moon, system: Monitor }

/** Satu tombol yang berganti Terang → Gelap → Ikut perangkat (docs/rencana-tema-kaca.md §4). */
export function ThemeToggle() {
  const [theme, setTheme] = useTheme()
  const Icon = ICONS[theme]
  const label = `Tema: ${THEME_LABELS[theme]}`
  return (
    <Button
      variant="ghost"
      size="icon"
      className="size-11 md:size-9"
      aria-label={`${label}. Ganti tema`}
      title={label}
      onClick={() => setTheme(nextTheme(theme))}
    >
      <Icon />
    </Button>
  )
}
