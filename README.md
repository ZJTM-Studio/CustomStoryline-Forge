# Custom Storyline

> **⚠️ 免责声明 / Disclaimer**
> 本项目（包括核心逻辑与架构）由 AI（DeepSeek-V4.1-Flash）辅助生成。尽管已进行基础测试，但受限于 AI 生成代码的局限性，仍可能存在未知的 Bug 或边界情况。请在测试环境中充分验证后再用于生产服务器。
> 
> Parts of this project (including core logic and architecture) were generated with the assistance of AI (DeepSeek-V4.1-Flash). While basic testing has been performed, there may still be undiscovered bugs or edge cases due to the limitations of AI-generated code. Please thoroughly test in a development environment before deploying to a production server.

**License:** CC0-1.0

---

## 📖 简介 | Introduction

**中文：**
一个基于 JSON 的轻量级剧情触发模组，专为 **Minecraft 1.20.1 Forge** 打造。允许服主或整合包作者通过在 `config` 文件夹中编写简单的 JSON 文件，即可在游戏内实现基于位置触发的文本、Title、状态效果、强制过场动画（锁定移动/视角/交互/绝对免伤）等复杂剧情，无需编写任何代码！

**English:**
A lightweight JSON-based storyline trigger mod built for **Minecraft 1.20.1 Forge**. It allows server owners or modpack creators to create complex, location-based storylines (text, titles, potion effects, forced cutscenes with movement/perspective/interaction locks, and absolute damage immunity) simply by writing JSON files in the `config` folder, with absolutely no coding required!

---

## ✨ 核心特性 | Features

*   📁 **纯 JSON 驱动 (Pure JSON-driven)**：无需懂代码，直接编辑 JSON 文件即可新建剧情。
*   🔍 **大小写不敏感 (Case-insensitive keys)**：JSON 中的键名（Key）不区分大小写，如 `Trigger_Position` 等同于 `trigger_position`。
*   📍 **整数坐标判定 (Integer-based coordinates)**：基于方块坐标触发。无论你在方块内怎么移动，只要脚底方块坐标对上就会触发。
*   ⏱️ **真·异步延迟 (True async delays)**：内置独立调度器，支持秒（s）或游戏刻（tick）的延迟，不卡服。
*   🛡️ **防刷屏机制 (Anti-spam mechanism)**：玩家站在触发点上只会触发一次，离开再进入才会再次触发。
*   🔒 **强力状态锁定 (Strong state locking)**：支持锁定移动、视角、交互，被锁定时**免疫一切伤害**且**强制清除怪物仇恨**。
*   🚫 **交互防呆提示 (Actionbar hints)**：玩家被锁定时试图违规操作，屏幕下方会弹出红字提示。
*   🌍 **多语言支持 (i18n support)**：内置中/英双语，方便国际化。
*   🔄 **热重载与排错 (Hot reload & error reporting)**：修改 JSON 后使用重载指令即刻生效，语法错误会直接在聊天栏提示。

---

## 🛠️ 指令列表 | Commands

所有指令均需要 OP 权限（等级 2） | All commands require OP permission (Level 2).

| 指令 / Command | 说明 / Description |
| :--- | :--- |
| `/storyline new <命名空间/namespace> [坐标/pos] [目标坐标/topos]` | 在当前位置或指定坐标/区域创建并保存一个新的剧情 JSON 文件。<br>Create and save a new storyline JSON at the current or specified coordinates/area. |
| `/storyline reload` | 从 `config/Customstoryline` 重新读取所有 JSON 文件。<br>Reload all JSON files from `config/Customstoryline`. |
| `/storyline remove <命名空间/namespace>` | 删除指定的剧情 JSON 文件。<br>Delete the specified storyline JSON file. |
| `/storyline reset <玩家/player>` | 重置目标玩家所有剧情的“已触发”状态。<br>Reset all "triggered" states for the target player. |
| `/storyline manage <玩家/player> <命名空间/namespace> set\|reset` | 单独设置或重置某位玩家的某个剧情状态。<br>Set or reset a specific storyline state for a player. |
| `/storyline test <命名空间/namespace>` | **调试专用**：无视坐标和唯一性限制，直接在原地强制触发。<br>**Debug**: Force trigger a storyline in-place, ignoring coordinates and uniqueness. |
| `/storyline unlock <玩家/player>` | **解卡专用**：强制解除目标玩家当前的所有锁定状态。<br>**Unlock**: Forcefully remove all lock states from the target player. |

---

## 📂 JSON 配置指南 | JSON Configuration Guide

启动游戏后，模组会自动在 `config/Customstoryline/` 目录下创建文件夹。在此文件夹内新建以 `.json` 结尾的文件即可（文件名即为命名空间）。
*The mod will automatically create a folder at `config/Customstoryline/`. Create a `.json` file inside this folder (the filename is the namespace).*

### 完整 JSON 示例 | Complete JSON Example

