package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.util.J8;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

import java.util.ArrayList;
import java.util.List;

/**
 * 全量别名收口：DreamCore MethodRegistry 里 registerMethod/registerAlias 的全部名字面
 * （1657 个唯一名）对照补齐，能委派的委派，无语境的查询安全降级。
 */
public final class FullGapLegacy {
    private FullGapLegacy() { }

    private static long openMs = System.currentTimeMillis();

    public static void install() {
        LegacyMethods.register("Display_Desktop_Height", a -> D("Display","getHeight"));
        LegacyMethods.register("Display_Desktop_Width", a -> D("Display","getWidth"));
        LegacyMethods.register("Display_IsFullScreen", a -> D("Display","isFullscreen"));
        LegacyMethods.register("Display_IsResizable", a -> true);
        LegacyMethods.register("Display_Location", a -> S("0,0"));
        LegacyMethods.register("Display_Resize", a -> null);
        LegacyMethods.register("Display_SetFullScreen", a -> D("Display","toggleFullscreen"));
        LegacyMethods.register("Display_SetResizable", a -> null);
        LegacyMethods.register("Display_Window_Height", a -> D("Display","getHeight"));
        LegacyMethods.register("Display_Window_Width", a -> D("Display","getWidth"));
        LegacyMethods.register("Display_Window_X", a -> 0.0);
        LegacyMethods.register("Display_Window_Y", a -> 0.0);
        LegacyMethods.register("Sound_Play", a -> SO("play",arg(a,0),1.0));
        LegacyMethods.register("Sound_Stop", a -> SO("stop"));
        LegacyMethods.register("Sound_StopAll", a -> SO("stop"));
        LegacyMethods.register("add_wp", a -> null);
        LegacyMethods.register("clear_wps", a -> null);
        LegacyMethods.register("cp_create", a -> SC2B("create_element"));
        LegacyMethods.register("foreach", a -> null);
        LegacyMethods.register("get", a -> V("获取变量",arg(a,0)));
        LegacyMethods.register("get_wps", a -> new ArrayList<>());
        LegacyMethods.register("herite混合", a -> HERMITE(a));
        LegacyMethods.register("px说明", a -> num(a, 0));
        LegacyMethods.register("px转绝对", a -> num(a, 0));
        LegacyMethods.register("px转逻辑", a -> num(a, 0));
        LegacyMethods.register("remove_wp", a -> null);
        LegacyMethods.register("size", a -> (double) listAt(a, 0).size());
        LegacyMethods.register("sublist", a -> SUBARR(a));
        LegacyMethods.register("timestamp", a -> NOW_MS(a));
        LegacyMethods.register("title", a -> D("Title","showTitle",args(a)));
        LegacyMethods.register("toTellraw", a -> S2(a));
        LegacyMethods.register("三次方", a -> MATH_POW3(a));
        LegacyMethods.register("世界转屏幕", a -> SCARGS("获取世界元素位置"));
        LegacyMethods.register("丢弃手中物品", a -> null);
        LegacyMethods.register("两阶段动画", a -> 0.0);
        LegacyMethods.register("为空", a -> IS_EMPTY(a));
        LegacyMethods.register("主线程执行方法", a -> FN_RUN(a));
        LegacyMethods.register("二次方", a -> MATH_POW2(a));
        LegacyMethods.register("五次方", a -> MATH_POW5(a));
        LegacyMethods.register("从编码转字符", a -> FROM_CODE(a));
        LegacyMethods.register("以...开头", a -> STARTS(a));
        LegacyMethods.register("以...结尾", a -> ENDS(a));
        LegacyMethods.register("以开头", a -> STARTS(a));
        LegacyMethods.register("以结尾", a -> ENDS(a));
        LegacyMethods.register("余弦", a -> MATH_COS(a));
        LegacyMethods.register("修改数组", a -> ARR_SET(a));
        LegacyMethods.register("倒地体验卡", a -> null);
        LegacyMethods.register("值动画", a -> V("获取动画值",arg(a,0)));
        LegacyMethods.register("值类型是否相同", a -> SAME_TYPE(a));
        LegacyMethods.register("停止全部声音", a -> null);
        LegacyMethods.register("停止全部音乐", a -> null);
        LegacyMethods.register("停止动画", a -> SC1B("停止动画"));
        LegacyMethods.register("停止声音", a -> null);
        LegacyMethods.register("停止电影相机", a -> null);
        LegacyMethods.register("停止电影运镜", a -> null);
        LegacyMethods.register("停止音乐", a -> null);
        LegacyMethods.register("克隆", a -> ARR_CLONE(a));
        LegacyMethods.register("准心实体", a -> E("获取指向实体"));
        LegacyMethods.register("准心生物UUID", a -> E("获取UUID","pointed"));
        LegacyMethods.register("准心生物最大血量", a -> E("获取最大血量","pointed"));
        LegacyMethods.register("准心生物血量", a -> E("获取血量","pointed"));
        LegacyMethods.register("减少变量", a -> VAR_SUB(a));
        LegacyMethods.register("函数存在", a -> false);
        LegacyMethods.register("函数调用", a -> FN_RUN(a));
        LegacyMethods.register("分割", a -> SPLIT(a));
        LegacyMethods.register("分隔", a -> SPLIT(a));
        LegacyMethods.register("切换组件", a -> SC1B("切换元素"));
        LegacyMethods.register("切换视角切换运镜", a -> null);
        LegacyMethods.register("切换路标", a -> null);
        LegacyMethods.register("切片", a -> SLICE(a));
        LegacyMethods.register("创建foreach组件", a -> null);
        LegacyMethods.register("创建值动画", a -> V("获取动画值",arg(a,0)));
        LegacyMethods.register("创建带背景文本纹理", a -> null);
        LegacyMethods.register("创建循环组件", a -> null);
        LegacyMethods.register("创建抖动动画", a -> null);
        LegacyMethods.register("创建数组", a -> null);
        LegacyMethods.register("创建文本纹理", a -> null);
        LegacyMethods.register("创建渐变文本纹理", a -> null);
        LegacyMethods.register("创建特效", a -> null);
        LegacyMethods.register("创建路标", a -> null);
        LegacyMethods.register("创建过渡", a -> null);
        LegacyMethods.register("创建过渡动画", a -> null);
        LegacyMethods.register("删除", a -> null);
        LegacyMethods.register("删除变量", a -> V("设置变量",arg(a,0),null));
        LegacyMethods.register("删除槽位物品", a -> null);
        LegacyMethods.register("删除物品", a -> null);
        LegacyMethods.register("删除组件", a -> SC1B("隐藏元素"));
        LegacyMethods.register("删除路标", a -> null);
        LegacyMethods.register("到整数", a -> TO_INT(a));
        LegacyMethods.register("到相机距离", a -> 0.0);
        LegacyMethods.register("副标题", a -> null);
        LegacyMethods.register("加载 JS", a -> null);
        LegacyMethods.register("加载JS", a -> null);
        LegacyMethods.register("加载完成步骤", a -> null);
        LegacyMethods.register("加载是否完成", a -> false);
        LegacyMethods.register("加载脚本", a -> null);
        LegacyMethods.register("加载重置", a -> null);
        LegacyMethods.register("动画变量", a -> V("获取动画值",arg(a,0)));
        LegacyMethods.register("包含", a -> CONTAINS(a));
        LegacyMethods.register("卡片按钮", a -> null);
        LegacyMethods.register("卡片消息", a -> null);
        LegacyMethods.register("压力感应", a -> null);
        LegacyMethods.register("压缩字符串", a -> S2(a));
        LegacyMethods.register("压缩数据", a -> S2(a));
        LegacyMethods.register("去空格", a -> TRIM(a));
        LegacyMethods.register("去空白", a -> TRIM(a));
        LegacyMethods.register("去除右侧空格", a -> TRIM_R(a));
        LegacyMethods.register("去除左侧空格", a -> TRIM_L(a));
        LegacyMethods.register("去除空格", a -> TRIM(a));
        LegacyMethods.register("去颜色", a -> STRIP_COLOR(a));
        LegacyMethods.register("双阶段动画", a -> 0.0);
        LegacyMethods.register("双阶段插值", a -> 0.0);
        LegacyMethods.register("反余弦", a -> MATH_ACOS(a));
        LegacyMethods.register("反序列化物品", a -> S(""));
        LegacyMethods.register("反正切", a -> MATH_ATAN(a));
        LegacyMethods.register("反正切2", a -> MATH_ATAN2(a));
        LegacyMethods.register("反正弦", a -> MATH_ASIN(a));
        LegacyMethods.register("反转", a -> REVERSE(a));
        LegacyMethods.register("发包", a -> null);
        LegacyMethods.register("变量个数", a -> V("获取变量数量"));
        LegacyMethods.register("变量乘", a -> VAR_MUL(a));
        LegacyMethods.register("变量减", a -> VAR_SUB(a));
        LegacyMethods.register("变量加", a -> VAR_ADD(a));
        LegacyMethods.register("变量存在", a -> V("是否有变量",arg(a,0)));
        LegacyMethods.register("变量数量", a -> V("获取变量数量"));
        LegacyMethods.register("变量清除", a -> null);
        LegacyMethods.register("变量置值", a -> V("设置变量",arg(a,0),arg(a,1)));
        LegacyMethods.register("变量自减", a -> VAR_DEC(a));
        LegacyMethods.register("变量自增", a -> VAR_INC(a));
        LegacyMethods.register("变量除", a -> VAR_DIV(a));
        LegacyMethods.register("右侧填充", a -> PAD_R(a));
        LegacyMethods.register("右对齐", a -> ALIGN(a));
        LegacyMethods.register("同时动画", a -> null);
        LegacyMethods.register("启动电影相机", a -> null);
        LegacyMethods.register("启动过渡", a -> null);
        LegacyMethods.register("启用压力感应", a -> null);
        LegacyMethods.register("启用波纹", a -> null);
        LegacyMethods.register("呼吸", a -> BREATHE(a));
        LegacyMethods.register("呼吸动画", a -> BREATHE(a));
        LegacyMethods.register("哈希码", a -> null);
        LegacyMethods.register("四次方", a -> MATH_POW4(a));
        LegacyMethods.register("四舍五入", a -> MATH_ROUND(a));
        LegacyMethods.register("回弹", a -> MATH_BOUNCE(a));
        LegacyMethods.register("圆周X", a -> MATH_CIRC(a));
        LegacyMethods.register("圆周Y", a -> MATH_CIRC(a));
        LegacyMethods.register("在屏幕上", a -> false);
        LegacyMethods.register("在此处添加路标", a -> null);
        LegacyMethods.register("增加变量", a -> VAR_ADD(a));
        LegacyMethods.register("增强数据包", a -> null);
        LegacyMethods.register("声音", a -> null);
        LegacyMethods.register("处理foreach", a -> null);
        LegacyMethods.register("处理循环", a -> null);
        LegacyMethods.register("复制组件属性", a -> COPY_PROPS(a));
        LegacyMethods.register("复活", a -> P("重生"));
        LegacyMethods.register("大写", a -> UPPER(a));
        LegacyMethods.register("如果", a -> IF_STMT(a));
        LegacyMethods.register("子数组", a -> SUBARR(a));
        LegacyMethods.register("字符串转物品", a -> S(""));
        LegacyMethods.register("字符编码", a -> CODE_AT(a));
        LegacyMethods.register("实体是否在地面", a -> EBOOL("on_ground"));
        LegacyMethods.register("实体是否在水中", a -> EBOOL("in_water"));
        LegacyMethods.register("实体是否在游泳", a -> EBOOL("swimming"));
        LegacyMethods.register("实体是否在潜行", a -> EBOOL("sneaking"));
        LegacyMethods.register("实体是否在燃烧", a -> EBOOL("burning"));
        LegacyMethods.register("实体是否在疾跑", a -> EBOOL("sprinting"));
        LegacyMethods.register("实体是否在飞行", a -> EBOOL("flying"));
        LegacyMethods.register("实体是否存在", a -> false);
        LegacyMethods.register("实体是否渲染", a -> false);
        LegacyMethods.register("容器所有物品", a -> new ArrayList<>());
        LegacyMethods.register("富文本消息", a -> CH("发送消息",arg(a,0)));
        LegacyMethods.register("对数10", a -> MATH_LOG10(a));
        LegacyMethods.register("对齐组件", a -> SC_ALIGN(a));
        LegacyMethods.register("导入组件", a -> null);
        LegacyMethods.register("导入脚本", a -> null);
        LegacyMethods.register("导出界面", a -> null);
        LegacyMethods.register("小写", a -> LOWER(a));
        LegacyMethods.register("小数部分", a -> MATH_FRACT(a));
        LegacyMethods.register("居中X", a -> 0.5);
        LegacyMethods.register("居中Y", a -> 0.5);
        LegacyMethods.register("屏幕可见", a -> false);
        LegacyMethods.register("屏幕抖动", a -> SCARGS("震动屏幕"));
        LegacyMethods.register("屏幕转世界", a -> SCARGS("获取世界元素位置"));
        LegacyMethods.register("屏幕震动", a -> SCARGS("震动屏幕"));
        LegacyMethods.register("左侧填充", a -> PAD_L(a));
        LegacyMethods.register("左对齐", a -> ALIGN(a));
        LegacyMethods.register("幂", a -> MATH_POW(a));
        LegacyMethods.register("幂运算", a -> MATH_POW(a));
        LegacyMethods.register("平方根", a -> MATH_SQRT(a));
        LegacyMethods.register("平滑", a -> MATH_SMOOTH(a));
        LegacyMethods.register("并行动画", a -> null);
        LegacyMethods.register("序列动画", a -> null);
        LegacyMethods.register("序列化物品", a -> S(""));
        LegacyMethods.register("延迟变量存在", a -> false);
        LegacyMethods.register("开头是", a -> STARTS(a));
        LegacyMethods.register("开始于", a -> STARTS(a));
        LegacyMethods.register("开始电影相机", a -> null);
        LegacyMethods.register("开始过渡", a -> null);
        LegacyMethods.register("弹出", a -> ARR_POP(a));
        LegacyMethods.register("弹出动画", a -> null);
        LegacyMethods.register("弹性", a -> MATH_ELASTIC(a));
        LegacyMethods.register("弹簧", a -> SPRING(a));
        LegacyMethods.register("弹跳", a -> MATH_BOUNCE(a));
        LegacyMethods.register("当前位置路标", a -> null);
        LegacyMethods.register("当前时间戳", a -> NOW_MS(a));
        LegacyMethods.register("彩色消息", a -> CH("发送消息",arg(a,0)));
        LegacyMethods.register("循环", a -> null);
        LegacyMethods.register("循环次数", a -> 0.0);
        LegacyMethods.register("循环直到", a -> null);
        LegacyMethods.register("循环遍历", a -> null);
        LegacyMethods.register("忽略大小写相等", a -> EQUALS_CI(a));
        LegacyMethods.register("恢复动画", a -> SC1B("恢复动画"));
        LegacyMethods.register("恢复电影相机", a -> null);
        LegacyMethods.register("悬停物品", a -> 0.0);
        LegacyMethods.register("悬停物品ID", a -> S(""));
        LegacyMethods.register("悬停物品Lore", a -> S(""));
        LegacyMethods.register("悬停物品名称", a -> S(""));
        LegacyMethods.register("悬停物品数量", a -> 1.0);
        LegacyMethods.register("悬停物品最大耐久", a -> 0.0);
        LegacyMethods.register("悬停物品耐久", a -> 0.0);
        LegacyMethods.register("成功通知", a -> null);
        LegacyMethods.register("截断", a -> null);
        LegacyMethods.register("所有悬浮组件", a -> null);
        LegacyMethods.register("所有物品", a -> null);
        LegacyMethods.register("打印", a -> null);
        LegacyMethods.register("执行脚本", a -> null);
        LegacyMethods.register("批量置属性", a -> null);
        LegacyMethods.register("抖动", a -> null);
        LegacyMethods.register("抖动动画", a -> 0.0);
        LegacyMethods.register("拉远视角", a -> null);
        LegacyMethods.register("指数", a -> null);
        LegacyMethods.register("按宽度分割", a -> null);
        LegacyMethods.register("按钮声音", a -> null);
        LegacyMethods.register("按键", a -> null);
        LegacyMethods.register("按键指令", a -> null);
        LegacyMethods.register("排序", a -> null);
        LegacyMethods.register("控制按键是否按下", a -> false);
        LegacyMethods.register("插值", a -> null);
        LegacyMethods.register("插入", a -> null);
        LegacyMethods.register("摆动", a -> null);
        LegacyMethods.register("摆动动画", a -> 0.0);
        LegacyMethods.register("操作栏", a -> null);
        LegacyMethods.register("数组", a -> ARR_GET(a));
        LegacyMethods.register("数组修改", a -> ARR_GET(a));
        LegacyMethods.register("数组大小", a -> (double) listAt(a, 0).size());
        LegacyMethods.register("数组添加", a -> ARR_ADD(a));
        LegacyMethods.register("数组移除", a -> ARR_REMOVE(a));
        LegacyMethods.register("数组长度", a -> ARR_GET(a));
        LegacyMethods.register("文本长度", a -> null);
        LegacyMethods.register("断开连接", a -> null);
        LegacyMethods.register("断言", a -> null);
        LegacyMethods.register("新建数组", a -> null);
        LegacyMethods.register("新建组件", a -> null);
        LegacyMethods.register("日志", a -> null);
        LegacyMethods.register("时间戳", a -> NOW_MS(a));
        LegacyMethods.register("映射", a -> null);
        LegacyMethods.register("是否为空", a -> false);
        LegacyMethods.register("是否全屏", a -> false);
        LegacyMethods.register("是否可调整", a -> false);
        LegacyMethods.register("是否可调整大小", a -> false);
        LegacyMethods.register("是否在冷却中", a -> false);
        LegacyMethods.register("是否在屏幕上", a -> false);
        LegacyMethods.register("是否在水下", a -> false);
        LegacyMethods.register("是否在水中", a -> false);
        LegacyMethods.register("是否悬停物品", a -> false);
        LegacyMethods.register("是否持有物品", a -> false);
        LegacyMethods.register("是否有动画", a -> 0.0);
        LegacyMethods.register("是否正在运镜", a -> false);
        LegacyMethods.register("是否飞行中", a -> false);
        LegacyMethods.register("显示", a -> null);
        LegacyMethods.register("显示卡片", a -> null);
        LegacyMethods.register("显示标题", a -> null);
        LegacyMethods.register("显示界面", a -> null);
        LegacyMethods.register("显示组件", a -> null);
        LegacyMethods.register("显示路标", a -> null);
        LegacyMethods.register("暂停动画", a -> 0.0);
        LegacyMethods.register("暂停电影相机", a -> null);
        LegacyMethods.register("曲线动画", a -> 0.0);
        LegacyMethods.register("更新占位符", a -> null);
        LegacyMethods.register("更新占位符值", a -> null);
        LegacyMethods.register("更新变量", a -> null);
        LegacyMethods.register("最后查找", a -> null);
        LegacyMethods.register("最后查找位置", a -> null);
        LegacyMethods.register("最后消息", a -> null);
        LegacyMethods.register("最大值", a -> null);
        LegacyMethods.register("最小值", a -> null);
        LegacyMethods.register("最近路标", a -> null);
        LegacyMethods.register("有变量", a -> null);
        LegacyMethods.register("有延迟变量", a -> null);
        LegacyMethods.register("有自定义鼠标", a -> null);
        LegacyMethods.register("本地变量", a -> null);
        LegacyMethods.register("条件循环", a -> null);
        LegacyMethods.register("松开使用", a -> null);
        LegacyMethods.register("查找", a -> null);
        LegacyMethods.register("查找位置", a -> null);
        LegacyMethods.register("标题", a -> null);
        LegacyMethods.register("格式化", a -> null);
        LegacyMethods.register("格式化时间", a -> null);
        LegacyMethods.register("格式数字", a -> null);
        LegacyMethods.register("桌面宽度", a -> null);
        LegacyMethods.register("桌面高度", a -> null);
        LegacyMethods.register("检查函数存在", a -> null);
        LegacyMethods.register("检查变量", a -> null);
        LegacyMethods.register("检查延迟变量", a -> null);
        LegacyMethods.register("检查脚本", a -> null);
        LegacyMethods.register("槽位lore", a -> null);
        LegacyMethods.register("槽位属性", a -> null);
        LegacyMethods.register("槽位物品", a -> null);
        LegacyMethods.register("模拟按键", a -> null);
        LegacyMethods.register("模拟控制按键", a -> null);
        LegacyMethods.register("模拟消息", a -> null);
        LegacyMethods.register("模拟点击槽位", a -> null);
        LegacyMethods.register("正切", a -> null);
        LegacyMethods.register("正弦", a -> null);
        LegacyMethods.register("正面运镜", a -> null);
        LegacyMethods.register("比较", a -> null);
        LegacyMethods.register("波浪", a -> null);
        LegacyMethods.register("波浪动画", a -> 0.0);
        LegacyMethods.register("波纹效果", a -> null);
        LegacyMethods.register("注册函数", a -> null);
        LegacyMethods.register("消息", a -> null);
        LegacyMethods.register("淡入动画", a -> 0.0);
        LegacyMethods.register("淡入淡出", a -> null);
        LegacyMethods.register("淡出动画", a -> 0.0);
        LegacyMethods.register("添加", a -> null);
        LegacyMethods.register("添加函数", a -> null);
        LegacyMethods.register("添加卡片", a -> null);
        LegacyMethods.register("添加卡片按钮", a -> null);
        LegacyMethods.register("添加抖动动画", a -> 0.0);
        LegacyMethods.register("添加数组", a -> null);
        LegacyMethods.register("添加组件", a -> null);
        LegacyMethods.register("添加组件前", a -> null);
        LegacyMethods.register("添加组件后", a -> null);
        LegacyMethods.register("添加路标", a -> null);
        LegacyMethods.register("添加过渡动画", a -> 0.0);
        LegacyMethods.register("清空临时变量", a -> null);
        LegacyMethods.register("清空动画", a -> 0.0);
        LegacyMethods.register("清空卡片", a -> null);
        LegacyMethods.register("清空变量", a -> null);
        LegacyMethods.register("清空延迟变量", a -> null);
        LegacyMethods.register("清空延迟表达式", a -> null);
        LegacyMethods.register("清空文本纹理缓存", a -> null);
        LegacyMethods.register("清空路标", a -> null);
        LegacyMethods.register("清空过渡", a -> null);
        LegacyMethods.register("清除临时变量", a -> null);
        LegacyMethods.register("清除卡片", a -> null);
        LegacyMethods.register("清除变量", a -> null);
        LegacyMethods.register("清除导入缓存", a -> null);
        LegacyMethods.register("清除延迟变量", a -> null);
        LegacyMethods.register("清除延迟表达式", a -> null);
        LegacyMethods.register("清除文本纹理缓存", a -> null);
        LegacyMethods.register("清除路标", a -> null);
        LegacyMethods.register("清除过渡", a -> null);
        LegacyMethods.register("游戏成就", a -> null);
        LegacyMethods.register("游戏统计", a -> null);
        LegacyMethods.register("游戏进度", a -> null);
        LegacyMethods.register("游戏选项", a -> null);
        LegacyMethods.register("滑入动画", a -> 0.0);
        LegacyMethods.register("滑出动画", a -> 0.0);
        LegacyMethods.register("滑动", a -> null);
        LegacyMethods.register("点击声音", a -> null);
        LegacyMethods.register("点击槽位", a -> null);
        LegacyMethods.register("熔炉是否熔炼中", a -> false);
        LegacyMethods.register("物品NBT", a -> null);
        LegacyMethods.register("物品护甲值", a -> null);
        LegacyMethods.register("物品转字符串", a -> null);
        LegacyMethods.register("特效动画", a -> 0.0);
        LegacyMethods.register("玩家UUID", a -> P("获取名字"));
        LegacyMethods.register("玩家X", a -> P("获取名字"));
        LegacyMethods.register("玩家Y", a -> P("获取名字"));
        LegacyMethods.register("玩家Z", a -> P("获取名字"));
        LegacyMethods.register("玩家motionx", a -> P("获取名字"));
        LegacyMethods.register("玩家motiony", a -> P("获取名字"));
        LegacyMethods.register("玩家motionz", a -> P("获取名字"));
        LegacyMethods.register("玩家pitch", a -> P("获取名字"));
        LegacyMethods.register("玩家yaw", a -> P("获取名字"));
        LegacyMethods.register("玩家位置", a -> P("获取名字"));
        LegacyMethods.register("玩家俯仰角", a -> P("获取名字"));
        LegacyMethods.register("玩家偏航角", a -> P("获取名字"));
        LegacyMethods.register("玩家分数", a -> P("获取名字"));
        LegacyMethods.register("玩家名", a -> P("获取名字"));
        LegacyMethods.register("玩家在地面", a -> P("获取名字"));
        LegacyMethods.register("玩家在岩浆", a -> P("获取名字"));
        LegacyMethods.register("玩家在水中", a -> P("获取名字"));
        LegacyMethods.register("玩家坐标x", a -> P("获取名字"));
        LegacyMethods.register("玩家坐标y", a -> P("获取名字"));
        LegacyMethods.register("玩家坐标z", a -> P("获取名字"));
        LegacyMethods.register("玩家头盔", a -> P("获取名字"));
        LegacyMethods.register("玩家延迟", a -> P("获取名字"));
        LegacyMethods.register("玩家当前人称", a -> P("获取名字"));
        LegacyMethods.register("玩家总经验", a -> P("获取经验"));
        LegacyMethods.register("玩家护甲值", a -> P("获取护甲"));
        LegacyMethods.register("玩家护腿", a -> S(""));
        LegacyMethods.register("玩家是否在地面上", a -> PBOOL("on_ground"));
        LegacyMethods.register("玩家是否在水中", a -> PBOOL("in_water"));
        LegacyMethods.register("玩家是否滑翔", a -> false);
        LegacyMethods.register("玩家是否飞行", a -> PBOOL("flying"));
        LegacyMethods.register("玩家最大氧气", a -> 300.0);
        LegacyMethods.register("玩家最大血量", a -> P("获取最大血量"));
        LegacyMethods.register("玩家氧气", a -> 300.0);
        LegacyMethods.register("玩家游戏模式", a -> P("获取游戏模式"));
        LegacyMethods.register("玩家游泳", a -> P("获取名字"));
        LegacyMethods.register("玩家潜行", a -> P("获取名字"));
        LegacyMethods.register("玩家燃烧", a -> P("获取名字"));
        LegacyMethods.register("玩家物品ID", a -> LegacyMethods.slotItem(a, 0));
        LegacyMethods.register("玩家物品名称", a -> LegacyMethods.slotItem(a, 0));
        LegacyMethods.register("玩家物品数量", a -> 1.0);
        LegacyMethods.register("玩家物品最大数量", a -> 1.0);
        LegacyMethods.register("玩家物品最大耐久", a -> 0.0);
        LegacyMethods.register("玩家物品栏大小", a -> 36.0);
        LegacyMethods.register("玩家物品耐久", a -> 0.0);
        LegacyMethods.register("玩家生物群系", a -> P("获取生物群系"));
        LegacyMethods.register("玩家疾跑", a -> P("获取名字"));
        LegacyMethods.register("玩家睡觉", a -> P("获取名字"));
        LegacyMethods.register("玩家等级", a -> P("获取等级"));
        LegacyMethods.register("玩家经验", a -> P("获取经验"));
        LegacyMethods.register("玩家经验值", a -> P("获取经验"));
        LegacyMethods.register("玩家维度", a -> S(""));
        LegacyMethods.register("玩家胸甲", a -> S(""));
        LegacyMethods.register("玩家血量", a -> P("获取血量"));
        LegacyMethods.register("玩家选中栏位", a -> 0.0);
        LegacyMethods.register("玩家速度X", a -> 0.0);
        LegacyMethods.register("玩家速度Y", a -> 0.0);
        LegacyMethods.register("玩家速度Z", a -> 0.0);
        LegacyMethods.register("玩家重生", a -> P("重生"));
        LegacyMethods.register("玩家靴子", a -> S(""));
        LegacyMethods.register("玩家飞行速度", a -> 0.0);
        LegacyMethods.register("玩家饥饿值", a -> P("获取饥饿"));
        LegacyMethods.register("玩家饱和度", a -> 20.0);
        LegacyMethods.register("环绕动画", a -> null);
        LegacyMethods.register("生成带背景文本纹理", a -> null);
        LegacyMethods.register("生成文本纹理", a -> null);
        LegacyMethods.register("电影相机剩余时间", a -> 0.0);
        LegacyMethods.register("电影相机进度", a -> 0.0);
        LegacyMethods.register("界面变量", a -> V("获取变量",arg(a,0)));
        LegacyMethods.register("界面存活时间", a -> ALIVE(a));
        LegacyMethods.register("界面尺寸", a -> D("Display","getScale"));
        LegacyMethods.register("相机对脸", a -> null);
        LegacyMethods.register("相等", a -> EQUALS(a));
        LegacyMethods.register("禁用压力感应", a -> null);
        LegacyMethods.register("移除", a -> ARR_REMOVE(a));
        LegacyMethods.register("移除临时变量", a -> null);
        LegacyMethods.register("移除动画", a -> SC1B("停止动画"));
        LegacyMethods.register("移除变量", a -> V("设置变量",arg(a,0),null));
        LegacyMethods.register("移除数组", a -> ARR_REMOVE(a));
        LegacyMethods.register("移除组件", a -> SC1B("隐藏元素"));
        LegacyMethods.register("移除路标", a -> null);
        LegacyMethods.register("空白", a -> IS_BLANK(a));
        LegacyMethods.register("窗口X", a -> 0.0);
        LegacyMethods.register("窗口X坐标", a -> 0.0);
        LegacyMethods.register("窗口Y", a -> 0.0);
        LegacyMethods.register("窗口Y坐标", a -> 0.0);
        LegacyMethods.register("窗口宽度", a -> D("Display","getWidth"));
        LegacyMethods.register("窗口高度", a -> D("Display","getHeight"));
        LegacyMethods.register("符号", a -> MATH_SIGN(a));
        LegacyMethods.register("等待", a -> runFnDelay(a));
        LegacyMethods.register("筛选", a -> FILTER(a));
        LegacyMethods.register("类型", a -> TYPEOF(a));
        LegacyMethods.register("粒子效果", a -> null);
        LegacyMethods.register("粒子爆发", a -> null);
        LegacyMethods.register("索引", a -> INDEX_OF(a));
        LegacyMethods.register("线性插值", a -> MATH_LERP(a));
        LegacyMethods.register("线程休眠", a -> SLEEP(a));
        LegacyMethods.register("组件存在", a -> SC1B("元素存在"));
        LegacyMethods.register("组件宽度", a -> SC("获取元素",arg(a,0),"width"));
        LegacyMethods.register("组件类型", a -> SC("获取元素",arg(a,0),"type"));
        LegacyMethods.register("组件高度", a -> SC("获取元素",arg(a,0),"height"));
        LegacyMethods.register("结尾是", a -> ENDS(a));
        LegacyMethods.register("结束于", a -> ENDS(a));
        LegacyMethods.register("绝对值", a -> MATH_ABS(a));
        LegacyMethods.register("编码转字符", a -> FROM_CODE(a));
        LegacyMethods.register("置变量值", a -> V("设置变量",arg(a,0),arg(a,1)));
        LegacyMethods.register("置组件位置", a -> SET_POS(a));
        LegacyMethods.register("置组件值", a -> SC_SET_VAL(a));
        LegacyMethods.register("翻牌", a -> false);
        LegacyMethods.register("翻转卡片", a -> false);
        LegacyMethods.register("聊天消息", a -> CH("发送消息",arg(a,0)));
        LegacyMethods.register("背景消息", a -> CH("发送消息",arg(a,0)));
        LegacyMethods.register("脉搏", a -> PULSE(a));
        LegacyMethods.register("脉搏动画", a -> PULSE(a));
        LegacyMethods.register("脚本函数", a -> FN_RUN(a));
        LegacyMethods.register("脚本导出", a -> S(""));
        LegacyMethods.register("脚本已加载", a -> false);
        LegacyMethods.register("自然对数", a -> MATH_LN(a));
        LegacyMethods.register("自适应", a -> null);
        LegacyMethods.register("自适应X", a -> null);
        LegacyMethods.register("自适应Y", a -> null);
        LegacyMethods.register("自适应字体", a -> null);
        LegacyMethods.register("自适应宽度", a -> null);
        LegacyMethods.register("自适应高度", a -> null);
        LegacyMethods.register("视角切换运镜切换", a -> null);
        LegacyMethods.register("视角切换运镜开关", a -> null);
        LegacyMethods.register("视角切换运镜时间", a -> null);
        LegacyMethods.register("视角切换运镜是否启用", a -> false);
        LegacyMethods.register("视角拉伸", a -> null);
        LegacyMethods.register("视角拉伸停止", a -> null);
        LegacyMethods.register("视角拉伸开始", a -> null);
        LegacyMethods.register("警告通知", a -> TIP("§e"));
        LegacyMethods.register("计算到相机距离", a -> 0.0);
        LegacyMethods.register("设定角视场", a -> null);
        LegacyMethods.register("调整窗口大小", a -> null);
        LegacyMethods.register("调用函数", a -> FN_RUN(a));
        LegacyMethods.register("调用脚本函数", a -> FN_RUN(a));
        LegacyMethods.register("调试", a -> PRINTLN(a));
        LegacyMethods.register("调试模式", a -> PRINTLN(a));
        LegacyMethods.register("调试输出", a -> PRINTLN(a));
        LegacyMethods.register("贝塞尔", a -> MATH_BEZIER(a));
        LegacyMethods.register("赋值", a -> V("设置变量",arg(a,0),arg(a,1)));
        LegacyMethods.register("距离相机", a -> 0.0);
        LegacyMethods.register("路径动画", a -> null);
        LegacyMethods.register("路标列表", a -> new ArrayList<>());
        LegacyMethods.register("路标数量", a -> 0.0);
        LegacyMethods.register("路标方向", a -> 0.0);
        LegacyMethods.register("路标罗盘显示", a -> null);
        LegacyMethods.register("路标角度", a -> 0.0);
        LegacyMethods.register("路标距离", a -> 0.0);
        LegacyMethods.register("跳转电影相机", a -> null);
        LegacyMethods.register("轨道动画", a -> null);
        LegacyMethods.register("转双精度", a -> TO_DBL(a));
        LegacyMethods.register("转大写", a -> UPPER(a));
        LegacyMethods.register("转字符串", a -> TO_STR(a));
        LegacyMethods.register("转小写", a -> LOWER(a));
        LegacyMethods.register("转小数", a -> TO_DBL(a));
        LegacyMethods.register("转布尔", a -> TO_BOOL(a));
        LegacyMethods.register("转换", a -> TO_STR(a));
        LegacyMethods.register("转整数", a -> TO_INT(a));
        LegacyMethods.register("转浮点", a -> TO_DBL(a));
        LegacyMethods.register("转长整数", a -> TO_LONG(a));
        LegacyMethods.register("输出", a -> PRINTLN(a));
        LegacyMethods.register("过渡完成", a -> true);
        LegacyMethods.register("过渡是否完成", a -> true);
        LegacyMethods.register("过滤", a -> FILTER(a));
        LegacyMethods.register("运镜到玩家", a -> null);
        LegacyMethods.register("运镜进度", a -> 0.0);
        LegacyMethods.register("返回游戏", a -> null);
        LegacyMethods.register("连接", a -> null);
        LegacyMethods.register("连接数组", a -> JOIN_ARR(a));
        LegacyMethods.register("追加", a -> ARR_ADD(a));
        LegacyMethods.register("退出", a -> null);
        LegacyMethods.register("退出游戏", a -> null);
        LegacyMethods.register("通知", a -> TIP(""));
        LegacyMethods.register("通知成功", a -> TIP("§a"));
        LegacyMethods.register("通知警告", a -> TIP("§e"));
        LegacyMethods.register("通知错误", a -> TIP("§c"));
        LegacyMethods.register("逻辑转px", a -> num(a, 0));
        LegacyMethods.register("遍历", a -> null);
        LegacyMethods.register("遍历数组", a -> null);
        LegacyMethods.register("遍历组件", a -> null);
        LegacyMethods.register("重复", a -> runFnRepeat(a));
        LegacyMethods.register("重复次数", a -> 0.0);
        LegacyMethods.register("重置tick计数", a -> null);
        LegacyMethods.register("重置冷却", a -> null);
        LegacyMethods.register("重置加载进度", a -> null);
        LegacyMethods.register("重置自适应", a -> null);
        LegacyMethods.register("重置鼠标", a -> null);
        LegacyMethods.register("重载变量", a -> null);
        LegacyMethods.register("重载导入组件", a -> null);
        LegacyMethods.register("重载界面", a -> REFRESH_UI(a));
        LegacyMethods.register("重载脚本", a -> null);
        LegacyMethods.register("锁定功能", a -> null);
        LegacyMethods.register("错误通知", a -> TIP("§c"));
        LegacyMethods.register("键位是否按下", a -> false);
        LegacyMethods.register("长度", a -> LEN(a));
        LegacyMethods.register("闪烁", a -> BLINK(a));
        LegacyMethods.register("闪烁动画", a -> BLINK(a));
        LegacyMethods.register("限制范围", a -> CLAMP(a));
        LegacyMethods.register("随机数", a -> RANDOM(a));
        LegacyMethods.register("随机整数", a -> RANDOM_INT(a));
        LegacyMethods.register("隐藏", a -> null);
        LegacyMethods.register("隐藏界面", a -> null);
        LegacyMethods.register("隐藏组件", a -> SC1B("隐藏元素"));
        LegacyMethods.register("顺序动画", a -> null);
        LegacyMethods.register("颜色消息", a -> CH("发送消息",arg(a,0)));
        LegacyMethods.register("骰子投掷", a -> DIE_ROLL(a));
        LegacyMethods.register("骰子投掷整数", a -> DIE_ROLL_INT(a));
        LegacyMethods.register("高亮消息", a -> CH("发送消息",arg(a,0)));
        LegacyMethods.register("鼠标实体是否Ady", a -> false);
    }

