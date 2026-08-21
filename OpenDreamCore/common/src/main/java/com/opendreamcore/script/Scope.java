package com.opendreamcore.script;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 脚本作用域：局部变量 + 命名空间变量（vars/global/player 前缀）。
 * 引用解析：vars.coin → 页面变量；global.xxx → 全局；player.xxx → 玩家占位符；
 * 裸标识符 → 局部（脚本内）优先，其次父作用域，再其次页面变量。
 * 函数/Lambda 调用用 child() 开子作用域（局部隔离，vars/global/player 共享）。
 */
public final class Scope {

    private final Scope parent;
    private final Map<String, Object> locals = new LinkedHashMap<>();
    private final Map<String, Object> vars;
    private final Map<String, Object> globals;
    private final Map<String, Object> players;

    public Scope() {
        this(null, new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    private Scope(Scope parent, Map<String, Object> vars, Map<String, Object> globals, Map<String, Object> players) {
        this.parent = parent;
        this.vars = vars;
        this.globals = globals;
        this.players = players;
    }

    /** 子作用域：局部全新，vars/global/player 与父共享（闭包可读父局部）。 */
    public Scope child() {
        return new Scope(this, vars, globals, players);
    }

    /** 解析引用路径（a.b.c）：首个段决定命名空间。 */
    public Object resolve(String... path) {
        if (path.length == 0) {
            return null;
        }
        String head = path[0];
        if ("vars".equals(head)) {
            return path.length == 1 ? vars : getDeep(vars, path, 1);
        }
        if ("global".equals(head)) {
            return path.length == 1 ? globals : getDeep(globals, path, 1);
        }
        if ("player".equals(head) || "papi".equals(head)) {
            return path.length == 1 ? players : getDeep(players, path, 1);
        }
        for (Scope s = this; s != null; s = s.parent) {
            if (s.locals.containsKey(head)) {
                return path.length == 1 ? s.locals.get(head) : getDeep(s.locals, path, 1);
            }
        }
        return path.length == 1 ? vars.get(head) : getDeep(vars, path, 1);
    }

    /**
     * 赋值（a.b.c=）：首段决定命名空间。
     * 闭包语义：名字已在某祖先作用域声明 → 写回那里（Lambda/函数内修改外部局部）；
     * 未声明 → 当前作用域新建。
     */
    public void assign(String name, Object value) {
        if ("vars".equals(name)) {
            throw new IllegalArgumentException("vars 是命名空间，不能直接赋值");
        }
        if ("global".equals(name) || "player".equals(name) || "papi".equals(name)) {
            throw new IllegalArgumentException(name + " 是命名空间，不能直接赋值");
        }
        for (Scope s = this; s != null; s = s.parent) {
            if (s.locals.containsKey(name)) {
                s.locals.put(name, value);
                return;
            }
        }
        locals.put(name, value);
    }

    /** 声明：一律在当前作用域新建（函数/Lambda 内 变量 x = ... 不覆盖外层）。 */
    public void declare(String name, Object value) {
        locals.put(name, value);
    }

    public void assignVar(String name, Object value) {
        vars.put(name, value);
    }

    public void assignGlobal(String name, Object value) {
        globals.put(name, value);
    }

    public void assignPlayer(String name, Object value) {
        players.put(name, value);
    }

    public Map<String, Object> vars() {
        return vars;
    }

    public Map<String, Object> globals() {
        return globals;
    }

    public Map<String, Object> players() {
        return players;
    }

    private static Object getDeep(Map<String, Object> map, String[] path, int from) {
        Object cur = map.get(path[from]);
        for (int i = from + 1; i < path.length && cur instanceof Map<?, ?> m; i++) {
            cur = m.get(path[i]);
        }
        return cur;
    }
}