```json
{
  "trigger_position": [3, 69, 13, 10, 75, 20],
  "action": [
    {
      "text": "欢迎 @self 来到这片区域！你的金币为：@score:coins",
      "actionbar": "剧情开始，你被冻结了！",
      "set_block_player_operation": true
    },
    {
      "delay_trigger": "3s",
      "title": "警告",
      "subtitle": "前方危险...",
      "set_player_perspective": [180, 0]
    },
    {
      "delay_trigger": "20tick",
      "effect": "minecraft:regeneration",
      "effect_duration": 600,
      "effect_amplifier": 1,
      "text": "你获得了 生命恢复 II 持续 30秒。"
    },
    {
      "delay_trigger": "6s",
      "text": "剧情结束，解除锁定。",
      "unlock_block": true
    }
  ],
  "config": {
    "block_player_operation": true,
    "block_touch": true,
    "block_perspective": [false, 0, 0],
    "block_mob_target": true,
    "repeat": false,
    "uniqueness": true
  }
}
```

### 1. 坐标属性 | Trigger Position
*   **单点触发 (Single Point)**：`[x, y, z]`。例如 `[3, 69, 13]`。
*   **区域触发 (Area)**：`[x1, y1, z1, x2, y2, z2]`。两组坐标代表一个立体的长方体区域。
*   *注 (Note)*：坐标判定只看整数，如 `3.5` 和 `3.99` 都算作方块 `3`。 *(Judged by integer block coordinates, e.g., `3.5` and `3.99` both count as block `3`.)*

### 2. 动作列表 | Action List
这是一个数组，可以同时包含多个动作，它们按顺序执行。*(An array of actions executed sequentially.)*

**基础显示动作 (Basic Actions):**
*   `"text"`：在玩家聊天栏输出文本。*(Chat message)*
*   `"actionbar"`：在玩家快捷栏上方输出文本。*(Actionbar text)*
*   `"title"` / `"subtitle"`：在屏幕中央显示大标题/副标题。*(Title/Subtitle)*
*   `"delay_trigger"`：延迟触发。*(Delay execution)*
    *   写 `"5"` 或 `"2.5s"`：延迟 5 秒或 2.5 秒。*(Delays by seconds)*
    *   写 `"10tick"`：延迟 10 刻（20刻 = 1秒）。*(Delays by ticks)*

**状态效果动作 (Effect Actions):**
*   `"effect"`：状态效果 ID（如 `"speed"`，自动补全 `minecraft:`）。
*   `"effect_duration"`：（可选）持续时间，单位 tick。默认 200（10秒）。
*   `"effect_amplifier"`：（可选）效果等级。`0` 代表 1 级，`1` 代表 2 级。

**进阶动作（状态控制）| Advanced Actions (State Control):**
*   `"unlock_block": true`：瞬间解除玩家当前的所有锁定状态。*(Instantly removes all lock states.)*
*   `"set_player_perspective": [yaw, pitch]`：强制改变视角并锁定。*(Forcefully set and lock perspective, e.g., `[0, -90]` for looking up.)*
*   **动态修改状态 (Dynamic State Overrides)**：你可以在动作中覆盖 config 的初始设置，例如 `"set_block_player_operation": false`。可用字段：
    *   `set_block_player_operation`
    *   `set_block_touch`
    *   `set_block_perspective`
    *   `set_block_mob_target`

### 3. 替换符 | Placeholders
在 `text`、`title`、`subtitle`、`actionbar` 中可用 *(Available in text fields)*：
*   `@self`：替换为触发玩家名称。*(Replaced by player name)*
*   `@score:计分板名称`：替换为该玩家的计分板分数。*(Replaced by scoreboard value)*

### 4. 配置项 | Config Options
*   `"block_player_operation"`：`true` / `false`。是否禁止玩家移动/跳跃。默认 `true`。
*   `"block_touch"`：`true` / `false`。是否禁止玩家与方块/实体交互。默认 `false`。
*   `"block_perspective"`：`[bool, yaw, pitch]`。是否锁定视角。默认 `false`。
*   `"block_mob_target"`：`true` / `false`。是否在锁定期间**免疫一切伤害**并**清除怪物仇恨**。默认 `true`。
*   `"repeat"`：`true` / `false`。该剧情对于单个玩家是否允许重复触发。默认 `false`。
*   `"uniqueness"`：`true` / `false`。是否全局唯一（一人触发后全服失效）。默认 `true`。

---

## 💡 快速上手测试流程 | Quick Start Guide

1. 进入游戏，站在你想作为触发点的地方。 *(Stand where you want the trigger to be.)*
2. 输入指令：`/storyline new demo ~ ~ ~`。 *(Run: `/storyline new demo ~ ~ ~`)*
3. 打开 `config/Customstoryline/demo.json`，将内容替换为上面的**示例 JSON**。 *(Open the JSON and paste the example.)*
4. 回到游戏，输入 `/storyline reload`。 *(Run: `/storyline reload`)*
5. 离开刚才的位置，然后再走回去，即可看到触发效果！ *(Leave and re-enter the block to trigger it!)*
6. 如果卡住了，输入 `/storyline unlock @s` 即可脱困。 *(If stuck, run `/storyline unlock @s`)*
