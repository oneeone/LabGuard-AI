# LabGuard AI 部署与运行说明

本文对应 `main` 分支提交 `aa9ab12`，适用于 Java 17 及以上环境。默认使用 `demo` 模式，不调用真实视觉 API，也不需要配置密钥即可离线演示。

## 三种交付方式

下列三种方式任选其一即可；本项目同时提供前两种：

| 方式 | 本项目对应内容 | 适用场景 |
| --- | --- | --- |
| 可运行程序包 | `dist/LabGuard-AI-v1.0.0.zip` | 评委在本机直接运行 |
| 部署说明 | 本文 `DEPLOY.md` | 评委按步骤自行部署 |

程序包内已包含可执行 JAR、Windows/Linux 启动脚本、`.env.example` 和本说明，不包含 `.env` 或任何 API 密钥。

## 程序包获取方式

1. **GitHub Release 附件**（首选）：<https://github.com/oneeone/LabGuard-AI/releases>
   或直接取最新版：<https://github.com/oneeone/LabGuard-AI/releases/latest>
   - 文件名：`LabGuard-AI-v1.0.0.zip`
   - 版本：`v1.0.0`
   - 大小：约 35 MB（36,970,515 字节）；解压后 `labguard-ai.jar` 为 39,560,858 字节（JAR 已含全部依赖）
2. **作品附件**：与 ZIP 同源，本地文件即可运行。

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

## 环境要求

- JDK 17 或更高版本
- 可用的 `8080` 端口，或通过 `PORT` 环境变量指定其他端口
- Chrome / Edge / Firefox 等现代浏览器
- 首次启动无需联网；`demo` 模式完全离线
- 从源码构建还需要 Maven 3.9 或以上版本；只运行已打包 JAR 时不需要 Maven

检查 Java：

```bash
java -version
```

## Windows 快速启动

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

## Linux / macOS 启动

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

##  配置项

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

##  数据与备份

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

##  Docker 部署

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

## 常见问题

| 现象 | 处理 |
| --- | --- |
| `Port 8080 was already in use` | 设置 `PORT=8081` 后重启，或结束占用 8080 的进程 |
| `unable to open database file` | 确认启动目录下存在可写的 `data/`，或把 `DB_URL` 指向可写路径 |
| 上传后提示文件过大 | 页面限制 5MB，后端限制 10MB；压缩图片或调整 `MAX_FILE_SIZE` |
| `AUTH_FAILED` | 检查 `VISION_API_KEY` 是否完整、是否包含多余尖括号或空格 |
| `NOT_CONFIGURED` | `real` 模式下补齐 Base URL、Key、Model 三项 |
| 页面样式没有更新 | 强制刷新浏览器缓存（`Ctrl + F5`） |

## 从源码重新构建

需要重新生成可执行 JAR 时：

```bash
mvn clean package
java -jar target/labguard-ai-0.1.0-SNAPSHOT.jar
```

`mvn package` 会先执行全部测试；当前版本共 167 项测试。

##  安全边界

- AI 结果是辅助建议，不能替代人工复核和现场确认。
- AI 核验不会自动关闭工单，关闭动作必须由人工完成。
- `demo` 模式的结果始终标记为 `DEMO`，不会伪装成真实识别。
- 真实 API 失败时直接报错，不回退到演示数据，不伪造成功结果。
- 不要把 `.env`、真实密钥、实验室敏感照片提交到公开仓库。
