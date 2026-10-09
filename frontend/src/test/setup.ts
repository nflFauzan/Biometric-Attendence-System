import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Vitest tidak memakai global afterEach, jadi DOM dibersihkan manual setelah setiap test.
afterEach(() => cleanup())
