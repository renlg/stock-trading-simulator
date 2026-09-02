# 模拟股票交易系统前端

基于 React 18、TypeScript、Vite 5、Ant Design 5、axios 和 react-router-dom 构建。

## 本地启动

请先确保 Spring Boot 后端已在 `http://localhost:8080` 启动，然后执行：

```bash
npm install
npm run dev
```

开发服务器会将 `/api` 请求代理到 `http://localhost:8080`。请打开 Vite 输出的本地地址访问系统。

## 生产构建

```bash
npm run build
npm run preview
```

生产文件输出到 `dist/` 目录。
