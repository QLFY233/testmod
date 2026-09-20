# Test Mod

Minecraft **1.21.1** + **NeoForge 21.1.251** 的测试模组。

**目的**：打通「写代码 → GitHub Actions 云端编译 → 下载 jar → 进游戏看效果」这条链路。

## 它做什么

按 **K** 键打开一个自定义 GUI 界面，里面摆了四种常用控件：

| 控件 | 演示内容 |
|---|---|
| `EditBox` | 文本输入，界面底部实时回显内容 |
| `Checkbox` | 开关状态 |
| 自定义 `AbstractSliderButton` 子类 | 0~100 的数值滑块，拖动时文字实时更新 |
| `Button` × 2 | 重置 / 关闭 |

## 编译

本机**不装 JDK / Gradle**，编译全部在云端完成：

```bash
git push
```

然后去 [Actions](https://github.com/QLFY233/testmod/actions) 页面等构建完成，
在该次 run 页面底部 **Artifacts** 区下载 `mod-jar.zip`，解压得到 jar。

## 安装

1. 安装 Minecraft 1.21.1 和 [NeoForge](https://neoforged.net/) 21.1.251
2. 启动一次游戏，让启动器生成 `.minecraft/mods/` 目录
3. 把 jar 丢进去
4. 启动游戏，在 Mods 列表里确认 Test Mod 已加载
5. 进入世界后按 **K**（可在「选项 → 控制 → 测试模组」里改键）

## 代码结构

```
src/main/java/io/github/qlfy233/testmod/
├── TestMod.java          @Mod 主类，通用逻辑（本 mod 没有注册任何物品/方块）
├── TestModClient.java    @Mod(dist = Dist.CLIENT)，客户端专用入口
└── client/
    ├── KeyBindings.java  按键注册 + tick 检测
    └── DemoGuiScreen.java 界面本体
```

`src/main/resources/assets/testmod/lang/` 下有 `en_us.json` 和 `zh_cn.json`，
界面上的所有文字都走翻译键，所以显示语言跟随游戏设置。

## 许可

MIT
