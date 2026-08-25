package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

import java.util.ArrayList;
import java.util.List;

public final class CoreArrayLegacy {
    private CoreArrayLegacy() { }

    private static double num(Object[] a, int i) {
        return a != null && i < a.length && a[i] instanceof Number n ? n.doubleValue() : 0;
    }

    private static Object arg(Object[] a, int i) {
        return a != null && i < a.length ? a[i] : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object[] a, int i) {
        Object v = arg(a, i);
        if (v instanceof List) return (List<Object>) v;
        return new ArrayList<>();
    }

    public static void install() {
        LegacyMethods.register("array_create", a -> {
            List<Object> l = new ArrayList<>();
            for (Object x : a) if (x != null) l.add(x);
            return l;
        });
        LegacyMethods.register("create_array", a -> new ArrayList<>());
        LegacyMethods.register("array_add", a -> {
            List<Object> l = list(a, 0);
            l.add(arg(a, 1));
            return l;
        });
        LegacyMethods.register("array_get", a -> {
            List<Object> l = list(a, 0);
            int idx = (int) num(a, 1);
            return idx >= 0 && idx < l.size() ? l.get(idx) : null;
        });
        LegacyMethods.register("array_set", a -> {
            List<Object> l = list(a, 0);
            int idx = (int) num(a, 1);
            if (idx >= 0 && idx < l.size()) l.set(idx, arg(a, 2));
            return l;
        });
        LegacyMethods.register("array_insert", a -> {
            List<Object> l = list(a, 0);
            int idx = Math.min((int) num(a, 1), l.size());
            l.add(idx < 0 ? 0 : idx, arg(a, 2));
            return l;
        });
        LegacyMethods.register("array_remove", a -> {
            List<Object> l = list(a, 0);
            Object target = arg(a, 1);
            l.removeIf(x -> java.util.Objects.equals(x, target));
            return l;
        });
        LegacyMethods.register("array_replace", a -> {
            List<Object> l = list(a, 0);
            Object from = arg(a, 1), to = arg(a, 2);
            for (int k = 0; k < l.size(); k++)
                if (java.util.Objects.equals(l.get(k), from)) l.set(k, to);
            return l;
        });
        LegacyMethods.register("array_length", a -> (double) list(a, 0).size());
        LegacyMethods.register("array_size", a -> (double) list(a, 0).size());
        LegacyMethods.register("array_sub", a -> {
            List<Object> l = list(a, 0);
            int st = Math.max(0, (int) num(a, 1));
            int en = Math.min(l.size(), (int) num(a, 2));
            return st <= en ? new ArrayList<>(l.subList(st, en)) : new ArrayList<>();
        });
        LegacyMethods.register("push", a -> {
            List<Object> l = list(a, 0);
            l.add(arg(a, 1));
            return l;
        });
        LegacyMethods.register("pop", a -> {
            List<Object> l = list(a, 0);
            return l.isEmpty() ? null : l.remove(l.size() - 1);
        });
        LegacyMethods.register("keys", a -> new ArrayList<>(list(a, 0)));
        LegacyMethods.register("values", a -> new ArrayList<>(list(a, 0)));
        LegacyMethods.register("clone", a -> new ArrayList<>(list(a, 0)));
        LegacyMethods.register("sort", a -> {
            List<Object> l = list(a, 0);
            l.sort((x, y) -> {
                if (x instanceof Number nx && y instanceof Number ny)
                    return Double.compare(nx.doubleValue(), ny.doubleValue());
                return String.valueOf(x).compareTo(String.valueOf(y));
            });
            return l;
        });
        LegacyMethods.register("filter", a -> {
            List<Object> src = list(a, 0);
            String keyword = a != null && a.length > 1 && a[1] != null ? String.valueOf(a[1]) : "";
            List<Object> out = new ArrayList<>();
            for (Object x : src) if (String.valueOf(x).contains(keyword)) out.add(x);
            return out;
        });
        LegacyMethods.register("map", a -> list(a, 0));
        LegacyMethods.register("for_each", a -> null);
        LegacyMethods.register("for_each_simple", a -> null);
    }
}
