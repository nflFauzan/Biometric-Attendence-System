/**
 * Kelas tambahan untuk DialogContent: di desktop tetap dialog di tengah,
 * di HP (< 768px) menjadi bottom sheet selebar layar (docs/design-system.md §6.8).
 */
export const dialogSheetClass = [
  'max-h-[90dvh] overflow-y-auto rounded-xl md:max-w-lg',
  'max-md:top-auto max-md:bottom-0 max-md:max-w-full max-md:translate-y-0',
  'max-md:rounded-t-2xl max-md:rounded-b-none max-md:border-x-0 max-md:border-b-0',
  'max-md:pb-[calc(1.5rem+env(safe-area-inset-bottom))]',
].join(' ')
