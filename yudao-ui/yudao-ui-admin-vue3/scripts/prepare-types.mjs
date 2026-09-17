import { build } from 'vite'

// Use the application's actual auto-import/component configuration, not a
// handwritten declaration list. No application bundle is emitted.
const entry = 'virtual:npdms-typecheck-entry'
await build({
  mode: 'test',
  logLevel: 'error',
  plugins: [{
    name: 'npdms-typecheck-entry',
    resolveId(id) { if (id === entry) return '\0' + entry },
    load(id) { if (id === '\0' + entry) return 'export {}' }
  }],
  build: {
    write: false,
    emptyOutDir: false,
    rollupOptions: { input: entry }
  }
})