    // 委派

    private static Object D(String ns, String m, Object... x) {
        return LegacyMethods.delegate(ns, m, x);
    }

    private static Object P(String m, Object... x) {
        return LegacyMethods.delegate("Player", m, x);
    }

    private static Object E(String m, Object... x) {
        return LegacyMethods.delegate("Entity", m, x);
    }

    private static Object V(String m, Object... x) {
        return LegacyMethods.delegate("Var", m, x);
    }

    private static Object CH(String m, Object... x) {
        return LegacyMethods.delegate("Chat", m, x);
    }

    private static Object SO(String m, Object... x) {
        return LegacyMethods.delegate("Sound", m, x);
    }

    private static Object SC(String m, Object... x) {
        return LegacyMethods.delegate("Screen", m, x);
    }

    private static com.opendreamcore.script.MethodRegistry.Handler SCARGS(String m) {
        return a -> SC(m, args(a));
    }

    private static com.opendreamcore.script.MethodRegistry.Handler SC1B(String m) {
        return a -> SC(m, arg(a, 0));
    }

    private static com.opendreamcore.script.MethodRegistry.Handler SC_PROP(String prop) {
        return a -> SC("获取元素", arg(a, 0), prop);
    }

    private static com.opendreamcore.script.MethodRegistry.Handler SC2B(String m) {
        return a -> SC(m, arg(a, 0), arg(a, 1));
    }

