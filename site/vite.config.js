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
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-react': ['react', 'react-dom', 'react-dom/client'],
          'vendor-gsap': ['gsap', 'gsap/ScrollTrigger'],
          'vendor-motion': ['motion'],
        },
      },
    },
  },
})
