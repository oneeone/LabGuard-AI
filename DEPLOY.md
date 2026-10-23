# LabGuard AI 部署与运行说明

本文适用于 v1.0.0 程序包与 `main` 分支代码，要求 Java 17 及以上环境。默认使用 `demo` 模式，不调用真实视觉 API，也不需要配置密钥即可离线演示。

## 1. 三种交付方式

作品提交时，下列三种方式任选其一即可；本项目同时提供前两种：

| 方式 | 本项目对应内容 | 适用场景 |
| --- | --- | --- |
| 可运行程序包 | `dist/LabGuard-AI-v1.0.0.zip` | 评委在本机直接运行 |
| 部署说明 | 本文 `DEPLOY.md` | 评委按步骤自行部署 |
| 在线体验地址 | 按第 8 节部署后获得公网 URL | 需要免安装体验 |

程序包内已包含可执行 JAR、Windows/Linux 启动脚本、`.env.example` 和本说明，不包含 `.env` 或任何 API 密钥。

### 1.1 程序包获取方式

**运行包不在 Git 仓库的文件树里。** `dist/` 被 `.gitignore` 忽略（Spring Initializr 生成的 NetBeans 段落含 `/dist/`），因此 `git clone` 或仓库 ZIP 下载都拿不到 `dist/LabGuard-AI-v1.0.0.zip`。请按下列顺序获取：

1. **GitHub Release 附件**（首选）：<https://github.com/oneeone/LabGuard-AI/releases>
   或直接取最新版：<https://github.com/oneeone/LabGuard-AI/releases/latest>
   - 文件名：`LabGuard-AI-v1.0.0.zip`，版本 `v1.0.0`，大小约 35 MB（36,970,515 字节）
   - 解压后 `labguard-ai.jar` 为 39,560,858 字节（已含全部依赖）
2. **赛事提交系统的作品附件**：与 ZIP 同源，下载后直接解压运行。
3. **按第 11 节从源码自行构建**：`mvn clean package` 生成同等可执行 JAR，无需下载程序包。

包内共 8 个文件，已逐项审计：无 `.env`、无 `data/labguard.db`、无 `uploads/` 图片、无 API 密钥：

```text
LabGuard-AI-v1.0.0/
├── labguard-ai.jar      # 可执行 JAR（含全部依赖）
├── start-windows.bat    # Windows 一键启动
├── start-linux.sh       # Linux/macOS 启动脚本
├── DEPLOY.md            # 本说明
├── .env.example         # 环境变量清单，密钥字段一律留空
├── 使用说明.txt
├── data/.keep           # 空占位，首次启动自动建库
└── uploads/.keep        # 空占位，首次上传自动建目录
```

## 2. 环境要求

- JDK 17 或更高版本
- 可用的 `8080` 端口，或通过 `PORT` 环境变量指定其他端口
- Chrome / Edge / Firefox 等现代浏览器
- 首次启动无需联网；`demo` 模式完全离线
- 从源码构建还需要 Maven 3.9 或以上版本；只运行已打包 JAR 时不需要 Maven

检查 Java：

```bash
java -version
```

## 3. Windows 快速启动

1. 解压 `LabGuard-AI-v1.0.0.zip`，得到 `LabGuard-AI-v1.0.0` 目录。
2. 双击 `start-windows.bat`。
3. 浏览器访问 <http://localhost:8080>。
4. 停止服务：在启动窗口按 `Ctrl + C`，或直接关闭窗口。

也可以在 PowerShell 中手动运行：

```powershell
cd .\LabGuard-AI-v1.0.0
java -jar .\labguard-ai.jar
```

首次启动会自动创建 `data/labguard.db`；`uploads/` 在首次保存图片时自动创建。数据库表结构由 `schema.sql` 自动初始化。

## 4. Linux / macOS 启动

```bash
unzip LabGuard-AI-v1.0.0.zip
cd LabGuard-AI-v1.0.0
chmod +x start-linux.sh
./start-linux.sh
```

或手动运行：

```bash
java -jar labguard-ai.jar
```

## 5. 配置项

默认配置可以直接运行。需要修改端口或使用真实视觉 API 时，将 `.env.example` 复制为同目录下的 `.env`：

```powershell
Copy-Item .env.example .env
```

```bash
cp .env.example .env
```

常用变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PORT` | `8080` | HTTP 端口 |
| `VISION_MODE` | `demo` | `demo` 返回固定样例并标记 DEMO；`real` 调用真实视觉 API |
| `VISION_API_BASE_URL` | 空 | OpenAI 兼容接口地址，可写基础地址或完整 `/chat/completions` |
| `VISION_API_KEY` | 空 | 视觉 API 密钥，禁止提交到 Git |
| `VISION_MODEL` | 空 | 视觉模型名称 |
| `UPLOAD_DIR` | `uploads` | 上传图片保存目录 |
| `DB_URL` | `jdbc:sqlite:./data/labguard.db` | SQLite 文件路径 |
| `MAX_FILE_SIZE` | `10MB` | 单张图片上限，页面端另有 5MB 预校验 |

配置优先级：操作系统环境变量 > 同目录 `.env` > `application.properties` 默认值。

### 使用真实视觉 API

```env
VISION_MODE=real
VISION_API_BASE_URL=https://<服务商>/v1
VISION_API_KEY=<你的密钥>
VISION_MODEL=<视觉模型名>
```

注意：

- `real` 模式缺少任意一项配置会在发起请求前报 `NOT_CONFIGURED`，不会回退到演示数据。
- 鉴权失败、限流、超时、网络错误、响应格式非法都会显示明确错误，不会伪造识别结果。
- `.env` 已被 `.gitignore` 忽略，不要通过聊天、截图、提交记录或公开仓库传递密钥。
- 在线部署时优先使用平台提供的 Secret/Environment Variable，不要把 `.env` 打包进镜像或提交到仓库。

## 6. 数据与备份

运行数据只有两处：

```text
LabGuard-AI/
├── data/labguard.db     # SQLite 业务数据
└── uploads/             # 上传的巡检与整改图片
```

备份时同时复制 `data/` 和 `uploads/`，否则数据库中的图片 URL 会失效。

重置演示数据：停止服务后删除 `data/labguard.db` 和 `uploads/` 内的文件，再重新启动。数据库会用 `schema.sql` 重新建表。

部署到 Linux 时确保运行用户对这两个目录有读写权限：

```bash
mkdir -p data uploads
chown -R <运行用户>:<运行用户> data uploads
```

## 7. Docker 部署

仓库根目录提供 `Dockerfile`，镜像内默认 `VISION_MODE=demo`，不会包含 `.env`。

```bash
docker build -t labguard-ai:1.0.0 .
docker run -d --name labguard-ai \
  -p 8080:8080 \
  -v labguard-data:/app/data \
  -v labguard-uploads:/app/uploads \
  labguard-ai:1.0.0
