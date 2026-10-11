package com.gtlmodular.common.data.nanochip;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gtlmodular.GTLModular;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>JEI 多方块预览页"收录情况"的启动期自检（只打印一次，INFO）。</b>
 *
 * <h3>为什么需要（父代理 2026-09-15 批准）</h3>
 * 玩家实测："NAC 那些主机**只能看到配方，看不到多方块预览**"。子代理逐字节取证
 * （`_census/multiblock_preview_diagnosis.md`）得出两条关键事实：
 * <ol>
 *   <li>JEI 分类 {@code gtceu:multiblock_info} 的**唯一收录门槛**是
 *       {@code MultiblockMachineDefinition::isRenderXEIPreview}（一个裸 boolean，
 *       {@code MachineBuilder} 构造函数默认置 true；GTCEu 自带的机器也都不调
 *       {@code multiblockPreviewRenderer}/{@code renderMultiblockXEIPreview}）；</li>
 *   <li>**唯一能让某个 shape 在预览里消失**的路径是 {@code PatternPreviewWidget.initializePattern}
 *       的末行 {@code return controller == null ? null : new MBPattern(...)}
 *       —— 即"预览形状里**找不到控制器方块**" ⇒ 整块被 {@code filter(Objects::nonNull)} 滤掉。</li>
 * </ol>
 * ⇒ 于是只剩两种可能：**① 没被分类收录**、**② 收录了但预览里找不到控制器**。
 * 本类在**注册全部完成之后**（{@code FMLLoadCompleteEvent}）把这两件事的判据一次性打出来：
 * <ul>
 *   <li>{@code isRenderXEIPreview()} —— 我们的每个定义是否**有资格**进那个分类；</li>
 *   <li>{@code getMatchingShapes()} 的**条数**与**是否含控制器** —— 走的就是 JEI 预览用的同一条
 *       懒加载路径（`getMatchingShapes → patternFactory.get().getPreview(...)`），
 *       顺带成了"图案能不能解析出来 + 控制器在不在里面"的**启动期自检**
 *       （2026-09-15 那次 `decode` 越界崩溃正是这条路径第一次被取用时发生的）。</li>
 * </ul>
 * 只打一次、只打 INFO，不参与任何逻辑、不改任何行为。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NanochipPreviewSelfCheck {

    private NanochipPreviewSelfCheck() {
    }

    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        List<MultiblockMachineDefinition> defs = new ArrayList<>();
        if (NanochipMachines.NANOCHIP_ASSEMBLY_COMPLEX != null) {
            defs.add(NanochipMachines.NANOCHIP_ASSEMBLY_COMPLEX);
        }
        for (MultiblockMachineDefinition def : NanochipMachines.NANOCHIP_MODULES.values()) {
            if (def != null) {
                defs.add(def);
            }
        }
        if (defs.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] 预览自检：一个多方块定义都没有（注册顺序变了吗？）");
            return;
        }
        GTLModular.LOGGER.info("[gtlmodular] ===== JEI 多方块预览自检（共 {} 个定义）=====", defs.size());
        GTLModular.LOGGER.info("[gtlmodular]   收录门槛 = MultiblockMachineDefinition.isRenderXEIPreview（分类 gtceu:multiblock_info）");
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
                // ★ 这一步就是 JEI 预览的取数路径（懒加载），也是 2026-09-15 崩溃的触发点
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
            GTLModular.LOGGER.info("[gtlmodular]   {} | isRenderXEIPreview={} | matchingShapes={} {}",
                    def.getId(), render, shapes, err == null ? "" : ("⚠ " + err));
        }
        GTLModular.LOGGER.info("[gtlmodular]   小结：有资格被 JEI 收录 {}/{}；能取到预览形状 {}/{}",
                renderable, defs.size(), withCtrl, defs.size());
        GTLModular.LOGGER.info("[gtlmodular]   若两行都是满分却仍看不到预览页 ⇒ 问题在 JEI 分类的显示层（分类里 0 配方会被 JEI 隐藏），"
                + "把这两行日志发回即可定案");
    }
}
