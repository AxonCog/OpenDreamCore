package com.opendreamcore.client.screen;

import com.opendreamcore.client.ClientController;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 屏幕编辑器撤销/重做历史（C4 自 OdcScreen 抽出）：
 * 原子 = ClientController.elementEditsSnapshot 全量快照（按页面 id 存取），栈深 64。
 * 线程约定：仅渲染线程调用（与编辑交互同线程）。
 */
public final class EditHistory {

    /** 栈深上限。 */
    public static final int MAX_DEPTH = 64;

    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();

    /** 记录当前状态到撤销栈（清空重做栈）。 */
    public void push(String pageId) {
        try {
            String snap = ClientController.get().elementEditsSnapshot(pageId);
            undo.push(snap);
            if (undo.size() > MAX_DEPTH) {
                undo.removeLast();
            }
            redo.clear();
        } catch (Exception ignored) {
        }
    }

    /** 撤销：恢复上一快照并刷新。 */
    public void undo(String pageId) {
        if (undo.isEmpty()) {
            return;
        }
        try {
            String cur = ClientController.get().elementEditsSnapshot(pageId);
            redo.push(cur);
            String prev = undo.pop();
            ClientController.get().restoreElementEdits(pageId, prev);
            ClientController.get().refreshCurrent();
        } catch (Exception ignored) {
        }
    }

    /** 重做：恢复下一快照并刷新。 */
    public void redo(String pageId) {
        if (redo.isEmpty()) {
            return;
        }
        try {
            String cur = ClientController.get().elementEditsSnapshot(pageId);
            undo.push(cur);
            String next = redo.pop();
            ClientController.get().restoreElementEdits(pageId, next);
            ClientController.get().refreshCurrent();
        } catch (Exception ignored) {
        }
    }
}