```

访问 <http://localhost:8080>。需要真实 API 时，用环境变量文件启动，且该文件不要提交：

```bash
docker run -d --name labguard-ai \
  -p 8080:8080 \
  --env-file .env \
  -v labguard-data:/app/data \
  -v labguard-uploads:/app/uploads \
  labguard-ai:1.0.0
```

必须把 `/app/data` 和 `/app/uploads` 挂载到持久卷；否则容器重建后 SQLite 数据和上传图片会丢失。

## 8. 获得在线体验地址

> 在线地址必须依托一个持续运行的主机。没有云平台账号时，无法仅靠本地代码生成稳定的公网 URL。

### 方案 A：支持 Docker 的云平台（推荐）

Render、Railway、Fly.io 等平台均可直接使用仓库中的 `Dockerfile`：

1. 将仓库推送到 GitHub，在平台创建 Web Service 并选择 Docker 部署。
2. 设置环境变量 `VISION_MODE=demo`；公开演示不要配置真实密钥。
3. 挂载持久磁盘：
   - 数据目录：`/app/data`
   - 上传目录：`/app/uploads`
   - 如果平台只允许一个挂载点，把卷挂到 `/app/data`，并设置 `UPLOAD_DIR=/app/data/uploads`，让数据库和图片共用同一个持久卷。
4. 平台会自动注入 `PORT`，应用已通过 `server.port=${PORT:...}` 适配。
5. 部署完成后，把平台生成的 `https://...` 地址作为在线体验地址。

注意：免费套餐可能使用临时文件系统，重启或重新部署后 SQLite 数据会清空。正式演示请使用持久卷，或改用云数据库。

### 方案 B：云服务器 + systemd

以 Ubuntu 为例，把程序包解压到 `/opt/labguard`：

```ini
# /etc/systemd/system/labguard.service
[Unit]
Description=LabGuard AI
After=network.target

[Service]
User=labguard
WorkingDirectory=/opt/labguard
EnvironmentFile=/opt/labguard/.env
ExecStart=/usr/bin/java -jar /opt/labguard/labguard-ai.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now labguard
sudo systemctl status labguard
```

再使用 Nginx 反向代理到 `127.0.0.1:8080`，并配置 HTTPS。云服务器安全组只需开放 80/443，不要直接暴露管理端口。

### 方案 C：临时演示隧道

Cloudflare Tunnel、ngrok 等工具可以把本机 `8080` 临时映射为公网地址，适合答辩前快速演示，但地址依赖本机持续运行，不适合作为长期提交地址。

## 9. 常见问题

| 现象 | 处理 |
| --- | --- |
| `Port 8080 was already in use` | 设置 `PORT=8081` 后重启，或结束占用 8080 的进程 |
| `unable to open database file` | 确认启动目录下存在可写的 `data/`，或把 `DB_URL` 指向可写路径 |
| 上传后提示文件过大 | 页面限制 5MB，后端限制 10MB；压缩图片或调整 `MAX_FILE_SIZE` |
| `AUTH_FAILED` | 检查 `VISION_API_KEY` 是否完整、是否包含多余尖括号或空格 |
| `NOT_CONFIGURED` | `real` 模式下补齐 Base URL、Key、Model 三项 |
| 页面样式没有更新 | 强制刷新浏览器缓存（`Ctrl + F5`） |

## 10. 提交文案参考

可按作品提交表要求填写：

- 可运行程序包：`LabGuard-AI-v1.0.0.zip`（Java 17+，默认 demo 模式，无需密钥），下载入口见第 1.1 节的 GitHub Releases 页面
- 源代码仓库：<https://github.com/oneeone/LabGuard-AI>
- 开源组件、第三方模型、外部 API 与数据集使用说明：`_docs/开源组件与第三方资源使用说明.md`
- 部署说明：本文件
- 在线体验地址：完成第 8 节部署后填写平台生成的 URL

## 11. 从源码重新构建

需要重新生成可执行 JAR 时：

```bash
mvn clean package
java -jar target/labguard-ai-0.1.0-SNAPSHOT.jar
```

`mvn package` 会先执行全部测试；当前版本共 167 项测试。

## 12. 安全边界

- AI 结果是辅助建议，不能替代人工复核和现场确认。
- AI 核验不会自动关闭工单，关闭动作必须由人工完成。
- `demo` 模式的结果始终标记为 `DEMO`，不会伪装成真实识别。
- 真实 API 失败时直接报错，不回退到演示数据，不伪造成功结果。
- 不要把 `.env`、真实密钥、实验室敏感照片提交到公开仓库。
