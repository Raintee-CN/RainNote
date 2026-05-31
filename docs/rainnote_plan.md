# RainNote 手机便签方案

## 产品定位

RainNote 是一款 Kotlin Android 手机便签软件，核心体验是按行块编辑便签，并支持两台设备通过碰一碰完成配对和同步。

## 核心能力

- 手机优先，横屏、折叠屏和平板上使用响应式双栏布局。
- 便签由一行一行的块组成，而不是单个长文本框。
- 每一行可以独立选择单行文本、富文本或代码块。
- 按回车在普通文本和富文本块中创建下一行。
- 代码块内回车保留为代码换行，工具栏负责退出或新建下一块。
- NFC 负责碰一碰配对，后续同步可扩展为局域网、Wi-Fi Direct 或蓝牙传输。

## 数据模型

```kotlin
data class Note(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Long
)

data class NoteBlock(
    val id: String,
    val noteId: String,
    val type: BlockType,
    val content: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

enum class BlockType {
    PlainText,
    RichText,
    CodeBlock
}
```

## 编辑器交互

- 首页展示便签列表和当前便签编辑器。
- 小屏使用上下结构：便签列表在上，编辑器在下。
- 宽屏使用左右结构：左侧列表，右侧编辑器。
- 新建便签时自动创建第一行普通文本块。
- 每个块左侧显示类型标记：文本、富文、代码。
- 块右侧提供类型切换和删除操作。
- 普通文本或富文本块按回车会拆出下一块。
- 空块可删除，但每条便签至少保留一个块。

## 富文本策略

第一版采用轻量 Markdown 富文本：

- 存储为普通字符串，便于同步和冲突处理。
- 支持 `**加粗**`、`- 列表`、链接和标题等 Markdown 写法。
- 第一版重点完成存储、编辑和块级切换，后续再增加渲染和工具栏格式化。

## 碰一碰同步策略

Android Beam 已废弃，因此碰一碰不直接承载完整便签内容。

推荐流程：

1. 两台设备靠近并触发 NFC。
2. 交换设备 ID、设备名称、临时 token 和后续连接地址。
3. 用户确认配对。
4. 建立可信设备关系。
5. 后续通过局域网 HTTP、Wi-Fi Direct 或蓝牙传输同步包。

第一版实现 NFC 配对入口和同步数据包结构，网络传输层留作扩展点。

## 同步包

```kotlin
data class SyncPayload(
    val deviceId: String,
    val notes: List<Note>,
    val blocks: List<NoteBlock>,
    val deletedIds: List<String>,
    val timestamp: Long
)
```

## 冲突处理

第一版采用块级最后更新时间策略：

- 不同块可以独立合并。
- 同一块两端都发生修改时保留本地版本，并把远端版本追加为冲突副本。
- 后续可升级为 CRDT，但不在 MVP 中引入复杂协同算法。

## 当前实现范围

- 本地 SQLite 持久化。
- 便签列表。
- 响应式列表加编辑器布局。
- 行块编辑器。
- 单行文本、富文本、代码块三种类型。
- 新建、编辑、删除、类型切换。
- NFC 权限、设备标识和同步管理基础类。

## 后续增强

- Room + Migration 替代手写 SQLiteOpenHelper。
- Markdown 渲染和格式化工具栏。
- 代码语法高亮。
- Wi-Fi Direct 或局域网 HTTP 实际同步传输。
- 加密配对和端到端同步加密。
- 标签、搜索、归档和回收站。
