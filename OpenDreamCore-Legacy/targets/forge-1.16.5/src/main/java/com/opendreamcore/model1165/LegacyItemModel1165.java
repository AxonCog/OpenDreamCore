package com.opendreamcore.model1165;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.opendreamcore.client.model.LegacyItemModelRuleResolver;
import com.opendreamcore.client.model.LegacyRigRenderer;
import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.BakedRig;
import com.opendreamcore.visual.ItemView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix3f;
import net.minecraft.util.math.vector.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real 1.16.5 ItemModel mesh consumer. */
public final class LegacyItemModel1165 {
    private LegacyItemModel1165() { }

    public static boolean tryGui(Object gui, ItemStack stack, int x, int y) { return tryGui(stack, x, y); }
    public static boolean tryGui(ItemStack stack, int x, int y) {
        LegacyItemModelRuleResolver.Hit hit = resolve(stack);
        if (!ready(hit)) return false;
        MatrixStack ms = new MatrixStack(); ms.translate(x + 8.0, y + 8.0, 100.0);
        ms.scale((float) (hit.scale * 16.0F), (float) (hit.scale * 16.0F), (float) (hit.scale * 16.0F));
        IRenderTypeBuffer.Impl out = Minecraft.getInstance().renderBuffers().bufferSource();
        draw(hit, stack, ms, out, 0xF000F0); out.endBatch(); return true;
    }

    public static boolean tryWorld(ItemStack stack, MatrixStack ms, IRenderTypeBuffer out, int light, int overlay) {
        LegacyItemModelRuleResolver.Hit hit = resolve(stack);
        if (!ready(hit) || ms == null || out == null) return false;
        ms.pushPose(); try { float s = (float) hit.scale; ms.scale(s, s, s); draw(hit, stack, ms, out, light); return true; } finally { ms.popPose(); }
    }

    private static LegacyItemModelRuleResolver.Hit resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation id = stack.getItem().getRegistryName(); String type=id==null?"":id.toString();
        return LegacyItemModelRuleResolver.resolve(type, view(stack,type));
    }
    private static ItemView view(ItemStack stack,String type){
        String name=stack.getDisplayName().getString(); CompoundNBT tag=stack.getTag(); List<String> lore=new ArrayList<String>();
        if(tag!=null&&tag.contains("display",10)){CompoundNBT d=tag.getCompound("display");if(d.contains("Lore",9)){ListNBT l=d.getList("Lore",8);for(int i=0;i<l.size();i++)lore.add(l.getString(i));}}
        return new ItemView(type,name,lore,nbt(tag));
    }
    private static Map<String,Object> nbt(CompoundNBT tag){if(tag==null)return Collections.emptyMap();Map<String,Object>o=new LinkedHashMap<String,Object>();for(String k:tag.getAllKeys())o.put(k,nbtValue(tag.get(k)));return o;}
    private static Object nbtValue(Object v){if(v instanceof CompoundNBT)return nbt((CompoundNBT)v);if(v instanceof ListNBT){ListNBT l=(ListNBT)v;List<Object>o=new ArrayList<Object>();for(int i=0;i<l.size();i++)o.add(l.getString(i));return o;}return v==null?null:v.toString();}
    private static boolean ready(LegacyItemModelRuleResolver.Hit h){if(h==null||h.baked==null||h.baked.isEmpty())return false;if(h.textureOverride!=null)return ModelRenderer1165.texture(h.textureOverride)!=null;if(h.rig!=null){for(BakedRig.Bone b:h.rig.bones)for(BakedRig.Part p:b.parts)if(ModelRenderer1165.texture(p.texture)==null)return false;}else for(BakedModel m:h.baked)if(ModelRenderer1165.texture(m.texture)==null)return false;return true;}
    private static void draw(LegacyItemModelRuleResolver.Hit h,ItemStack stack,MatrixStack ms,IRenderTypeBuffer out,int light){Matrix4f mat=ms.last().pose();Matrix3f nrm=ms.last().normal();if(h.rig!=null){final Matrix4f fm=mat;final Matrix3f fn=nrm;LegacyRigRenderer.render(UUID.nameUUIDFromBytes(("item:"+stack.getItem()).getBytes()),h.rig,h.ruleAnims,"idle",false,false,0F,texture->{ResourceLocation r=ModelRenderer1165.texture(h.textureOverride!=null?h.textureOverride:texture);if(r==null)return null;IVertexBuilder b=out.getBuffer(RenderType.entityCutout(r));return (x,y,z,u,v,nx,ny,nz)->emit(b,fm,fn,x,y,z,u,v,nx,ny,nz,light);});return;}for(BakedModel m:h.baked){ResourceLocation r=ModelRenderer1165.texture(h.textureOverride!=null?h.textureOverride:m.texture);if(r==null)return;IVertexBuilder b=out.getBuffer(RenderType.entityCutout(r));emit(b,mat,nrm,m.quads,4,light);emit(b,mat,nrm,m.tris,3,light);}}
    private static void emit(IVertexBuilder b,Matrix4f m,Matrix3f n,float[]d,int count,int light){if(d==null)return;for(int off=0;off+count*8<=d.length;off+=count*8)for(int v=0;v<count;v++){int o=off+v*8;int idx=(v==3&&count==3)?2:v;int q=off+idx*8;emit(b,m,n,d[q]/16.0F,d[q+1]/16.0F,d[q+2]/16.0F,d[q+3],d[q+4],d[q+5],d[q+6],d[q+7],light);}}
    private static void emit(IVertexBuilder b,Matrix4f m,Matrix3f n,float x,float y,float z,float u,float v,float nx,float ny,float nz,int light){b.vertex(m,x,y,z).color(255,255,255,255).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,nx,ny,nz).endVertex();}
}
