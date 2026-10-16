package com.opendreamcore.ui;

import com.opendreamcore.page.Page;
import com.opendreamcore.script.MethodRegistry;
import com.opendreamcore.script.engine.DreamLang;
import com.opendreamcore.script.engine.DreamLangExecutor;
import com.opendreamcore.script.engine.Scope;

import java.util.LinkedHashMap;
import java.util.Map;

/** Page methods resolve against the calling scope, including this layout pass's frames. */
public final class PageFunctions {
    private PageFunctions() { }

    public static void bind(Page page, Scope scope, Object currentComponent) {
        Namespace methods = new Namespace(page, scope);
        scope.declare("方法", methods);
        scope.declare("当前组件", currentComponent);
        // DragonCore's rewrite also accepts registered methods without 方法.
        for (String name : LOCAL_METHODS) scope.declare(name, methods.member(name));
    }

    private static final String[] LOCAL_METHODS = {
            "执行方法", "取yaml值", "取Yaml值", "取yamlValue", "取组件值",
            "取屏幕宽度", "取屏幕高度"
    };

    private static final class Namespace implements DreamLangExecutor.Callable {
        private final Page page;
        private final Scope boundScope;
        private final Map<String, DreamLangExecutor.Callable> methods = new LinkedHashMap<>();

        private Namespace(Page page, Scope scope) {
            this.page = page;
            this.boundScope = scope;
        }

        @Override
        public Object call(Object[] args) {
            throw new DreamLangExecutor.ScriptException("方法 是命名空间，需要 .方法名(...)");
        }

        @Override
        public Object member(String name) {
            return methods.computeIfAbsent(name, key -> args -> invoke(key, args));
        }

        private Scope caller() {
            Scope current = DreamLang.currentScope();
            return current == null ? boundScope : current;
        }

        private Object invoke(String name, Object[] args) {
            Scope caller = caller();
            if ("执行方法".equals(name)) {
                String function = args.length == 0 ? null : String.valueOf(args[0]);
                String script = page.functions().get(function);
                if (script == null) {
                    throw new DreamLangExecutor.ScriptException("页面函数不存在: " + function);
                }
                Scope child = caller.child();
                Map<String, Object> parameters = new LinkedHashMap<>();
                for (int i = 1; i < args.length; i++) parameters.put(String.valueOf(i - 1), args[i]);
                child.declare("参数", parameters);
                Object component = caller.resolve("局部变量", "当前组件");
                if (component == null) component = caller.resolve("当前组件");
                child.declare("当前组件", component);
                return DreamLang.call(script, child);
            }
            if (("取yaml值".equals(name) || "取Yaml值".equals(name) || "取yamlValue".equals(name))
                    && args.length == 1) {
                return pageValue(String.valueOf(args[0]));
            }
            if ("取组件值".equals(name) && args.length >= 2) {
                Object component = caller.resolve(String.valueOf(args[0]));
                Object value = memberOf(component, String.valueOf(args[1]));
                if (value != null) return value;
            }
            if ("取屏幕宽度".equals(name)) return caller.resolve("window", "width");
            if ("取屏幕高度".equals(name)) return caller.resolve("window", "height");
            return MethodRegistry.require(name).invoke(args);
        }

        private Object pageValue(String path) {
            Object value;
            if (page.variables().containsKey(path)) value = page.variables().get(path);
            else if (page.options().containsKey(path)) value = page.options().get(path);
            else {
                String[] parts = path.split("\\.");
                value = page.variables().get(parts[0]);
                if (value == null) value = page.options().get(parts[0]);
                for (int i = 1; i < parts.length; i++) value = memberOf(value, parts[i]);
            }
            // YAML frequently quotes numeric values; arithmetic still sees a number.
            if (value instanceof String text) {
                try { return Double.valueOf(text.trim()); }
                catch (NumberFormatException ignored) { }
            }
            return value;
        }
    }

    private static Object memberOf(Object target, String key) {
        if (target instanceof Map<?, ?> map) return map.get(key);
        if (target instanceof DreamLangExecutor.MapLike map) return map.get(key);
        return null;
    }
}
