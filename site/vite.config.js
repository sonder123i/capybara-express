import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Relative base keeps the same build working at the Render root and under the
// /capybara-express/ path on GitHub Pages.
export default defineConfig({
  base: './',
  plugins: [react()],
  build: {
    target: 'es2020',
    cssMinify: true,
  },
})
