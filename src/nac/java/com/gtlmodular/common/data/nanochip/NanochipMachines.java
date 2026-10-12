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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 纳米芯片组装复合体（NAC）机器注册。
 *
 * <p>机器条目只能在 GTCEu 的机器注册窗口创建；结构方块必须更早创建，
 * 才能赶上 Forge 的方块注册事件。
 */
public final class NanochipMachines {

    private NanochipMachines() {
    }

    private static boolean initialized;

    /** NAC 主控制器。 */
    public static MultiblockMachineDefinition NANOCHIP_ASSEMBLY_COMPLEX;

    /** 11 种模块机（按种类索引）。 */
    public static final Map<NanochipModuleKind, MultiblockMachineDefinition> NANOCHIP_MODULES =
            new LinkedHashMap<>();

    /** 真空传送输入端口（VCI）。 */
    public static MachineDefinition VACUUM_INPUT_HATCH;

    /** 真空传送输出端口（VCO）。 */
    public static MachineDefinition VACUUM_OUTPUT_HATCH;

    /** 真空传送带管道（细管外观 + 玩家喷漆换色；定向路由见方案文档 §1.2 第 5 步）。
     *  <p>⚠️ <b>2026-09-17 更正</b>：这里早先把「自动取色」当成真空管道的一项外观特性写了进来 ——
     *  "从邻居自动取色"（{@code autoColorFromNeighbour()}）是移植版自己发明的东西，
     *  NH 里没有，已在第六轮<b>整段删除</b>；管道的颜色只有两个来源：玩家喷漆罐 / 未染色（-1）。*/
    public static MachineDefinition VACUUM_PIPE;

    /**
     * 真空传送带仓的电压档：<b>11 = UIV</b> —— 与原版注册处一致，只影响机壳贴图与仓室等级，不影响搬运速度。
     *
     * <p><b>取证（2026-09-14 深夜更正，之前写的 8/UV 是错的）</b>：
     * 原版在 {@code gregtech/loaders/preload/LoaderMetaTileEntities.registerNacHatches()} 里注册这两个仓：
     * <pre>
     * new MTEHatchVacuumConveyorInput(MetaTileEntityIDs.HATCH_VACUUM_CONVEYOR_INPUT.ID,
     *         "vacuum.hatch.input", "Vacuum Conveyor Input", 11)   // 第 4 个参数 = tier = 11
     * </pre>
     * （{@code javap -c} 里就是 {@code bipush 11}；输出仓同样 11。）
     * 而 {@code Textures$BlockIcons.MACHINECASINGS_SIDE} 是 15 元素数组、
     * index 0=ULV … 8=UV、9=UHV、10=UEV、<b>11=UIV</b>、12=UMV、13=UXV、14=MAX（已核对数组填充字节码），
     * 所以原版仓的机壳是 {@code MACHINE_UIV_SIDE/TOP/BOTTOM}。
     */
    public static final int VACUUM_HATCH_TIER = 11;

    /** NAC 贴图（自 GTNH jar 解包）。 */
    private static final ResourceLocation CASING_TEX =
            NanochipModContext.assetId("block/casings/nanochip_mesh_interface_casing");

