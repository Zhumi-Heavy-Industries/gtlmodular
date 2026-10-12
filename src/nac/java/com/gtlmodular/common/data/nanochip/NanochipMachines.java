package com.gtlmodular.common.data.nanochip;

import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.client.renderer.machine.MachineRenderer;
import com.gregtechceu.gtceu.client.renderer.machine.OverlayTieredActiveMachineRenderer;
import com.gtlmodular.common.data.NanochipBlocks;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipCalibration;
import com.gtlmodular.common.machine.nanochip.NanochipModuleKind;
import com.gtlmodular.common.machine.nanochip.NanochipModuleMachine;
import com.gtlmodular.common.machine.nanochip.NanochipModuleUpkeep;
import com.gtlmodular.common.machine.nanochip.NanochipPipeMachine;
import com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine;
import com.gtlmodular.common.machine.nanochip.display.NanochipLogisticsText;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 机器条目必须在 GTCEu 的机器注册窗口创建，结构方块则需提前创建以赶上 Forge 方块注册。 */
public final class NanochipMachines {

    private NanochipMachines() {
    }

    private static boolean initialized;

    public static MultiblockMachineDefinition NANOCHIP_ASSEMBLY_COMPLEX;

    /** 14 种内置模块按种类索引，保持注册与运行规格一一对应。 */
    public static final Map<NanochipModuleKind, MultiblockMachineDefinition> NANOCHIP_MODULES =
            new LinkedHashMap<>();

    private static final Map<ResourceLocation, MultiblockMachineDefinition> MODULES_BY_ID = new LinkedHashMap<>();

    public static MultiblockMachineDefinition moduleDefinition(ResourceLocation id) {
        return MODULES_BY_ID.get(id);
    }

    public static java.util.Collection<MultiblockMachineDefinition> moduleDefinitions() {
        return java.util.Collections.unmodifiableCollection(MODULES_BY_ID.values());
    }

    public static MachineDefinition VACUUM_INPUT_HATCH;

    public static MachineDefinition VACUUM_OUTPUT_HATCH;

    public static MachineDefinition VACUUM_PIPE;

    public static final int VACUUM_HATCH_TIER = 11;

    private static final ResourceLocation CASING_TEX =
            NanochipModContext.assetId("block/casings/nanochip_mesh_interface_casing");

    private static final ResourceLocation MODULE_CASING_TEX =
            NanochipModContext.assetId("block/casings/nanochip_mesh_interface_casing");
    /** WorkableOverlayModel#registerTextureAtlas 按根路径探测状态后缀，此处只提供 overlay 根目录。 */
    private static final ResourceLocation OVERLAY_TEX =
            NanochipModContext.assetId("block/multiblock/nanochip_assembly_complex");

    private static ResourceLocation moduleOverlayTex(NanochipModuleSpec spec) {
        return spec.overlay();
    }

    public static void register() {
        if (initialized) {
            return;
        }
        initialized = true;
        registerComplex();
        registerModules();
        registerVacuumHatches();
        registerVacuumPipe();
    }

    /** 沿用 OverlayTieredActiveMachineRenderer 的部件渲染路径，使仓并入多方块后仍能绘制前脸。 */
    public static final class VacuumHatchRenderer extends OverlayTieredActiveMachineRenderer {
        private static final ResourceLocation HATCH_CASING_TEX =
                NanochipModContext.assetId("block/casings/nanochip_mesh_interface_casing");

        public VacuumHatchRenderer(int tier, ResourceLocation idleModel, ResourceLocation activeModel) {
            super(tier, idleModel, activeModel);
            Map<String, ResourceLocation> hull = new HashMap<>();
            hull.put("bottom", HATCH_CASING_TEX);
            hull.put("top", HATCH_CASING_TEX);
            hull.put("side", HATCH_CASING_TEX);
            setTextureOverride(hull);
        }

