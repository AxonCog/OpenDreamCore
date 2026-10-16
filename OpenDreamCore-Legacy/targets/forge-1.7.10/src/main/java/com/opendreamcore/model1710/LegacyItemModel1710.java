package com.opendreamcore.model1710;

import com.opendreamcore.client.model.LegacyItemModelRuleResolver;
import com.opendreamcore.client.model.LegacyRigRenderer;
import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.BakedRig;
import com.opendreamcore.visual.ItemView;
import net.minecraft.item.Item;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real ItemModel consumer for 1.7.10. Icon owners call tryGui; item render hooks call tryWorld. */
public final class LegacyItemModel1710 {
    private LegacyItemModel1710() { }

    public static boolean tryGui(Object gui, ItemStack stack, int x, int y) { return tryGui(stack, x, y); }
    public static boolean tryGui(ItemStack stack, int x, int y) {
        LegacyItemModelRuleResolver.Hit hit = resolve(stack);
        if (!ready(hit)) return false;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x + 8.0F, y + 8.0F, 100.0F);
            GL11.glScalef((float) (hit.scale * 16.0F), (float) (hit.scale * 16.0F), (float) (hit.scale * 16.0F));
            draw(hit, stack);
            return true;
        } finally { GL11.glPopMatrix(); }
    }

    /** Draws in the caller's item-world transform, replacing vanilla only on a complete hit. */
    public static boolean tryWorld(ItemStack stack) {
        LegacyItemModelRuleResolver.Hit hit = resolve(stack);
        if (!ready(hit)) return false;
        GL11.glPushMatrix();
        try {
            GL11.glScalef((float) hit.scale, (float) hit.scale, (float) hit.scale);
            draw(hit, stack);
            return true;
        } finally { GL11.glPopMatrix(); }
    }

    private static LegacyItemModelRuleResolver.Hit resolve(final ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || stack.getItem() == null) return null;
        Object id = Item.itemRegistry.getNameForObject(stack.getItem());
        final String type = id == null ? "" : id.toString();
        return LegacyItemModelRuleResolver.resolve(type, view(stack, type));
    }

    private static ItemView view(ItemStack stack, String type) {
        String name = stack.getDisplayName();
        NBTTagCompound tag = stack.getTagCompound();
        List<String> lore = new ArrayList<String>();
        if (tag != null && tag.hasKey("display", 10)) {
            NBTTagCompound d = tag.getCompoundTag("display");
            if (d.hasKey("Lore", 9)) {
                NBTTagList lines = d.getTagList("Lore", 8);
                for (int i = 0; i < lines.tagCount(); i++) lore.add(lines.getStringTagAt(i));
            }
        }
        return new ItemView(type, name, lore, nbt(tag));
    }

    private static Map<String, Object> nbt(NBTTagCompound tag) {
        if (tag == null) return Collections.emptyMap();
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Object k : tag.func_150296_c()) {
            String key = String.valueOf(k);
            out.put(key, nbtValue(tag.getTag(key)));
        }
        return out;
    }

    private static Object nbtValue(Object v) {
        if (v instanceof NBTTagCompound) return nbt((NBTTagCompound) v);
        if (v instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) v;
            List<Object> out = new ArrayList<Object>();
            for (int i = 0; i < list.tagCount(); i++) out.add(String.valueOf(list.getStringTagAt(i)));
            return out;
        }
        return v == null ? null : v.toString();
    }

    private static boolean ready(LegacyItemModelRuleResolver.Hit hit) {
        if (hit == null || hit.baked == null || hit.baked.isEmpty()) return false;
        if (hit.textureOverride != null) return ModelRenderer1710.texture(hit.textureOverride) != null;
        if (hit.rig != null) {
            for (BakedRig.Bone b : hit.rig.bones) for (BakedRig.Part p : b.parts)
                if (ModelRenderer1710.texture(p.texture) == null) return false;
        } else for (BakedModel m : hit.baked)
            if (ModelRenderer1710.texture(m.texture) == null) return false;
        return true;
    }

    private static void draw(LegacyItemModelRuleResolver.Hit hit, ItemStack stack) {
        Minecraft mc = Minecraft.getMinecraft();
        if (hit.rig != null) {
            final Map<String, Sink> groups = new LinkedHashMap<String, Sink>();
            LegacyRigRenderer.render(UUID.nameUUIDFromBytes(("item:" + stack.getItem()).getBytes()),
                    hit.rig, hit.ruleAnims, "idle", false, false, 0.0F,
                    texture -> { Sink s = groups.get(texture); if (s == null) { s = new Sink(); groups.put(texture, s); } return s; });
            Tessellator t = Tessellator.instance;
            for (Map.Entry<String, Sink> e : groups.entrySet()) {
                ResourceLocation rl = ModelRenderer1710.texture(hit.textureOverride != null ? hit.textureOverride : e.getKey());
                if (rl == null) continue; mc.getTextureManager().bindTexture(rl); e.getValue().draw(t);
            }
            return;
        }
        Tessellator t = Tessellator.instance;
        for (BakedModel m : hit.baked) {
            ResourceLocation rl = ModelRenderer1710.texture(hit.textureOverride != null ? hit.textureOverride : m.texture);
            if (rl == null) return;
            mc.getTextureManager().bindTexture(rl);
            emit(t, m.quads, 4); emit(t, m.tris, 3);
        }
    }

    private static void emit(Tessellator t, float[] d, int n) {
        if (d == null || d.length < n * 8) return; t.startDrawing(n == 4 ? GL11.GL_QUADS : GL11.GL_TRIANGLES);
            for (int off = 0; off + n * 8 <= d.length; off += n * 8)
                for (int v = 0; v < n; v++) { int o = off + v * 8; t.addVertexWithUV(d[o] / 16.0D, d[o + 1] / 16.0D, d[o + 2] / 16.0D, d[o + 3], d[o + 4]); }
        t.draw();
    }
    private static final class Sink implements LegacyRigRenderer.VertexSink {
        float[] v = new float[128]; int n;
        public void vertex(float x,float y,float z,float u,float w,float nx,float ny,float nz) {
            if (n + 5 > v.length) { float[] q = new float[v.length * 2]; System.arraycopy(v,0,q,0,n); v=q; }
            v[n++]=x;v[n++]=y;v[n++]=z;v[n++]=u;v[n++]=w;
        }
        void draw(Tessellator t) { t.startDrawingQuads(); for(int i=0;i+4<n;i+=5)t.addVertexWithUV(v[i],v[i+1],v[i+2],v[i+3],v[i+4]); t.draw(); }
    }
}