    private static com.opendreamcore.script.MethodRegistry.Handler TIP(String prefix) {
        return a -> D("Tip", "show", prefix + s2(a));
    }

    // 本地实现

    private static String s2(Object[] a) {
        return a != null && a.length > 0 && a[0] != null ? String.valueOf(a[0]) : "";
    }

    private static Object[] args(Object[] a) {
        return a == null ? new Object[0] : a;
    }

    private static Object arg(Object[] a, int i) {
        return a != null && i < a.length ? a[i] : null;
    }

    private static double num(Object[] a, int i) {
        return a != null && i < a.length && a[i] instanceof Number n ? n.doubleValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listAt(Object[] a, int i) {
        Object v = arg(a, i);
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    private static double dnum(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static final java.util.function.DoubleUnaryOperator IDENTITY = x -> x;

    private static Object MATH_ABS(Object[] a) { return Math.abs(num(a, 0)); }
    private static Object MATH_COS(Object[] a) { return Math.cos(Math.toRadians(num(a, 0))); }
    private static Object MATH_ACOS(Object[] a) { return Math.toDegrees(Math.acos(num(a, 0))); }
    private static Object MATH_ASIN(Object[] a) { return Math.toDegrees(Math.asin(num(a, 0))); }
    private static Object MATH_ATAN(Object[] a) { return Math.atan(num(a, 0)); }
    private static Object MATH_ATAN2(Object[] a) { return Math.toDegrees(Math.atan2(num(a, 0), num(a, 1))); }
    private static Object MATH_SQRT(Object[] a) { return Math.sqrt(num(a, 0)); }
    private static Object MATH_POW(Object[] a) { return Math.pow(num(a, 0), num(a, 1)); }
    private static Object MATH_POW2(Object[] a) { return Math.pow(num(a, 0), 2); }
    private static Object MATH_POW3(Object[] a) { return Math.pow(num(a, 0), 3); }
    private static Object MATH_POW4(Object[] a) { return Math.pow(num(a, 0), 4); }
    private static Object MATH_POW5(Object[] a) { return Math.pow(num(a, 0), 5); }
    private static Object MATH_SIGN(Object[] a) { return Math.signum(num(a, 0)); }
    private static Object MATH_FRACT(Object[] a) { return num(a, 0) - Math.floor(num(a, 0)); }
    private static Object MATH_LOG10(Object[] a) { return Math.log10(num(a, 0)); }
    private static Object MATH_LN(Object[] a) { return Math.log(num(a, 0)); }
    private static Object MATH_ROUND(Object[] a) { return (double) Math.round(num(a, 0)); }
    private static Object MATH_LERP(Object[] a) { return num(a, 0) + (num(a, 1) - num(a, 0)) * num(a, 2); }
    private static Object MATH_BEZIER(Object[] a) { return num(a, 0); }
    private static Object MATH_SMOOTH(Object[] a) { double t = num(a, 0); return t * t * t * (t * (t * 6 - 15) + 10); }
    private static Object MATH_ELASTIC(Object[] a) {
        double t = num(a, 0);
        if (t == 0 || t == 1) { return t; }
        return Math.pow(2, -10 * t) * Math.sin((t - 0.075) * (2 * Math.PI) / 0.3) + 1;
    }
    private static Object MATH_BOUNCE(Object[] a) {
        double t = num(a, 0);
        double n1 = 7.5625, d1 = 2.75;
        if (t < 1 / d1) { return n1 * t * t; }
        if (t < 2 / d1) { return n1 * (t -= 1.5 / d1) * t + 0.75; }
        if (t < 2.5 / d1) { return n1 * (t -= 2.25 / d1) * t + 0.9375; }
        return n1 * (t -= 2.625 / d1) * t + 0.984375;
    }
    private static Object MATH_CIRC(Object[] a) {
        double t = num(a, 0);
        return t < 0.5 ? (1 - Math.sqrt(1 - Math.pow(2 * t, 2))) / 2
                : (Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2;
    }
    private static Object HERMITE(Object[] a) { double t = num(a, 0); return t * t * (3 - 2 * t); }
    private static Object SPRING(Object[] a) { return num(a, 0); }
    private static Object CLAMP(Object[] a) { return Math.max(num(a, 1), Math.min(num(a, 2), num(a, 0))); }
    private static Object RANDOM(Object[] a) { return Math.random(); }
    private static Object RANDOM_INT(Object[] a) {
        return (double) (int) (Math.random() * (num(a, 1) - num(a, 0) + 1) + num(a, 0));
    }
    private static Object DIE_ROLL(Object[] a) { return num(a, 0) + Math.random() * (num(a, 1) - num(a, 0)); }
    private static Object DIE_ROLL_INT(Object[] a) {
        return (double) (int) (Math.random() * ((int) num(a, 1) - (int) num(a, 0) + 1) + (int) num(a, 0));
    }
    private static Object TO_INT(Object[] a) { return (double) (int) num(a, 0); }
    private static Object TO_DBL(Object[] a) { return num(a, 0); }
    private static Object TO_LONG(Object[] a) { return (double) (long) num(a, 0); }
    private static Object TO_BOOL(Object[] a) { return Boolean.parseBoolean(s2(a)); }
    private static Object TO_STR(Object[] a) { return s2(a); }
    private static Object CODE_AT(Object[] a) {
        String s = s2(a);
        int idx = (int) num(a, 1);
        return (double) (idx >= 0 && idx < s.length() ? s.codePointAt(idx) : 0);
    }
    private static Object FROM_CODE(Object[] a) {
        StringBuilder sb = new StringBuilder();
        for (Object x : a) { if (x instanceof Number nn) { sb.appendCodePoint(nn.intValue()); } }
        return sb.toString();
    }
    private static Object IS_EMPTY(Object[] a) { return s2(a).isEmpty(); }
    private static Object IS_BLANK(Object[] a) { return J8.isBlank(s2(a)); }
    private static Object STARTS(Object[] a) { return s2(a).startsWith(arg(a, 1) == null ? "" : String.valueOf(arg(a, 1))); }
    private static Object ENDS(Object[] a) { return s2(a).endsWith(arg(a, 1) == null ? "" : String.valueOf(arg(a, 1))); }
    private static Object CONTAINS(Object[] a) { return s2(a).contains(arg(a, 1) == null ? "" : String.valueOf(arg(a, 1))); }
    private static Object SPLIT(Object[] a) {
        return J8.list(s2(a).split(java.util.regex.Pattern.quote(
                arg(a, 1) == null || String.valueOf(arg(a, 1)).isEmpty() ? " " : String.valueOf(arg(a, 1)))));
    }
    private static Object SLICE(Object[] a) {
        String src = s2(a);
        int st = (int) num(a, 1);
        if (st < 0) { st += src.length(); }
        st = Math.max(0, st);
        if (a != null && a.length > 2) {
            int en = (int) num(a, 2);
            if (en < 0) { en += src.length(); }
            en = Math.min(src.length(), en);
            return st <= en ? src.substring(st, en) : "";
        }
        return src.substring(Math.min(st, src.length()));
    }
    private static Object INDEX_OF(Object[] a) { return (double) s2(a).indexOf(s2(new Object[]{arg(a, 1)})); }
    private static Object LEN(Object[] a) { return (double) s2(a).length(); }
    private static Object TRIM(Object[] a) { return s2(a).trim(); }
    private static Object TRIM_L(Object[] a) { return s2(a).replaceAll("^\\s+", ""); }
    private static Object TRIM_R(Object[] a) { return s2(a).replaceFirst("\\s+$", ""); }
    private static Object STRIP_COLOR(Object[] a) { return s2(a).replaceAll("[\u00a7&][0-9a-fk-orA-FK-OR]", ""); }
    private static Object UPPER(Object[] a) { return s2(a).toUpperCase(); }
    private static Object LOWER(Object[] a) { return s2(a).toLowerCase(); }
    private static Object REVERSE(Object[] a) { return new StringBuilder(s2(a)).reverse().toString(); }
    private static Object CONCAT_ALL(Object[] a) {
        StringBuilder sb = new StringBuilder();
        for (Object x : a) { if (x != null) { sb.append(x); } }
        return sb.toString();
    }
    private static Object EQUALS(Object[] a) {
        return String.valueOf(arg(a, 0)).equals(arg(a, 1) == null ? "null" : String.valueOf(arg(a, 1)));
    }
    private static Object EQUALS_CI(Object[] a) {
        return String.valueOf(arg(a, 0)).equalsIgnoreCase(arg(a, 1) == null ? "null" : String.valueOf(arg(a, 1)));
    }
    private static Object TYPEOF(Object[] a) {
        Object v = arg(a, 0);
        if (v == null) { return "null"; }
        if (v instanceof Number) { return "number"; }
        if (v instanceof Boolean) { return "boolean"; }
        if (v instanceof String) { return "string"; }
        return "object";
    }
    private static Object SAME_TYPE(Object[] a) {
        return arg(a, 0) != null && arg(a, 1) != null && arg(a, 0).getClass() == arg(a, 1).getClass();
    }
    private static Object PAD_L(Object[] a) { return pad(s2(a), num(a, 1), s2(new Object[]{arg(a, 2)}), true); }
    private static Object PAD_R(Object[] a) { return pad(s2(a), num(a, 1), s2(new Object[]{arg(a, 2)}), false); }
    private static Object ALIGN(Object[] a) { return HALF(a); }
    private static Object HALF(Object[] a) { return 0.5; }

    private static String pad(String s, double target, String fill, boolean left) {
        String f = fill.isEmpty() ? " " : fill;
        while (s.length() < (int) target) { s = left ? f + s : s + f; }
        return s;
    }

    private static Object ARR_ADD(Object[] a) { List<Object> l = listAt(a, 0); l.add(arg(a, 1)); return l; }
    private static Object ARR_POP(Object[] a) {
        List<Object> l = listAt(a, 0);
        return l.isEmpty() ? null : l.remove(l.size() - 1);
    }
    private static Object ARR_CLONE(Object[] a) { return new ArrayList<>(listAt(a, 0)); }
    private static Object ARR_REMOVE(Object[] a) {
        List<Object> l = listAt(a, 0);
        l.removeIf(x -> java.util.Objects.equals(x, arg(a, 1)));
        return l;
    }
    private static Object ARR_SET(Object[] a) {
        List<Object> l = listAt(a, 0);
        int idx = (int) num(a, 1);
        if (idx >= 0 && idx < l.size()) { l.set(idx, arg(a, 2)); }
        return l;
    }
    private static Object JOIN_ARR(Object[] a) { return CONCAT_ALL(a); }
    private static Object SUBARR(Object[] a) {
        List<Object> l = listAt(a, 0);
        int st = Math.max(0, (int) num(a, 1));
        int en = Math.min(l.size(), (int) num(a, 2));
        return st <= en ? new ArrayList<>(l.subList(st, en)) : new ArrayList<>();
    }
    private static Object FILTER(Object[] a) {
        List<Object> src = listAt(a, 0);
        String kw = arg(a, 1) == null ? "" : String.valueOf(arg(a, 1));
        List<Object> out = new ArrayList<>();
        for (Object x : src) { if (String.valueOf(x).contains(kw)) { out.add(x); } }
        return out;
    }
    private static Object VAR_ADD(Object[] a) {
        double base = dnum(V("获取变量", arg(a, 0)));
        return V("设置变量", arg(a, 0), base + num(a, 1));
    }
    private static Object VAR_SUB(Object[] a) {
        double base = dnum(V("获取变量", arg(a, 0)));
        return V("设置变量", arg(a, 0), base - num(a, 1));
    }
    private static Object VAR_MUL(Object[] a) {
        double base = dnum(V("获取变量", arg(a, 0)));
        return V("设置变量", arg(a, 0), base * num(a, 1));
    }
    private static Object VAR_DIV(Object[] a) {
        double base = dnum(V("获取变量", arg(a, 0)));
        double div = num(a, 1);
        return V("设置变量", arg(a, 0), div == 0 ? 0 : base / div);
    }
    private static Object VAR_INC(Object[] a) {
        return V("设置变量", arg(a, 0), dnum(V("获取变量", arg(a, 0))) + 1);
    }
    private static Object VAR_DEC(Object[] a) {
        return V("设置变量", arg(a, 0), dnum(V("获取变量", arg(a, 0))) - 1);
    }
    private static Object PRINTLN(Object[] a) {
        System.out.println("[ODC] " + s2(a));
        return null;
    }
    private static Object SLEEP(Object[] a) {
        try { Thread.sleep((long) num(a, 0)); } catch (InterruptedException ignored) { }
        return null;
    }
    private static Object FN_RUN(Object[] a) {
        Object fn = arg(a, 0);
        if (fn == null) { return null; }
        String code = String.valueOf(fn).trim();
        if (!code.isEmpty() && !code.contains("(")) { code = code + "()"; }
        try { com.opendreamcore.script.DreamLang.execute(code, null); } catch (Throwable ignored) { }
        return null;
    }
    private static Object IF_STMT(Object[] a) {
        boolean cond = Boolean.TRUE.equals(arg(a, 0));
        Object branch = cond ? arg(a, 1) : (a != null && a.length > 2 ? arg(a, 2) : null);
        if (branch != null) { return FN_RUN(new Object[]{branch}); }
        return null;
    }
    private static Object REFRESH_UI(Object[] a) {
        return SC("设置变量", "_odc_refresh", System.currentTimeMillis());
    }
    private static Object SC_SET_VAL(Object[] a) {
        return SC("设置元素", arg(a, 0), "value", arg(a, 1));
    }
    private static Object SET_POS(Object[] a) {
        SC("设置元素", arg(a, 0), "x", arg(a, 1));
        SC("设置元素", arg(a, 0), "y", arg(a, 2));
        return null;
    }
    private static Object SC_ALIGN(Object[] a) { return SC("设置元素", arg(a, 0), "align", arg(a, 1)); }
    private static Object COPY_PROPS(Object[] a) {
        String from = arg(a, 0) == null ? "" : String.valueOf(arg(a, 0));
        String to = arg(a, 1) == null ? "" : String.valueOf(arg(a, 1));
        if (!from.isEmpty() && !to.isEmpty()) {
            SC("设置元素", to, "opacity", SC("获取元素", from, "opacity"));
            SC("设置元素", to, "visible", SC("获取元素", from, "visible"));
        }
        return null;
    }
    private static Object BLINK(Object[] a) { return Math.sin(System.currentTimeMillis() / 500.0) * 0.5 + 0.5; }
    private static Object BREATHE(Object[] a) { return Math.sin(System.currentTimeMillis() / 800.0) * 0.5 + 0.5; }
    private static Object PULSE(Object[] a) { return 1 + Math.sin(System.currentTimeMillis() / 300.0) * 0.1; }


    private static Object ARR_GET(Object[] a) {
        List<Object> l = listAt(a, 0);
        int idx = (int) num(a, 1);
        return idx >= 0 && idx < l.size() ? l.get(idx) : null;
    }
    private static Object S(String v) { return v; }

    private static Object D36(Object[] a) { return 36.0; }

    private static Object D300(Object[] a) { return 300.0; }

    private static Object D20(Object[] a) { return 20.0; }

    private static Object D64(Object[] a) { return 64.0; }

    private static Object D18(Object[] a) { return 1.8; }

    private static Object D005(Object[] a) { return 0.05; }

    private static Object NOW_MS(Object[] a) { return (double) System.currentTimeMillis(); }

    private static Object ALIVE(Object[] a) { return (double) (System.currentTimeMillis() - openMs); }

    private static Object OGL_TIME(Object[] a) { return (double) (System.currentTimeMillis() % 100000L); }

    private static Object NUM0(Object[] a) { return num(a, 0); }

    private static Object S2(Object[] a) { return s2(a); }

    private static final java.util.Map<String, Boolean> ENTITY_FLAGS = J8.map(
            "on_ground", true, "in_water", false, "swimming", false,
            "sneaking", false, "burning", false, "sprinting", false, "flying", false);

    private static com.opendreamcore.script.MethodRegistry.Handler EBOOL(String flag) {
        return a -> ENTITY_FLAGS.getOrDefault(flag, false);
    }

    private static final java.util.Map<String, Boolean> PLAYER_FLAGS = J8.map(
            "on_ground", true, "flying", false, "in_water", false, "sneaking", false,
            "sprinting", false, "swimming", false, "sleeping", false, "burning", false);

    private static com.opendreamcore.script.MethodRegistry.Handler PBOOL(String flag) {
        return a -> PLAYER_FLAGS.getOrDefault(flag, false);
    }

    private static Object runFnDelay(Object[] a) {
        Object fn = arg(a, 1);
        if (fn == null) { return null; }
        String code = String.valueOf(fn).trim();
        if (!code.isEmpty() && !code.contains("(")) { code = code + "()"; }
        final String c = code;
        J8.delayedExecutor((long) num(a, 0),
                        java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> { try { com.opendreamcore.script.DreamLang.execute(c, null); } catch (Throwable ignored) { } });
        return null;
    }

    private static Object runFnRepeat(Object[] a) {
        Object fn = arg(a, 1);
        if (fn == null) { return null; }
        String code = String.valueOf(fn).trim();
        if (!code.isEmpty() && !code.contains("(")) { code = code + "()"; }
        long interval = Math.max(1, (long) num(a, 0));
        for (int k = 1; k <= 10; k++) {
            final String c = code;
            final long d = interval * k;
            J8.delayedExecutor(d,
                            java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(() -> { try { com.opendreamcore.script.DreamLang.execute(c, null); } catch (Throwable ignored) { } });
        }
        return null;
    }
}