        /** 以本端口实际搬运记录选择动画，避免主机开工时所有空闲端口一起显示工作态。 */
        @Override
        public void renderMachine(java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads,
                                  com.gregtechceu.gtceu.api.machine.MachineDefinition definition,
                                  com.gregtechceu.gtceu.api.machine.MetaMachine machine,
                                  net.minecraft.core.Direction frontFacing, net.minecraft.core.Direction side,
                                  net.minecraft.util.RandomSource rand, net.minecraft.core.Direction modelFacing,
                                  net.minecraft.client.resources.model.ModelState modelState) {
            if (!(machine instanceof NanochipVacuumHatchMachine hatch)) {
                super.renderMachine(quads, definition, machine, frontFacing, side, rand, modelFacing, modelState);
                return;
            }
            com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer chosen =
                    hatch.isVacuumWorking() ? this.activeOverlayModel : this.overlayModel;
            com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer savedIdle = this.overlayModel;
            com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer savedActive = this.activeOverlayModel;
            this.overlayModel = chosen;
            this.activeOverlayModel = chosen;
            try {
                super.renderMachine(quads, definition, machine, frontFacing, side, rand, modelFacing, modelState);
            } finally {
                this.overlayModel = savedIdle;
                this.activeOverlayModel = savedActive;
            }
        }
    }

    private static void registerVacuumHatches() {
        int tier = VACUUM_HATCH_TIER;
        VACUUM_INPUT_HATCH = NanochipModContext.machineRegistrate()
                .machine("nanochip_vacuum_input_hatch",
                        holder -> new NanochipVacuumHatchMachine(holder, tier, IO.IN))
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_ITEMS)
                .tier(tier)
                .renderer(() -> new VacuumHatchRenderer(tier,
                        NanochipModContext.assetId("block/machine/part/nanochip_vacuum_input"),
                        NanochipModContext.assetId("block/machine/part/nanochip_vacuum_input_active")))
                .langValue("真空传送输入端口")
                .tooltips(vacuumHatchTooltips(true))
                .register();

