import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const apiProxy = {
  "/api/v1": {
    target: "http://127.0.0.1:8080",
    changeOrigin: true,
    configure(proxy) {
      proxy.on("proxyRes", (proxyResponse) => {
        const cookies = proxyResponse.headers["set-cookie"];
        if (!cookies) return;
        proxyResponse.headers["set-cookie"] = cookies.map((cookie) =>
          cookie
            .replace(/;\s*Secure\b/gi, "")
            .replace(/;\s*SameSite=None\b/gi, "; SameSite=Lax"),
        );
      });
    },
  },
};

export default defineConfig({
  plugins: [react()],
  server: { port: 3001, strictPort: true, proxy: apiProxy },
  preview: { port: 3001, strictPort: true, proxy: apiProxy },
  test: { environment: "jsdom" },
});
