# OpenDreamCore 附属插件开发指南

## 依赖

```groovy
// build.gradle
repositories {
    mavenLocal() // 或 flatDir { dirs 'libs' }
}
dependencies {
    compileOnly 'com.opendreamcore:opendreamcore-plugin:0.1.0'
}
```

plugin.yml 里加 `depend: [OpenDreamCore]`。

## 快速上手

```java
public class MyAddon extends JavaPlugin {

    @Override
    public void onEnable() {
        // 注册自定义脚本方法（页面 YAML 里直接调用）
        ScriptAPI.register("Shop", "购买", args -> {
            Player p = (Player) args[0];
            String item = String.valueOf(args[0]);
            // ... 购买逻辑
            return "§a购买成功";
        });

        // 监听 GUI 事件
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onButtonClick(OdcEvents.ButtonEvent e) {
        if ("buy_button".equals(e.getElementId())) {
            getLogger().info(e.getPlayer().getName() + " 点击了购买");
        }
    }
}
```

## API 一览

### 页面操作 (gui)

| 方法 | 说明 |
|---|---|
| `open(player, pageId)` | 打开页面，返回会话 id |
| `openSubPage(player, pageId)` | 打开子页 |
| `close(player)` | 关闭当前页面 |
| `isOpen(player)` | 是否有打开的页面 |
| `setVariable(player, key, value)` | 推送页面变量 |
| `setVariables(player, map)` | 批量推送变量 |
| `setElementProp(player, elementId, path, value)` | 设置元素属性 |
| `openContainer(player, pageId, inventory, type, title)` | 打开并绑定容器 |
| `closeContainer(player)` | 关闭容器 |

### 元素控制 (element)

| 方法 | 说明 |
|---|---|
| `show(player, elementId)` | 显示元素 |
| `hide(player, elementId)` | 隐藏元素 |
| `setProp(player, elementId, path, value)` | 设置属性（支持 dotted path） |

dotted path 示例：
```java
// 修改文本内容
ElementAPI.INSTANCE.setProp(player, "title", "text.content", "新标题");
// 修改按钮颜色
ElementAPI.INSTANCE.setProp(player, "buy", "button.background", "#FF0000");
```

### HUD (hud)

| 方法 | 说明 |
|---|---|
| `mountHud(player, pageId)` | 挂载个人 HUD |
| `unmountHud(player)` | 卸载个人 HUD |
| `mountGlobalHud(pageId)` | 全体挂载 |
| `showBossBar(target, id, text, progress, color)` | Boss 条 |
| `setNameTag(playerName, text, color)` | 头顶名牌 |
| `playMusic(target, file, volume, loop)` | 播放音乐 |
| `shake(player, strength, durationMs)` | 屏幕震动 |

### 容器 (container)

| 方法 | 说明 |
|---|---|
| `getItem(player, slot)` | 读容器槽位物品 |
| `setItem(player, slot, stack)` | 写容器槽位物品 |
| `clearSlot(player, slot)` | 清空槽位 |
| `getCursor(player)` | 光标物品 |
| `resync(player)` | 手动推送容器快照 |

### Tooltip (tooltip)

| 方法 | 说明 |
|---|---|
| `setTooltip(elementId, text)` | 设置元素提示 |
| `removeTooltip(elementId)` | 移除提示 |

### 脚本注册 (script)

| 方法 | 说明 |
|---|---|
| `register(namespace, method, handler)` | 注册脚本方法 |
| `registerAlias(namespace, handler, names...)` | 多别名注册 |

## 事件

所有事件在主线程触发。`ButtonEvent` 可取消（取消后不执行 actions 脚本）。

| 事件 | 触发时机 |
|---|---|
| `OdcEvents.OpenEvent` | 页面打开 |
| `OdcEvents.CloseEvent` | 页面关闭 |
| `OdcEvents.ButtonEvent` | 元素点击（可取消） |
| `OdcEvents.InputEvent` | 输入框提交 |
| `OdcEvents.SlotEvent` | 容器槽位点击 |
| `OdcEvents.HoverEvent` | 悬停 |
| `OdcEvents.PressEvent` | 按压（滑块拖动） |
| `OdcEvents.ScrollEvent` | 滚动 |
| `OdcEvents.KeyEvent` | 键盘绑定触发 |
| `OdcEvents.MouseEvent` | 鼠标绑定触发 |
| `OdcEvents.ChatEvent` | 聊天输入提交 |
| `OdcEvents.LayoutEvent` | 编辑器布局保存 |

## 完整示例：简易商店

```java
public class ShopAddon extends JavaPlugin {

    @Override
    public void onEnable() {
        // 注册脚本方法
        ScriptAPI.register("Shop", "购买", args -> {
            Player p = (Player) args[0];
            String itemId = String.valueOf(args[1]);
            double price = ((Number) args[2]).doubleValue();
            // 这里接 Vault 或其他经济插件
            return "§a购买 " + itemId + " x1 成功！扣费 " + price;
        });

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    // 打开商店
    public void openShop(Player player) {
        GUIAPI gui = OpenDreamCoreAPI.gui();
        gui.setVariable(player, "gold", getBalance(player));
        gui.open(player, "shop");
    }

    // 监听购买按钮
    @EventHandler
    public void onBuy(OdcEvents.ButtonEvent e) {
        if (!"buy_diamond".equals(e.getElementId())) return;
        Player p = e.getPlayer();
        p.sendMessage("§b购买了钻石！");
        OpenDreamCoreAPI.gui().setVariable(p, "gold", getBalance(p) - 100);
    }
}
```