        VACUUM_OUTPUT_HATCH = NanochipModContext.machineRegistrate()
                .machine("nanochip_vacuum_output_hatch",
                        holder -> new NanochipVacuumHatchMachine(holder, tier, IO.OUT))
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.EXPORT_ITEMS)
                .tier(tier)
                .renderer(() -> new VacuumHatchRenderer(tier,
                        NanochipModContext.assetId("block/machine/part/nanochip_vacuum_output"),
                        NanochipModContext.assetId("block/machine/part/nanochip_vacuum_output_active")))
                .langValue("真空传送输出端口")
                .tooltips(vacuumHatchTooltips(false))
                .register();

        NanochipModContext.logger().info("[gtlmodular] NAC 已注册真空传送带仓: 输入(VCI) / 输出(VCO), tier={}", tier);
    }

    private static void registerVacuumPipe() {
        VACUUM_PIPE = NanochipModContext.machineRegistrate()
                .machine("nanochip_vacuum_pipe", NanochipPipeMachine::new)
                .blockProp(p -> p.noOcclusion())
                .rotationState(RotationState.ALL)
                .tier(VACUUM_HATCH_TIER)
                .renderer(() -> new com.gtlmodular.client.renderer.NanochipPipeRenderer(
                        NanochipModContext.assetId("block/machine/part/vacuum_pipe")))
                .langValue("真空传送管道")
                .tooltips(
                        NanochipLogisticsText.deprecated(),
                        Component.translatable("gtlmodular.nanochip.pipe.tip.1"),
                        Component.translatable("gtlmodular.nanochip.pipe.tip.2"),
                        Component.translatable("gtlmodular.nanochip.pipe.tip.3"),
                        Component.translatable("gtlmodular.nanochip.pipe.tip.4"))
                .register();
        NanochipModContext.logger().info("[gtlmodular] NAC 已注册真空传送带管道（细管外观，定向路由已接入："
                + "由 nanochipComplex.vacuumRequiresPipes 切换 简单/管道定向 两种模式，默认=简单）");
    }

    public static ItemStack machineIconStack(String path) {
        if (path == null) {
            return ItemStack.EMPTY;
        }
        if (NANOCHIP_ASSEMBLY_COMPLEX != null && path.equals(NANOCHIP_ASSEMBLY_COMPLEX.getId().getPath())) {
            return NANOCHIP_ASSEMBLY_COMPLEX.asStack();
        }
        for (MultiblockMachineDefinition def : NANOCHIP_MODULES.values()) {
            if (def != null && path.equals(def.getId().getPath())) {
                return def.asStack();
            }
        }
        return NANOCHIP_ASSEMBLY_COMPLEX == null ? ItemStack.EMPTY : NANOCHIP_ASSEMBLY_COMPLEX.asStack();
    }

    private static Component[] vacuumHatchTooltips(boolean input) {
        return new Component[]{
                NanochipLogisticsText.deprecated(),
                Component.translatable("gtlmodular.nanochip.hatch.tip.paint"),
                Component.translatable("gtlmodular.nanochip.hatch.tip.what"),
                Component.translatable("gtlmodular.nanochip.hatch.tip.channel"),
                Component.translatable(input
                        ? "gtlmodular.nanochip.hatch.tip.in"
                        : "gtlmodular.nanochip.hatch.tip.out"),
                Component.translatable("gtlmodular.nanochip.hatch.tip.feed.room"),
                Component.translatable("gtlmodular.nanochip.hatch.tip.feed.module"),
                Component.empty(),
                Component.translatable("gtlmodular.nanochip.hatch.tip.route"),
                Component.translatable("gtlmodular.nanochip.hatch.tip.recolor")
        };
    }

    private static void registerComplex() {
        NANOCHIP_ASSEMBLY_COMPLEX = NanochipModContext.machineRegistrate()
                .multiblock("nanochip_assembly_complex", NanochipAssemblyComplexMachine::new)
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(NanochipRecipeTypes.ASSEMBLY_COMPLEX)
                .recipeModifier(NanochipAssemblyComplexMachine::nacRecipeModifier)
                .appearanceBlock(() -> NanochipBlocks.MESH_INTERFACE_CASING.get())
                .pattern(NanochipComplexPattern::build)
                .workableCasingRenderer(CASING_TEX, OVERLAY_TEX)
                .langValue("纳米芯片组装复合体")
                .tooltips(
                        Component.literal("NAC, 纳米芯片组装复合体").withStyle(ChatFormatting.GOLD),
                        Component.translatable("gtlmodular.nanochip.complex.desc"),
                        Component.translatable("gtlmodular.nanochip.complex.mode"),
                        Component.translatable("gtlmodular.nanochip.complex.structure",
                                NanochipComplexPattern.NAC_W,
                                NanochipComplexPattern.NAC_H,
                                NanochipComplexPattern.NAC_D,
                                NanochipComplexPattern.blockCount()))
                .register();
        NanochipModContext.logger().info("[gtlmodular] NAC 已注册: {} ({})",
                NANOCHIP_ASSEMBLY_COMPLEX.getId(), "纳米芯片组装复合体");
    }

    private static void registerModules() {
        for (NanochipModules.ResolvedModule resolved : NanochipModules.freezeBuiltins()) {
            NanochipModuleSpec spec = resolved.spec();
            MultiblockMachineDefinition def = com.gtlmodular.common.machine.nanochip.registration.NanochipModuleRegistration.registrate(spec)
                    .multiblock(spec.machineId(), holder -> new NanochipModuleMachine(holder, spec))
                    .rotationState(RotationState.ALL)
                    .recipeType(resolved.recipeType())
                    .recipeModifier(NanochipModuleMachine::moduleFlexibleOverclock)
                    .appearanceBlock(() -> NanochipBlocks.MESH_INTERFACE_CASING.get())
                    .pattern(NanochipComplexPattern::buildModule)
                    .beforeWorking(NanochipModuleUpkeep::onBeforeWorking)
                    .onWorking(NanochipModuleUpkeep::onWorking)
                    .workableCasingRenderer(spec.casing(), moduleOverlayTex(spec))
                    .langValue(spec.nameZh())
                    .tooltips(
                            Component.literal("§7NAC 模块 · §f" + spec.nameEn()).withStyle(ChatFormatting.GRAY),
                            Component.translatable(spec.langKey("role")),
                            Component.translatable(spec.langKey("desc")),
                            spec.unlocksFullMode()
                                    ? Component.translatable("gtlmodular.nanochip.module.gate")
                                    : Component.translatable("gtlmodular.nanochip.module.stack"),
                            Component.translatable("gtlmodular.nanochip.module.innate"),
                            Component.translatable("gtlmodular.nanochip.module.simple_logistics"))
                    .register();
            MODULES_BY_ID.put(spec.id(), def);
            if (spec.legacyKind() != null) NANOCHIP_MODULES.put(spec.legacyKind(), def);
            NanochipModContext.logger().info("[gtlmodular] NAC 模块已注册: {} ({})",
                    def.getId(), spec.nameZh());
        }
    }
}