    /**
     * 模块控制器的外壳贴图 —— <b>与复合体主机同一个贴图</b>（纳米芯片接口机械方块）。
     *
     * <p><b>取证（2026-09-17 更正：之前写的是 nanochip_reinforcement_casing，那是错的）</b>：
     * <pre>
     * MTENanochipAssemblyModuleBase.getCasingTexture()            // 偏移 0..6
     *   0: getstatic  MTENanochipAssemblyComplex.CASING_INDEX_WHITE
     *   3: invokestatic Textures$BlockIcons.getCasingTextureForId
     * </pre>
     * 而 {@code CASING_INDEX_WHITE = Casings.NanochipMeshInterfaceCasing.textureId}
     * （{@code MTENanochipAssemblyComplex.<clinit>} 偏移 0/3/6；= {@code gt.blockcasings12.1}，
     * 与 tooltip {@code gt.blockcasings12.1.name} = 纳米芯片接口机械方块互印）。
     * ⇒ <b>原版模块控制器的外壳就是主机那个机壳</b>，所以正面以外必须与主机同款。
     *
     * <p>注意口径：控制器<b>本体</b>面用的是这张贴图的「普通格」（两块控制器都<b>不</b>走 CTM 包装 ——
     * {@code CtmClientSetup} 按方块注册名认目标，{@code nanochip_assembly_complex} 与 11 个模块 id
     * 都不在 {@code TARGETS} 里）；玩家看到的"蓝黑连片"来自周围的壳格（网格接口机壳等）。
     * 本次改的只是"引用哪张贴图"，所以模块控制器本体从此与主机控制器本体<b>逐像素同款</b>。
     *
     * <p>结构格（塔身）不受此影响：塔身仍按 NH 口径用强化机壳等，
     * 见 {@code _census/role_reference.md} 与 {@code check_all_patterns.js} 第 ⑧ 条。
     */
    private static final ResourceLocation MODULE_CASING_TEX =
            NanochipModContext.assetId("block/casings/nanochip_mesh_interface_casing");
    /**
     * <b>NAC 主机</b>的正脸 overlay <b>根路径</b>。
     *
     * <p><b>实测约定（§75，GTCEu 1.4.4 字节码取证）</b>：{@code workableCasingRenderer(casing, overlay)}
     * 由 {@code MachineBuilder.lambda$workableCasingRenderer$14} 构造
     * {@code WorkableCasingMachineRenderer}，后者内部 {@code new WorkableOverlayModel(overlay)}；
     * {@code WorkableOverlayModel.registerTextureAtlas(Consumer)} 会<b>按后缀自动探测</b>同一根路径下的变体
     * （拼接模板取自 {@code BootstrapMethods #1/#2/#3}：
     * {@code "/overlay_" + face}、{@code path + 上者}、{@code 上者 + "_emissive"}）：
     * <pre>
     *   &lt;overlay&gt;/overlay_front.png                     空闲
     *   &lt;overlay&gt;/overlay_front_active.png              工作中（IWorkable.isActive() &amp;&amp; isWorkingEnabled()）
     *   &lt;overlay&gt;/overlay_front_paused.png              已停机（active 但工作被关）
     *   &lt;overlay&gt;/overlay_front_emissive.png            空闲 · 发光层（emissive）
     *   &lt;overlay&gt;/overlay_front_active_emissive.png     工作中 · 发光层（emissive，bakeFace 光照 15）
     * </pre>
     * 缺失的变体会回落到上一层（{@code ResourceManager.getResource} 判定），所以这里<b>只给根路径一个</b>
     * 就能让 {@code _ACTIVE} / {@code _ACTIVE_EMISSIVE} 自动生效。
     *
     * <p>本模组主机这三张图与 NH {@code OVERLAY_FRONT_NANOCHIP_ASSEMBLY_COMPLEX}
     * {@code {,_ACTIVE,_ACTIVE_GLOW}} <b>逐字节相同</b>（sha256 见 §75）。
     */
    private static final ResourceLocation OVERLAY_TEX =
            NanochipModContext.assetId("block/multiblock/nanochip_assembly_complex");

    /**
     * <b>某个模块自己的</b>正脸 overlay 根路径：{@code gtlmodular:block/multiblock/<machineId>}。
     *
     * <p>目录里的三张图逐字节来自 NH 的
     * {@code OVERLAY_FRONT_<kind.overlayPrefix()>{,_ACTIVE,_ACTIVE_GLOW}}（§75），
     * 文件名按 GTCEu 的后缀约定（见 {@link #OVERLAY_TEX}）。
     *
     * <p><b>两个名字谁管什么（2026-09-17 写清，别再把它们混成一句）</b>：
     * 这里的 {@code ResourceLocation} 是用 <b>{@link NanochipModuleKind#machineId()}</b> 拼的
     * —— 因为贴图目录在构建期就是按方块 id 落地的；
     * 而「kind ↔ NH 图标集」那张表的唯一来源是
     * {@link NanochipModuleKind#overlayPrefix()}，它<b>不</b>参与拼路径，
     * 而是在 {@code NanochipModuleKind} 的构造器里推导出贴图目录名并与 {@code machineId()}
     * 做相等断言（唯一例外 BIOLOGICAL_COORDINATOR 登记在那边）。
     * ⇒ 两条名字被不变量锁在一起；出现分歧时是类初始化直接失败，而不是这里悄悄画错图。
     * <p>⚠️ 早先这条注释写的是「映射只有 overlayPrefix() 一处来源」，而当时 overlayPrefix()
     * 一个调用点都没有、路径实际来自 machineId() —— 那就是"注释说 A、实现是 B"，已更正。
     */
    private static ResourceLocation moduleOverlayTex(NanochipModuleKind kind) {
        return NanochipModContext.assetId("block/multiblock/" + kind.machineId());
    }

