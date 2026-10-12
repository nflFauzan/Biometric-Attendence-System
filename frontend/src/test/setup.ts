import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

// Render pertama di mesin yang sibuk bisa jauh lebih lama dari batas default findBy/waitFor (1 detik).
// Test tidak menguji kecepatan, jadi batas tunggu dinaikkan agar hasilnya tidak bergantung beban CPU.
configure({ asyncUtilTimeout: 10_000 })

// Vitest tidak memakai global afterEach, jadi DOM dibersihkan manual setelah setiap test.
afterEach(() => cleanup())
