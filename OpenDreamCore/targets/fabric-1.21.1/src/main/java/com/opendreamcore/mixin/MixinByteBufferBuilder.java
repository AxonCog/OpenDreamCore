package com.opendreamcore.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 诊断：ByteBufferBuilder.clear() 时若还有未消费批次（resultCount>0）就打印
 * "Clearing BufferBuilder with unused batches" 的确切触发点。节流 5 秒只打一次，
 * 调用栈直接指明是哪个渲染路径在帧末留了未绘制/未上传的 batch（定位轮空刷屏）。
 */
@Mixin(com.mojang.blaze3d.vertex.ByteBufferBuilder.class)
public abstract class MixinByteBufferBuilder {

    @Shadow
    private int resultCount;

    private static volatile long odc$lastLog;

    @Inject(method = "clear", at = @At("HEAD"))
    private void odc$logClear(CallbackInfo ci) {
        odc$log("clear");
    }

    /** discard 也打印：1.21.1 的 "Clearing BufferBuilder" WARN 实际在 discard() 里。 */
    @Inject(method = "discard", at = @At("HEAD"))
    private void odc$logDiscard(CallbackInfo ci) {
        odc$log("discard");
    }

    private void odc$log(String what) {
        try {
            long now = System.currentTimeMillis();
            if (now - odc$lastLog < 4000L) {
                return;
            }
            odc$lastLog = now;
            StringBuilder sb = new StringBuilder();
            sb.append("\n[ODC-diag] ByteBufferBuilder.").append(what)
                    .append(" 调用 resultCount=").append(resultCount)
                    .append(" 调用栈:\n");
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            for (StackTraceElement e : st) {
                if (sb.length() > 1200) {
                    break;
                }
                sb.append("    at ").append(e).append('\n');
            }
            System.out.print(sb);
        } catch (Throwable ignored) {
        }
    }
}