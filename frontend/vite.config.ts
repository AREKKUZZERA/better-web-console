import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/',
  build: {
    outDir: '../target/generated-frontend/webconsole',
    emptyOutDir: true,
    sourcemap: false,
    assetsDir: 'assets'
  }
});