    /** 在 GTM 机器注册窗口内调用。 */
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

    /**
     * 真空传送带仓（VCI / VCO）：颜色就是线，同色自动对接。
     *
     * <p>它们是<b>仓室（part）</b>而不是多方块，所以能装进复合体控制室的壳格，
     * 也能装进任意模块的壳格；放在哪一侧就干哪一侧的活（见
     * {@link com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine}）。
     * 贴图直接复用 GTCEu 的物品总线仓模型（进口/出口两套）。
     */
    /**
     * 真空传送带仓的渲染器：外观 = <b>纳米芯片接口机壳（= NH 成型时的机壳）</b> + 前脸两层 overlay，并随通道颜色染色。
     *
     * <p><b>为什么是"继承 + 覆盖贴图"，而不是自己写一个 MachineRenderer</b>：
     * {@code OverlayTieredActiveMachineRenderer}（继承链上就是 GTCEu 的<b>标准仓室渲染路径</b>）——
     * 多方块成形后，控制器渲染整座结构时会通过 {@code IPartRenderer.renderReplacedPartMachine}
     * 回调每个仓室，把它的前脸 overlay 画回结构上。换成自定义 {@code MachineRenderer} 后这条路径不生效，
     * 玩家实测为「多方块一成形，仓的脸就没了」。
     *
     * <p><b>两层前脸 + 工作中切动画（与原版逐一对应）</b>：
     * <pre>
     * MTEHatchVacuumConveyorInput.getTexturesInactive(t) = {
     *     TextureFactory.of(OVERLAY_VACUUM_HATCH_INPUT, Dyes.getModulation(getColorization(), ...)),  // 第 1 层：被染料调制
     *     TextureFactory.of(OVERLAY_VACUUM_PIPE_PORT_INPUT)                                         // 第 2 层：不染色
     * }
     * getTexturesActive(t) = 同上，第 1 层换成 OVERLAY_VACUUM_HATCH_INPUT_ACTIVE（16×256 动画）
     * </pre>
     * 所以这里给 {@code OverlayTieredActiveMachineRenderer} 传<b>空闲模型 + 工作中模型</b>两个：
     * 空闲模型第 1 层 = 静态 overlay、工作中模型第 1 层 = ACTIVE 动画（引擎会按
     * {@code IRecipeLogicMachine.isActive()} / 多方块控制器是否在跑来切）。两个模型里
     * 第 1 层都带 {@code tintindex:2}（= {@code NanochipVacuumHatchMachine.TINT_OVERLAY}，吃染料），
     * 第 2 层不带 tintindex（不染色）。
     *
     * <h2>★ 2026-09-17 第六轮：仓的外壳贴图改成"纳米芯片接口机壳"（玩家第 3 条）</h2>
     * <p>★ <b>2026-09-17 第七轮更正</b>：这里说的是<b>"已并入成形结构"那一态</b>的外壳。本渲染器给出的
     * override 表（{@code bottom/top/side → 纳米芯片接口机壳}）就是"成形态"的底子；
     * <b>未并入任何成形结构</b>时，外壳由客户端 CTM 包装器在渲染期换成
     * <b>GTCEu 自己的 tier 机壳</b>（{@code gtceu:block/casings/voltage/uiv/*}，
     * 见 {@code com.gtlmodular.client.ctm.CtmHullGroup#tierCasingSprite}）—— 玩家裁决原文：
     * 「GTL里面不也有UIV机械外壳吗？直接引用不就行了」。
     * <p>第七轮同时<b>删除了</b>旧版留下的三张自定义机壳图
     * {@code assets/gtlmodular/textures/block/machine/part/vacuum_hatch_hull_*.png}
     * （它们自第六轮起就无人引用；现在那一态改为引用 GTCEu 的 tier 机壳 ⇒ 已删除，不再保留双份口径）。
     * <p>NH 的取证（下面这段）解释"为什么成形态必须是这张"：
     * <pre>
     * // ① 成型时：模块/主机结构把机壳索引交给仓
     * MTENanochipAssemblyModuleBase.addBaseStructure(StructureDefinition$Builder)  (dump3 偏移 45)
     *  45: getstatic     Field MTENanochipAssemblyComplex.CASING_INDEX_WHITE:I
     *  48: invokevirtual HatchElementBuilder.casingIndex:(I)Lgregtech/api/util/HatchElementBuilder;   // 把索引塞进构建器
     * MTENanochipAssemblyModuleBase.addConveyorToMachineList(IGregTechTileEntity, int)  (偏移 0..96)
     *  34: iload_2                                     // ← 参数 2 就是这个索引
     *  35: invokevirtual MTEHatchVacuumConveyorInput.updateTexture:(I)V
     *  72: iload_2
     *  73: invokevirtual MTEHatchVacuumConveyorOutput.updateTexture:(I)V
     *
     * // ② 这个索引到底是什么
     * MTENanochipAssemblyComplex.&lt;clinit&gt;  (偏移 0/3/6)
     *   0: getstatic     Field gregtech/api/casing/Casings.NanochipMeshInterfaceCasing:Lgregtech/api/casing/Casings;
     *   3: getfield      Field gregtech/api/casing/Casings.textureId:I
     *   6: putstatic     Field CASING_INDEX_WHITE:I
     * // ⇒ CASING_INDEX_WHITE = Casings.NanochipMeshInterfaceCasing.textureId =「纳米芯片接口机械方块」
     *
     * // ③ 仓怎么用它
     * MTEHatch.updateTexture(int)            // 偏移 0..34：page = id >> 7、index = id & 127
     * MTEHatch.getCasingTexture()            // 偏移 14..29：return casingTexturePages[texturePage][textureIndex]（非 0 才有）
     * MTEHatch.getTexture(...)               // 偏移 0..24：
     *   1: invokevirtual getCasingTexture:()Lgregtech/api/interfaces/ITexture;
     *   8: ifnonnull 26
     *  11: getstatic Textures$BlockIcons.MACHINE_CASINGS:[[Lgregtech/api/interfaces/ITexture;
     *  18: aaload   (mTier)  / 19: iload 4 / 21: iconst_1 / 22: iadd / 23: aaload
     * // ⇒ 有 casingTexture 用它，否则退回 MACHINE_CASINGS[tier][aColor+1]
     * </pre>
     * ⇒ 结论两条：
     * <ol>
     *   <li><b>在 NAC 结构里</b>，仓的外壳是 {@code NanochipMeshInterfaceCasing}
     *       —— 也就是与本模组复合体/模块机身<b>同一张机壳贴图</b>（{@link #CASING_TEX} / {@link #MODULE_CASING_TEX}）；
     *       {@code updateTexture(索引)} 是在<b>结构成形、把仓收进机器清单时</b>被调用的
     *       （{@code addConveyorToMachineList} 是 {@code HatchElementBuilder} 的元素回调）。</li>
     *   <li><b>独立摆放（不在多方块里）</b>时 NH 用的是 {@code MACHINE_CASINGS[11] = UIV 机壳}
     *       —— 因为那时 {@code texturePage/textureIndex} 还是 0、{@code getCasingTexture()} 返回 null。</li>
     * </ol>
     *
     * <h3>本移植版的取舍（诚实记录）</h3>
     * <p>GTCEu 的 {@code TextureOverrideRenderer} 是<b>按 MachineDefinition 全局唯一</b>的渲染器实例，
     * 它的 {@code override} 表在第一次烘焙模型时就被用掉（{@code getRotatedModel(Direction)} 按方向缓存），
     * <b>所以本渲染器给不出"同一个方块随成形状态换机壳"</b>（那需要 GT5U 的
     * {@code ITexture[] getTexture(...)} 逐帧组装的分层贴图路径）。
     * <p><b>但那一态并没有被放弃 —— 它由客户端的 CTM 包装器补上（2026-09-17 第七轮）</b>：
     * {@code com.gtlmodular.client.ctm.MachineCtmBakedModel} 在渲染期按
     * {@code CtmHullGroup.isCasingFromStructure(...)} 分成两态
     * （见该文件第 381~391 行的分支与 {@code CtmHullGroup} 类注释）：
     * <pre>
     *   未并入任何成形结构 → 外壳 = GTCEu 的 tier 机壳 gtceu:block/casings/voltage/uiv/*
     *                        （CtmHullGroup#tierCasingSprite），且不参与 CTM；
     *   已并入成形结构     → 外壳 = 纳米芯片接口机壳（= 本渲染器 override 的那三行）+ 参与 CTM。
     * </pre>
     * ⇒ 本渲染器的三行 hull 只是<b>"已并入态"的底子</b>。
     *
     * <p>⚠️ <b>2026-09-17 更正</b>：这里早先把"单独摆放时本移植版仍画接口机壳"当成一处
     * <b>有意保留</b>的偏差来记 —— 那句在第七轮加了两态渲染之后就已经<b>不成立</b>了
     * （实现见上面的 {@code MachineCtmBakedModel}），留着会与代码互相打脸，已改写为本节。
     *
     * <p><b>只动贴图引用</b>：方块 id、结构判定、仓的存档格式、搬运逻辑<b>一律没碰</b>。
     */
    public static final class VacuumHatchRenderer extends OverlayTieredActiveMachineRenderer {
        /**
         * 仓在 NAC 结构里的外壳贴图 —— NH 的 {@code CASING_INDEX_WHITE = NanochipMeshInterfaceCasing}
         * （见类注释的三段字节码）。与机身同一张：{@code block/casings/nanochip_mesh_interface_casing}。
         *
         * <p>NH 侧 {@code MTEHatch.getTexture} 给 6 个面返回的是<b>同一个</b> {@code ITexture}（非正面
         * {@code new ITexture[]{t}}，正面 {@code getTexturesActive/Inactive(t)} 也把 t 放在第 0 位），
         * 所以 bottom/top/side 三把键指向同一张贴图是正确的，不是偷懒。
         */
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

