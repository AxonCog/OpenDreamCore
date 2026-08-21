# forge-1.6.4

先挖个坑，后面填。

- Forge 9.11.1.964 · MC 1.6.4 · Java 8
- 状态：**排队中**

1.6.4 这版本老到什么程度呢——Gradle 2.x、ForgeGradle 上古版、Java 8 还没 lambda。工具链跟现代完全是两个世界。
网络协议和 1.21+ 不通，只能独立跑。
渲染得从头写（GuiScreen + 2D 绘制那套老 API）。

common 倒是能复用，但得注意别用 JDK 9+ 的东西（`List.of`、`var`、record 这些想都别想）。

反正排最后做，先把现代版本搞完再说。
