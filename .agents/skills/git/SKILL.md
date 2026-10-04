---
name: git
description: 查看、整理或提交 MKCS 仓库的 Git 变更；用于工作区范围、提交信息和验证记录。
metadata:
  short-description: MKCS Git 变更规范
---

# MKCS Git 变更规范

## 范围与安全

- 先检查 `git status --short` 与限定路径的 diff，保留其他人未提交的修改。不得使用 `reset --hard`、`checkout --`、`restore` 或强制覆盖未知来源文件。
- 提交、推送、创建分支或改写历史仅在用户明确要求时执行。完成实现不自动代表需要提交。
- 禁止提交 `.env`、密钥、构建产物、IDE 状态、日志、临时文件或与当前任务无关的改动；`.env.example` 可以提交。

## 提交质量

- 使用 Conventional Commits：`feat`、`fix`、`refactor`、`test`、`docs`、`chore`、`perf` 或 `style`，标题具体描述对象和行为。
- 一个提交保持单一目的。复杂变更在正文记录实际改动原因和实际执行的验证；不杜撰测试结果或人工修正。
- 暂存时使用限定路径，之后查看 `git diff --cached` 与 `git diff --check`。避免 `git add .` 掩盖无关文件。

## 提交信息与人工修正

- 禁止无意义提交信息：`update`、`修改`、`test`、`aaa`、`fix bug`、`change`。标题必须让读者不看 diff 也能知道对象和行为。
- Commit type 按主要目的选择：`feat` 新功能、`fix` 修复、`refactor` 行为不变的重构、`test` 测试、`docs` 文档、`chore` 维护、`perf` 性能、`style` 纯格式。新增功能附带测试仍用 `feat`；修复附带测试仍用 `fix`。
- 小型单文件文档改动可只写标题。复杂业务、Bug、配置或人工修正必须写正文，且只描述实际发生的内容：

```text
fix: return a successful response after password reset

What changed: return the standard Result after a verified password reset
Why: the endpoint previously completed work but returned null
Human Corrections: None
Verification: ./mvnw -pl mkcs-server -am test (passed)
```

- 人工修改影响最终代码时，`Human Corrections` 必须逐条说明“改了什么、为什么”；没有则写 `None`。人工修改后必须重新验证，不能沿用修改前的结果。

## 提交流程

仅在用户要求提交时，按以下顺序执行：

1. `git status --short && git diff --stat && git diff --cached --stat && git log -5 --oneline`
2. `git diff -- <paths>` 与 `git diff --check`，确认范围、敏感信息和意外格式化。
3. 执行并记录实际验证。
4. `git add -- <paths>`，再执行 `git diff --cached` 确认暂存内容。
5. 使用准确的标题和必要正文提交；之后执行 `git status --short` 并报告提交 ID 与文件范围。

禁止伪造 `Verification`，禁止提交 `.env`、token、密码、构建产物或不相关修改，禁止为让提交“好看”而顺手重构。

## 验证记录

根据改动运行最小相关检查：Skill 文档检查 frontmatter 与链接；Java 运行 Maven 编译/测试；SQL、配置和对象存储改动运行相应迁移或集成验证。依赖不可用时明确说明未验证项和原因。
