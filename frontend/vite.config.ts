import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
  plugins: [react()],
  base: '/',
  server: { host: '0.0.0.0', port: 5500, strictPort: true, proxy: { '/greenpilot': 'http://localhost:8080', '/iam': 'http://localhost:8080' } },
  build: { rollupOptions: { output: { manualChunks(id) {
    if (!id.includes('node_modules')) return;
    if (id.includes('/react/') || id.includes('/react-dom/') || id.includes('/scheduler/')) return 'vendor-react';
    if (id.includes('/recharts/') || id.includes('/d3-')) return 'vendor-charts';
    if (id.includes('/leaflet/') || id.includes('/react-leaflet/')) return 'vendor-map';
    if (id.includes('/@tanstack/')) return 'vendor-query';
    if (id.includes('/lucide-react/')) return 'vendor-icons';
  } } } },
});