        /**
         * 用 <b>"本端口最近真的搬过件"</b> 决定画空闲层还是工作层，而不是 GTCEu 默认的
         * "多方块控制器在跑" —— 后者会让整座 NAC 一开工、所有端口一起闪动画；原版是"这个端口在工作"才亮。
         *
         * <p>做法：把两层字段都临时指向选中的那一层，基类无论走哪条分支都会画出我们要的那层，
         * 调用完立刻还原（只影响渲染线程的这一帧，最坏是极端并发下某一帧选错层，纯视觉）。
         */
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
        // 外观定案（2026-09-14 深夜按原版注册处复核，第三次取证后；2026-09-17 更正实现口径）：
        //   ① GTNH 的仓用的是 Textures.BlockIcons.MACHINE_CASINGS[tier] —— 也就是
        //      iconsets/MACHINE_<档>_SIDE/TOP/BOTTOM。**档位取原版注册值 11（UIV）** ⇒ MACHINE_UIV_*。
        //      ⚠️ **本模组并没有把这三张图拷进来**（资源里搜不到任何 MACHINE_UIV_* 文件）——
        //      实现引用的是 **GTCEu 自己的** UIV tier 机壳
        //      `gtceu:block/casings/voltage/uiv/{bottom,top,side}`（见
        //      `com.gtlmodular.client.ctm.CtmHullGroup#tierCasingSprite`）；玩家裁决原文
        //      「GTL里面不也有UIV机械外壳吗？直接引用不就行了」。此处旧注释声称这三张图已被逐字节
        //      拷进本模组，与实现不符，已删除。
        //   ② 原版染色：MACHINE_CASINGS[tier][mIndex+1] = TextureFactory.of(bottom, top, side, Dyes.xxx.getRGBA())
        //      —— 就是"机壳贴图 × 染料 RGBA"（已核对 Textures$BlockIcons 静态初始化字节码），
        //      在 GTCEu 里的等价物就是模型面上的 tintindex:1 + 机器的染色值；
        //   ③ 所以正确组合 = **UIV tier 机壳 + tintindex:1 + 真空前脸 overlay**。
        // 之前两次尝试各错一半：换成自定义真空外壳但漏 tintindex（不能染色），
        // 或退回 OverlayTieredMachineRenderer（能染色但不是 GTNH 的机壳）。
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

