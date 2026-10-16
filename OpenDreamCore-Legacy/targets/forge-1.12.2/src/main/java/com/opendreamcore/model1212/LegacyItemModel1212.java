package com.opendreamcore.model1212;

import com.opendreamcore.client.model.LegacyItemModelRuleResolver;
import com.opendreamcore.client.model.LegacyRigRenderer;
import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.BakedRig;
import com.opendreamcore.visual.ItemView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
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

/** Real ItemModel consumer for 1.12.2; all paths return false for incomplete assets. */
public final class LegacyItemModel1212 {
    private LegacyItemModel1212() { }

    public static boolean tryGui(Object gui, ItemStack stack, int x, int y) { return tryGui(stack, x, y); }
    public static boolean tryGui(ItemStack stack, int x, int y) {
        LegacyItemModelRuleResolver.Hit hit = resolve(stack);
        if (!ready(hit)) return false;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 8.0, y + 8.0, 100.0);
            float s = (float) (hit.scale * 16.0F);
            GlStateManager.scale(s, s, s);
            draw(hit, stack);
            return true;
        } finally { GlStateManager.popMatrix(); }
    }

    public static boolean tryWorld(ItemStack stack) {
        LegacyItemModelRuleResolver.Hit hit = resolve(stack);
        if (!ready(hit)) return false;
        GlStateManager.pushMatrix();
        try { GlStateManager.scale((float) hit.scale, (float) hit.scale, (float) hit.scale); draw(hit, stack); return true; }
        finally { GlStateManager.popMatrix(); }
    }

    private static LegacyItemModelRuleResolver.Hit resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == null) return null;
        ResourceLocation id = stack.getItem().getRegistryName();
        String type = id == null ? "" : id.toString();
        return LegacyItemModelRuleResolver.resolve(type, view(stack, type));
    }
    private static ItemView view(ItemStack stack, String type) {
        String name = stack.getDisplayName(); NBTTagCompound tag = stack.getTagCompound();
        List<String> lore = new ArrayList<String>();
        if (tag != null && tag.hasKey("display", 10)) {
            NBTTagCompound d = tag.getCompoundTag("display");
            if (d.hasKey("Lore", 9)) { NBTTagList lines = d.getTagList("Lore", 8); for (int i=0;i<lines.tagCount();i++) lore.add(lines.getStringTagAt(i)); }
        }
        return new ItemView(type, name, lore, nbt(tag));
    }
    private static Map<String,Object> nbt(NBTTagCompound tag) {
        if (tag == null) return Collections.emptyMap(); Map<String,Object> out = new LinkedHashMap<String,Object>();
        for (String key : tag.getKeySet()) out.put(key, nbtValue(tag.getTag(key))); return out;
    }
    private static Object nbtValue(Object v) {
        if (v instanceof NBTTagCompound) return nbt((NBTTagCompound)v);
        if (v instanceof NBTTagList) { NBTTagList l=(NBTTagList)v; List<Object> o=new ArrayList<Object>(); for(int i=0;i<l.tagCount();i++)o.add(l.getStringTagAt(i)); return o; }
        return v == null ? null : v.toString();
    }
    private static boolean ready(LegacyItemModelRuleResolver.Hit h) {
        if (h == null || h.baked == null || h.baked.isEmpty()) return false;
        if (h.textureOverride != null) return ModelRenderer1212.texture(h.textureOverride) != null;
        if (h.rig != null) { for (BakedRig.Bone b:h.rig.bones) for(BakedRig.Part p:b.parts) if(ModelRenderer1212.texture(p.texture)==null)return false; }
        else for(BakedModel m:h.baked) if(ModelRenderer1212.texture(m.texture)==null)return false;
        return true;
    }
    private static void draw(LegacyItemModelRuleResolver.Hit h, ItemStack stack) {
        Minecraft mc=Minecraft.getMinecraft();
        if (h.rig != null) {
            final Map<String,Sink> groups=new LinkedHashMap<String,Sink>();
            LegacyRigRenderer.render(UUID.nameUUIDFromBytes(("item:"+stack.getItem()).getBytes()),h.rig,h.ruleAnims,"idle",false,false,0.0F,
                    texture->{Sink s=groups.get(texture);if(s==null){s=new Sink();groups.put(texture,s);}return s;});
            Tessellator t=Tessellator.getInstance(); for(Map.Entry<String,Sink> e:groups.entrySet()){ResourceLocation r=ModelRenderer1212.texture(h.textureOverride!=null?h.textureOverride:e.getKey());if(r==null)continue;mc.getTextureManager().bindTexture(r);e.getValue().draw(t);} return;
        }
        Tessellator t=Tessellator.getInstance(); for(BakedModel m:h.baked){ResourceLocation r=ModelRenderer1212.texture(h.textureOverride!=null?h.textureOverride:m.texture);if(r==null)return;mc.getTextureManager().bindTexture(r);emit(t,m.quads,4);emit(t,m.tris,3);}
    }
    private static void emit(Tessellator t,float[]d,int n){if(d==null||d.length<n*8)return;BufferBuilder b=t.getBuffer();b.begin(n==4?GL11.GL_QUADS:GL11.GL_TRIANGLES,DefaultVertexFormats.POSITION_TEX);for(int off=0;off+n*8<=d.length;off+=n*8)for(int v=0;v<n;v++){int o=off+v*8;b.pos(d[o]/16.0D,d[o+1]/16.0D,d[o+2]/16.0D).tex(d[o+3],d[o+4]).endVertex();}t.draw();}
    private static final class Sink implements LegacyRigRenderer.VertexSink {float[]v=new float[128];int n;public void vertex(float x,float y,float z,float u,float w,float nx,float ny,float nz){if(n+5>v.length){float[]q=new float[v.length*2];System.arraycopy(v,0,q,0,n);v=q;}v[n++]=x;v[n++]=y;v[n++]=z;v[n++]=u;v[n++]=w;}void draw(Tessellator t){BufferBuilder b=t.getBuffer();b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);for(int i=0;i+4<n;i+=5)b.pos(v[i],v[i+1],v[i+2]).tex(v[i+3],v[i+4]).endVertex();t.draw();}}
}
