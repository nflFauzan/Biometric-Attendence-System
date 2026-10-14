// Pasang tema sebelum halaman tampil agar mode gelap tidak berkedip putih (docs/rencana-tema-kaca.md §4).
// Logikanya sama dengan src/lib/theme.ts; kunci penyimpanan harus sama.
try {
  var tema = localStorage.getItem('tema')
  var gelap = tema === 'dark' || (tema !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.classList.toggle('dark', gelap)
  document.documentElement.style.colorScheme = gelap ? 'dark' : 'light'
} catch {
  // localStorage diblokir: biarkan terang, src/lib/theme.ts akan menyesuaikan setelah aplikasi dimuat.
}