    /**
     * <b>真空传送带管道</b>：原版那条"看得见的线路"的移植版（细管外观 + 自动取相邻端口颜色）。
     *
     * <p>外观与原版 {@code MTEVacuumConveyorPipe} 对齐：不是整方块，而是
     * <b>管芯与六个管臂同一个粗细（{@code 5..11} = 6 像素 = 0.375 格）+ 朝每个已连接方向伸出的管臂</b>，
     * 几何由
     * {@link com.gtlmodular.client.renderer.NanochipPipeRenderer} 在渲染时按
     * {@link NanochipPipeMachine#connectionMask()} 现算；管身层 {@code vacuum_pipe} <b>不染</b>、
     * 叠加条层 {@code vacuum_pipe_bar} <b>吃染料</b>（{@code tintindex = TINT_BAR = 3}，
     * 由 {@code NanochipPipeMachine.tintColor(3)} 提供），没有机壳底层。
     *
     * <p>★ 2026-09-17 口径更正（原文写的是"中心细芯（4..12）"，那是**已被推翻的旧口径**，
     * 而且与现常量也不符）：现在的口径与 {@code NanochipPipeMachine} 的类注释逐字一致 ——
     * <b>管芯与管臂同一个粗细 = {@code 5..11}</b>。证据（NH 字节码）：
     * {@code MetaPipeEntity.renderInWorld} 偏移 20 调 {@code getThickness()} →
     * {@code MetaPipeEntity.getThickness()} 偏移 23 虚分派 {@code getCollisionThickness()} →
     * {@code MTEVacuumConveyorPipe.getCollisionThickness()} 偏移 21 {@code ldc 0.375f}
     * （父类 {@code MTEBaseFactoryPipe} 的 {@code mThickness = 0.5f} 是**死值**，子类覆写了读它的方法）。
     * 详见 {@code _census/check_pipe_uv.js}。
     *
     * <p>渲染器仍然挂在 GTCEu 标准的 {@link MachineRenderer} 继承链上（机器方块本来就没有 blockstate json，
     * quads 全由 {@code MachineDefinition} 上的渲染器给），所以方块依旧是一个普通机器方块
     * （{@code NanochipPipeMachine extends MetaMachine}），颜色 / NBT / 螺丝刀换色<b>原样保留</b>。
     * <p>⚠️ <b>2026-09-17 更正</b>：这里早先写的是"颜色/NBT/螺丝刀/<b>自动取色</b>逻辑原样保留"——
     * "自动取色"（{@code autoColorFromNeighbour()}）是移植版自己发明的、NH 里没有的东西，
     * 已在第六轮**整段删除**，不存在"原样保留"这回事。现在管道的颜色只有两个来源：
     * 玩家亲手喷漆 / 未染色（{@code -1}）。
     *
     * <p><b>{@code .blockProp(p -> p.noOcclusion())} 是细管的必需项，不是可选优化</b>：
     * GTCEu 的机器方块默认是"实心满格"（{@code MachineBuilder} 默认
     * {@code initialProperties(() -> Blocks.DISPENSER)}，没有任何 {@code noOcclusion()}），
     * 于是 {@code Block.shouldRenderFace} 里的 {@code neighborState.canOcclude()} 为真、
     * <b>邻居贴着管道的那一面会被整片剔掉</b> —— 外观一旦变细，就会从管子的空隙里看到"邻居缺了一面"。
     * 关掉遮挡后邻居的面照常画，光也能穿过管子的空格（细管本来就该这样）。
     * GTCEu 自己在 {@code GTBlocks} 里也大量使用 {@code noOcclusion()}，是同一条路子。
     */
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
                        Component.translatable("gtlmodular.nanochip.pipe.tip.1"),
                        Component.translatable("gtlmodular.nanochip.pipe.tip.2"),
                        Component.translatable("gtlmodular.nanochip.pipe.tip.3"),
                        Component.translatable("gtlmodular.nanochip.pipe.tip.4"))
                .register();
        NanochipModContext.logger().info("[gtlmodular] NAC 已注册真空传送带管道（细管外观，定向路由已接入："
                + "由 nanochipComplex.vacuumRequiresPipes 切换 简单/管道定向 两种模式，默认=简单）");
    }

    /**
     * ★ 2026-09-19（队长：「JEI 里模块的配方应当显示它自己的图标，不显示别的」）：
     * 按<b>方块 id 的 path</b> 取"这台机器自己的方块物品"，供
     * {@code NanochipRecipeTypes.machineIcon(...)} → {@code GTRecipeType.setIconSupplier(...)} 用。
     *
     * <p>为什么用 id 字符串而不是直接传 {@code MultiblockMachineDefinition}：
     * {@code NanochipRecipeTypes} 的静态字段在 {@link NanochipModuleKind} 之前初始化
     * （kind 的构造参数引用了那些表），若让两边互相引用就成了类初始化环
     * ⇒ 表那一侧只存一个 <b>字符串</b>、真正的查表推迟到 Supplier 被 {@code get()} 的那一刻
     * （JEI 建分类时，注册窗口早已结束）。
     *
     * <p>兜底：id 找不到模块时回落到复合体自己的方块物品（正常路径永远走不到；
     * 一旦表里写错 id，{@code _census/check_hatch_abilities.js} 的图标钉子会先判红）。
     */
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
        // 【NH 原文对齐】逐条照抄 GT5U.tooltip.nac.hatch.vc.*（原版顺序）：
        //   base.1 = Must be %s to work              → 必须染色才能工作
        //   base.2 = Can be installed in the §aNanochip Assembly Complex
        //   base.3 = Vacuum Conveyor Connections are 1 to 1
        //   input/output.base.1 = Provides §eCircuit Component§7 input/output for NAC modules
        //   base.4/5 = §eOutputs from recipes with inputs from a colored VCI will be placed in a VCO of the corresponding color
        //
        // ★★ 2026-09-19（队长：「真空输入塞不进去东西呀」）★★ 追加两行"该往哪台塞什么"：
        //   现场查证（逐条字节码，详见 `_census/模块舱室核对_队员.md` §③）：
        //     · 两个端口都<b>继承 GTCEu 的 ItemBusPartMachine</b>，能力面用的是
        //       NotifiableItemStackHandler(this, slots, io, capabilityIO) —— 三参构造把 capabilityIO 也设成 io
        //       （javap：3 参构造偏移 0..8 把 aload_3 连传两次）⇒ VCI = IN、VCO = OUT；
        //     · capabilityIO 决定 canCapInput()/canCapOutput()（ICapabilityTrait 默认方法，javap 偏移 0..29）：
        //       ⚠️ 于是**自动化（管道/漏斗/AE）只能往 VCI 插**，往 VCO 插会被原样退回；
        //     · 而控制室上"真物品 → CC"的正门恰恰是 **VCO（输出端口）**（VCI 是 CC → 真物品的还原口，
        //       见 NanochipVacuumHatchMachine 的类注释与 roomEncapsulate/roomMaterialize）。
        //   ⇒ 队长往"输入端口"塞真物品，插得进去但**不会被封装**；而真正该喂的"输出端口"自动化又插不进去。
        //   修法有两半：① 代码侧让 VCO 对自动化也开放插入（NanochipVacuumHatchMachine.createInventory 覆写）；
        //   ② 提示里把"往哪台塞什么"写成中文，别再让玩家靠命名猜（VCI/VCO 是**站在网络视角**命名的）。
        return new Component[]{
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
                // ★ 2026-09-19（队长：「给主机注册本模组自己的类型（显示「纳米芯片组装复合体」/「纳米芯片换算」）」）：
                //   改前这里是 GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES ⇒ 机器界面「机器模式」/JEI 分类标题/
                //   Jade-TOP 模式名/样板总成模式页都显示 GTCEu 的官方译名「电路组装机」。
                //   现在换成主机**自己的**表 NanochipRecipeTypes.ASSEMBLY_COMPLEX（id gtlmodular:nanochip_assembly_complex
                //   ⇒ lang 键 gtlmodular.nanochip_assembly_complex = 「纳米芯片组装复合体」）。
                //   ⚠️ 它**不是**一张新空表：该表在构造时把电路组装机表登记成 GTCEu 的「代理表」，
                //      并在加载期把电路组装机的**同一批 GTRecipe 对象**索引进自己的 lookup
                //      （机制与"为什么不会丢配方/不会改数"的完整证据链见 NanochipRecipeTypes#ASSEMBLY_COMPLEX 的注释）。
                .recipeType(NanochipRecipeTypes.ASSEMBLY_COMPLEX)
                .recipeModifier(NanochipAssemblyComplexMachine::nacRecipeModifier)
                .appearanceBlock(() -> NanochipBlocks.MESH_INTERFACE_CASING.get())
                .pattern(NanochipComplexPattern::build)
                .workableCasingRenderer(CASING_TEX, OVERLAY_TEX)
                .langValue("纳米芯片组装复合体")
                .tooltips(
                        Component.literal("NAC, 纳米芯片组装复合体").withStyle(ChatFormatting.GOLD),
                        // ★★ 0.3.132【队长令：方块描述只保留 NH 原版那段 + 一行"允许用样板总成"】★★
                        //   队长原话：「鼠标放到主机上的描述，把你那些怎么操作的都给删掉，
                        //   只保留 NH 原版的描述，然后加上一个允许使用样板总成，因为能量消耗巨大直接从电网取电」
                        //   ⇒ 这一段（5 行，含新增的绿色"允许使用样板总成"那行）**全部写死在
                        //     `gtlmodular.nanochip.complex.desc` 这一个键里**（zh/en 两份），
                        //     这里只挂一个键 ⇒ 以后改文案只动语言文件，不用碰代码。
                        //   ⚠ 被撤掉挂载的键（语言文件里仍保留、可一行恢复）：
                        //     complex.direct / complex.full / complex.mode / complex.calib / complex.materials
                        Component.translatable("gtlmodular.nanochip.complex.desc"),
                        // ★ 结构尺寸留着：它不是"怎么操作"，是这台机器的客观参数
                        //   （原版 NH 的方块描述里也有这一类尺寸行）。
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
        for (NanochipModuleKind kind : NanochipModuleKind.values()) {
            MultiblockMachineDefinition def = NanochipModContext.machineRegistrate()
                    .multiblock(kind.machineId(), holder -> new NanochipModuleMachine(holder, kind))
                    .rotationState(RotationState.ALL)
                    .recipeType(kind.recipeType())
                    .recipeModifier(NanochipModuleMachine::moduleFlexibleOverclock)
                    .appearanceBlock(() -> NanochipBlocks.MESH_INTERFACE_CASING.get())
                    .pattern(NanochipComplexPattern::buildModule)
                    // 模块运行耗材（原版各模块的专属机制：浸泡液 / 净化水 / 超导冷却液 / 培养基）
                    .beforeWorking(NanochipModuleUpkeep::onBeforeWorking)
                    .onWorking(NanochipModuleUpkeep::onWorking)
                    // 【§75】每个模块用【自己的】正脸 overlay。改之前这里传的是 OVERLAY_TEX（主机那张），
                    // 于是 11 个模块的正脸全都长成主机的脸 —— 这是与 NH 的真实偏差，本轮修掉。
                    // NH 侧逐个模块类都引用自己那一套 OVERLAY_FRONT_*（字节码见 §75）；
                    // 「kind ↔ NH 图标集」的唯一来源是 NanochipModuleKind 的第 2 个构造参数
                    // （overlayPrefix()），它在那边由构造器不变量与贴图目录名锁死（见 moduleOverlayTex 注释）。
                    .workableCasingRenderer(MODULE_CASING_TEX, moduleOverlayTex(kind))
                    .langValue(kind.nameZh())
                    .tooltips(
                            Component.literal("§7NAC 模块 · §f" + kind.nameEn()).withStyle(ChatFormatting.GRAY),
                            Component.translatable(kind.langKey("role")),
                            Component.translatable(kind.langKey("desc")),
                            kind.unlocksFullMode()
                                    ? Component.translatable("gtlmodular.nanochip.module.gate")
                                    : Component.translatable("gtlmodular.nanochip.module.stack"),
                            Component.translatable("gtlmodular.nanochip.module.innate"),
                            // ★ 2026-09-19：简单物流模式怎么给本模块供料（插样板总成 / 镜像仓 → 写样板 → 下单）。
                            //   惰性（translatable），注册期无重活 —— check_registration_path.js ② 的门槛。
                            Component.translatable("gtlmodular.nanochip.module.simple_logistics"))
                    .register();
            NANOCHIP_MODULES.put(kind, def);
            NanochipModContext.logger().info("[gtlmodular] NAC 模块已注册: {} ({})",
                    def.getId(), kind.nameZh());
        }
    }
}
