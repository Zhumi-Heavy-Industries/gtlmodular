package com.gtlmodular.common.data.nanochip;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

import java.util.ArrayList;
import java.util.List;

/** 预览形状惰性构建，机器注册成功仍可能在打开预览时失败，因此自检需实际请求形状。 */
public final class NanochipPreviewSelfCheck {

    private NanochipPreviewSelfCheck() {
    }

    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        List<MultiblockMachineDefinition> defs = new ArrayList<>();
        if (NanochipMachines.NANOCHIP_ASSEMBLY_COMPLEX != null) {
            defs.add(NanochipMachines.NANOCHIP_ASSEMBLY_COMPLEX);
        }
        for (MultiblockMachineDefinition def : NanochipMachines.moduleDefinitions()) {
            if (def != null) {
                defs.add(def);
            }
        }
        if (defs.isEmpty()) {
            NanochipModContext.logger().warn("[gtlmodular] 预览自检：一个多方块定义都没有（注册顺序变了吗？）");
            return;
        }
        NanochipModContext.logger().info("[gtlmodular] ===== JEI 多方块预览自检（共 {} 个定义）=====", defs.size());
        NanochipModContext.logger().info("[gtlmodular]   收录门槛 = MultiblockMachineDefinition.isRenderXEIPreview（分类 gtceu:multiblock_info）");
        int renderable = 0, withCtrl = 0;
        for (MultiblockMachineDefinition def : defs) {
            boolean render = false;
            int shapes = -1;
            boolean hasController = false;
            String err = null;
            try {
                render = def.isRenderXEIPreview();
            } catch (Throwable t) {
                err = "isRenderXEIPreview 抛异常: " + t;
            }
            try {
                List<?> list = def.getMatchingShapes();
                shapes = list == null ? -1 : list.size();
                hasController = shapes > 0;
            } catch (Throwable t) {
                err = (err == null ? "" : err + "；") + "getMatchingShapes 抛异常: " + t;
            }
            if (render) {
                renderable++;
            }
            if (hasController) {
                withCtrl++;
            }
            NanochipModContext.logger().info("[gtlmodular]   {} | isRenderXEIPreview={} | matchingShapes={} {}",
                    def.getId(), render, shapes, err == null ? "" : ("⚠ " + err));
        }
        NanochipModContext.logger().info("[gtlmodular]   小结：有资格被 JEI 收录 {}/{}；能取到预览形状 {}/{}",
                renderable, defs.size(), withCtrl, defs.size());
        NanochipModContext.logger().info("[gtlmodular]   若两行都是满分却仍看不到预览页 ⇒ 问题在 JEI 分类的显示层（分类里 0 配方会被 JEI 隐藏），"
                + "把这两行日志发回即可定案");
    }
}
