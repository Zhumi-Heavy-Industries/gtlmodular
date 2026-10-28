package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.WorkableElectricMultipleRecipesMachine;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

import java.util.ArrayList;
import java.util.List;

/**
 * NAC 模块机（11 种模块共用一台实现，行为由 {@link NanochipModuleKind} 决定）。
 *
 * <p>每个模块本体都是一台<b>可独立运行</b>的电力多方块（沿用自己在
 * {@link NanochipModuleKind#recipeType()} 里指定的真实配方表，例如切割室 = 切割机、
 * 线材描绘器 = 线材轧机），因此玩家即使没建 NAC 也能单独用；
 * 一旦它成形并挂在附近的 NAC 上，就会按种类为 NAC 提供加成（见
 * {@link NanochipAssemblyComplexMachine#moduleEuMultiplier} /
 * {@link NanochipAssemblyComplexMachine#moduleDurationMultiplier}）。
 *
 * <p>照抄原版规格：
 * <ul>
 *   <li><b>模块天生无限并行</b>（{@link #getMaxParallel()} = {@link Integer#MAX_VALUE}）；</li>
 *   <li>超频<b>封顶 2/2</b>，且配方时长不会被压到 5 秒以下。</li>
 * </ul>
 */
public class NanochipModuleMachine extends WorkableElectricMultipleRecipesMachine
        implements IModularMachineModule<NanochipAssemblyComplexMachine, NanochipModuleMachine>,
        IMachineLife, IDisplayUIMachine, NanochipStructureRefresh.Refreshable {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            NanochipModuleMachine.class,
            WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /**
     * ★★ 2026-11-12【模块的成型判定<b>完全交回上游</b>（GTCEu / gtlcore 自己那套）】★★
     *
     * <p>队长原话：「<b>让那个模块的成型也归上游</b>」。背景是实机日志逐字证据——
     * 模块报「尚未成形」时逐格给出的原因是「{@code 该格实际=minecraft:air}（期望
     * {@code gtlcore:component_assembly_line_casing_lv}）」这类<b>缺方块</b>、
     * 或「{@code 该格实际=gtlmodular:nanochip_reinforcement_casing}（期望
     * {@code nanochip_mesh_interface_casing} / {@code gtceu:ulv_input_bus}）」这类<b>放错方块</b>
     * ⇒ 结论本来就是"结构本身不符"（上游的结论），不是我们代码里有什么判据把它按住了。
     * 既然玩家已经能从逐格原因里看懂"为什么不成型"，那"要不要判成型"这件事就没有理由再经我们的手。
     *
     * <h3>开关语义</h3>
     * {@code true}（默认 = 交回上游）⇒ 本机<b>不再</b>用我们自己的附加判定过滤模块的成型/不成立：
     * <ul>
     *   <li>{@link #checkPatternWithTryLock()} 不再跑 {@link NanochipExactPrecheck}
     *       （那一步是"昂贵匹配之前的最后一道'必然不成形'闸"），原样交回 {@code super}；</li>
     *   <li>{@link #asyncCheckPattern(long)} 的未成形侧不再跑
     *       {@link NanochipStructureCheckGate} 的"预检/签名/失败格证据"三件套
     *       （它们决定"该不该跑昂贵匹配" ⇒ 也属于我们会拦下上游匹配的判定），
     *       原样交回 {@code super.asyncCheckPattern(periodID)}；</li>
     *   <li>{@link #checkPatternWithLock()} 的"同 tick 折叠"也不再生效（同一口径：它是一道
     *       我们自己加在上游判定链上的门）。</li>
     * </ul>
     * 于是本机结构的"成形 / 不成形"结论<b>只</b>出自上游：
     * {@code MultiblockControllerMachine.asyncCheckPattern}
     * → {@code checkPatternWithTryLock()} / {@code checkPatternWithLock()}
     * → {@code MultiblockState.onBlockStateChanged} 这条链
     * （本机继承 {@code WorkableElectricMultipleRecipesMachine}，<b>没有</b>改它这一环的任何判据）。
     *
     * <p>{@code false} = 逐字恢复本次改动之前的行为（模块侧也过精确预检 + 变化闸 + 同 tick 折叠）。
     *
     * <h3>刻意保留（都不参与"成形/不成形"的结论）</h3>
     * <ul>
     *   <li><b>本机从来没有碰过锁</b>：{@link #checkPatternWithTryLock()} / {@link #checkPatternWithLock()}
     *       在本轮之前就是干净地调 {@code super}（锁的取/还全部由 gtlcore/GTCEu 自己那段代码负责，
     *       我们<b>没有</b>在本机加 {@code NanochipPatternLockHardening} 那类包装 —— 那是主机侧的事）
     *       ⇒ 默认开之后本机这两条覆写逐字等于上游，锁的形态与上游一字不差；</li>
     *   <li><b>"尚未成形"逐格原因诊断</b>（{@link #logUnformedReason()}）：只读
     *       {@code MultiblockState.error}（那正是上游写下的原因），不参与任何判定；</li>
     *   <li><b>功能类</b>：模块↔主机的连接注入、{@link #getHostScanPositions()}、
     *       主机侧失效宽限期（见 {@code NanochipAssemblyComplexMachine}）、仓位清单、
     *       界面 / Jade / 预览 —— 一条未动。</li>
     * </ul>
     */
    public static final boolean NAC_MODULE_FORMATION_BY_UPSTREAM = true;

    /**
     * ★ 异步结构检查的节流器（2026-09-16 止血）。
     *
     * <p>原因与实测证据见 {@link NanochipStructureCheckThrottle}：模块与主机共用 GTCEu 的**单线程**
     * 异步检查池，结构不成形时每 ~1 秒全量重扫，服务器线程会在 {@code patternLock} 上死等。
     *
     * <p><b>★【2026-09-18 第三轮】模块<b>不</b>加便宜预检 —— 这是有意的，不是漏了：</b>
     * <ul>
     *   <li>主机（{@link NanochipAssemblyComplexMachine}）的图案包围盒是 <b>63×49×63 = 194481 格</b>，
     *       所以才需要 {@link NanochipStructurePrecheck} 去挡；</li>
     *   <li>模块的图案是 7×{@code (3 + offsetY)}×7，最大 7×10×7 = <b>490 格</b>
     *       （{@link com.gtlmodular.common.data.nanochip.NanochipComplexPattern#buildModule}），
     *       单次匹配成本比主机小 <b>≈400 倍</b> ⇒ 预检省下的那点开销不值得多一份"预检与图案会不一致"的维护面；</li>
     *   <li>而 {@link NanochipStructureCheckThrottle#UNFORMED_SKIP} 本轮由 6 调回 <b>1</b>（免等待），
     *       对模块是净收益：模块成形同样是"放下最后一块 ⇒ 下一轮检查即成"，
     *       而它的单次成本本来就小。</li>
     * </ul>
     *
     * <p><b>★【2026-09-18 第四轮】模块<b>要</b>过「无变化就不重跑」闸</b>
     * （{@link NanochipStructureCheckGate}）：队长给的频率上限是<b>每台机器</b> ≤0.2 次/秒，
     * 模块也算"每台机器"。模块没有 16 点预检 ⇒ 签名固定用
     * {@link NanochipStructureCheckGate#UNKNOWN_SIGNATURE}（常量 ⇒ 闸按"没变"处理），
     * 于是判据落在<b>"上次失败那一格变了没有"</b> + 地板上：
     * 一条常驻的错方块（模块 V/P/Z 位放错）只会以 ≤1/30 秒 的间隔重试，而不是每秒一次；
     * 而玩家一补上正确方块（那一格变了）⇒ 立刻匹配 ⇒ 模块成形照样"免等待"。
     *
     * <p>★★ 2026-11-12【成型判定交回上游】：以上这段"闸"的调度只在
     * {@link #NAC_MODULE_FORMATION_BY_UPSTREAM} 为 {@code false} 时生效（见
     * {@link #asyncCheckPatternUnformed}）；默认 {@code true} 时由上游自己的 periodID 轮询驱动。
     */
    private final NanochipStructureCheckThrottle structureCheckThrottle = new NanochipStructureCheckThrottle();

    /** ★【2026-09-18 第四轮】与主机同一套变化闸（模块侧签名用常量哨兵，理由见上）。 */
    private final NanochipStructureCheckGate structureCheckGate = new NanochipStructureCheckGate();

    /**
     * ★【2026-09-21 A+（队长拍板「上」）】模块侧的<b>精确预检</b>（惰性解析，见 {@link #exactPrecheck()}）。
     *
     * <p>身份格表由 {@code NanochipComplexPattern.buildModule} 在构建本模块图案时登记
     * （只含"唯一方块 / 严格空气"的角色：底座 {@code P}/{@code Z}、塔身角色 {@code MESH}/
     * {@code REINFORCEMENT}、空格位 {@code ' '}），见 {@link NanochipExactPrecheck#forModule(String)}。
     * <b>不是</b>主机那套 16 点预检（模块图案 ≤490 格，主机的 {@code NanochipStructurePrecheck}
     * 仍然不引 —— 见上面那段"有意不加便宜预检"的说明）。
     */
    private NanochipExactPrecheck exactPrecheck;

    /**
     * 解析本模块的精确预检实例（按 {@code getDefinition().getId().getPath()} 查登记表）。
     *
     * @return 实例；<b>没登记过 / 定义还取不到 ⇒ {@code null}</b>（= 本台不做精确预检，一律放行）
     */
    private NanochipExactPrecheck exactPrecheck() {
        NanochipExactPrecheck local = this.exactPrecheck;
        if (local == null) {
            net.minecraft.resources.ResourceLocation id =
                    getDefinition() == null ? null : getDefinition().getId();
            if (id == null) {
                return null;
            }
            local = NanochipExactPrecheck.forModule(id.getPath());
            if (local == null) {
                return null;                       // 放行：绝不把"没登记"当成"不成形"
            }
            this.exactPrecheck = local;
        }
        return local;
    }

    /**
     * ★【2026-09-21 A+（队长拍板「上」）】{@code checkPatternWithTryLock()} 的覆写 ——
     * <b>昂贵整图案匹配之前的最后一道"必然不成形"闸</b>（与主机同款机制，字节码依据见主机那边的长注释）。
     *
     * <p>gtlcore {@code @Overwrite} 的 {@code asyncCheckPattern} 第 37–41 字节码是
     * {@code if (!checkPatternWithTryLock()) return;} —— 昂贵匹配与 {@code patternLock} 都在那一句里。
     * 本方法返回 {@code false} ⇒ 14 台模块<b>既不拿锁也不跑匹配</b>（模块与主机共用 GTCEu 那一个
     * <b>单线程</b>检查池，少占一次就少一份对主机那一趟的挤压）。
     *
     * <h3>零语义变更</h3>
     * 判据是<b>必要条件</b>（唯一方块谓词格 / {@code air()} 格逐格精确判定），逐类论证见
     * {@link NanochipExactPrecheck} 类注释；<b>拿不准的一律 {@code mayForm=true} ⇒ 原样 {@code super}</b>。
     * 另有 {@link NanochipExactPrecheck#MODULE_VALVE_MS} 兜底阀门：连续判"必然不成形"时每 10 秒放行一次
     * —— 它的存在是为了让"模块身份格表万一与图案脱节"这种<b>未取证风险</b>最坏只退化成"成形慢 ≤10 秒"，
     * 而<b>不会</b>变成"模块永不成形"；它不影响任何"成形/不成形"的结论（结论永远由 GTCEu 给）。
     *
     * <h3>可逆性</h3>
     * 删掉本方法 + {@link #exactPrecheck} 字段 + {@link #exactPrecheck()} 即完全回到本日之前的行为。
     *
     * <p>★★ 2026-11-12【成型判定交回上游】：本覆写就是"我们自己的判据"里最直接的一条（它会替上游
     * 回答"这次不成形"），所以受 {@link #NAC_MODULE_FORMATION_BY_UPSTREAM} 管辖，<b>默认不生效</b>
     * —— 开关为 {@code true} 时本方法逐字等价于 {@code super.checkPatternWithTryLock()}。
     */
    @Override
    public boolean checkPatternWithTryLock() {
        // 成型判定只由结构定义（pattern）决定：这里不再用精确预检替上游回答「这次不成形」。
        return super.checkPatternWithTryLock();
    }

    // ==========================================================================================
    // ★【2026-09-21 队长拍板「上」】NH 报告 §⑤ 可借鉴清单 #1 ——「同 tick 折叠」（模块侧）
    //   与主机 NanochipAssemblyComplexMachine#checkPatternWithLock 同一套机制、同一套取舍；
    //   完整的字节码依据与「为什么必须配一个下一 tick 的兑现点」写在主机那一份的长注释里。
    // ==========================================================================================
    /**
     * ★【2026-09-21 队长拍板「上」】{@code checkPatternWithLock()} 的覆写 ——
     * <b>同一个服务端 tick 内的重复调用折叠成一次真判定</b>（依据：{@code _census/NH的NAC成型机制_调研.md}
     * §⑤ 可借鉴清单 #1；队长 2026-09-21 原话「<b>上</b>」，并把窗口钉死成 1 个服务端 tick）。
     *
     * <p>模块侧同样吃这条路径：{@code MultiblockState.onBlockStateChanged} 在 {@code isFormed()==true} 时
     * 当场同步跑整场匹配。14 台模块与主机共用 GTCEu 那一个单线程检查池（见
     * {@link NanochipStructureCheckThrottle}），少跑一次就少挤主机一次。
     *
     * <p>返回值只可能是 {@code super.checkPatternWithLock()} 的真实结果（本次真跑，或同一 tick 内上一次
     * 真跑的结果）；跨 tick 一律重跑；拿不到世界/客户端侧 ⇒ 直接 {@code super}。
     * 「成形」方向的折叠由 {@link #confirmFoldedPattern()} 在下一 tick 兑现（理由见主机侧注释：
     * 成形且无错的控制器没有任何周期兜底重查）。
     *
     * <p>可逆性：删掉本方法 + {@link #foldTick}/{@link #foldValue}/{@link #foldDebt}
     * + {@link #confirmFoldedPattern()} + {@code tickHostState()} 里那一行调用 ⇒ 逐字回到本轮之前。
     *
     * <p>★★ 2026-11-12【成型判定交回上游】：本覆写属于"我们自己加在上游判定链上的一道门"
     * （折叠缓存会把同一 tick 内第 2..K 次 {@code checkPatternWithLock()} 直接换成上一次的结果），
     * 所以它<b>一并</b>受 {@link #NAC_MODULE_FORMATION_BY_UPSTREAM} 管辖 —— 默认（{@code true}）时
     * 本方法第一行就 {@code return super.checkPatternWithLock()}，三个 {@code fold*} 字段恒为初值
     * ⇒ {@link #confirmFoldedPattern()} 每 tick 只做一次 {@code volatile} 读后立刻返回。
     */
    @Override
    public boolean checkPatternWithLock() {
        // 成型判定只由结构定义决定：同 tick 折叠也撤掉，避免「缓存值」参与成形结论。
        return super.checkPatternWithLock();
    }

    /**
     * 下一 tick 兑现「被折叠掉的那次成形判定」（与主机同名方法同款；用 gtlcore 自己的重判定入口，
     * 不复制任何一行 GTCEu 分支）。拿不到 {@code patternLock}（异步线程正在跑匹配）⇒ 保留欠账、不阻塞。
     */
    private void confirmFoldedPattern() {
        if (!this.foldDebt) {
            return;
        }
        Level level = getLevel();
        if (level == null || level.isClientSide || !isFormed()) {
            this.foldDebt = false;                            // 已不在「成形」这一侧 ⇒ 无需兑现
            return;
        }
        MultiblockState state = getMultiblockState();
        if (state == null) {
            this.foldDebt = false;
            return;
        }
        if (!getPatternLock().tryLock()) {
            return;                                          // 异步线程在跑匹配 ⇒ 下一 tick 再试
        }
        try {
            // ★★ 0.3.131【① 修 NPE（与主机同款，点位 4/4）】不许再调 state.getCache() ★★
            //   `MultiblockState.getCache()` 的方法体是 `cache.stream().map(BlockPos::of).collect(...)`
            //   （`_census/_jtmp/MBState_c.txt:298-308`）⇒ cache 字段为 null 时**它自己抛 NPE**、
            //   永远返回不了 null（于是 `for (BlockPos pos : state.getCache())` 是必炸点）。
            //   cache 是 public 字段（同文件 `:35`）⇒ 直接读字段才真的 null 安全
            //   （同款写法 MoltenPatternAssemblyPartMachine.java:1908-1911）。
            //   cache==null ⟺ 该 state 从没跑过整场匹配（`clean()` 只在 BlockPattern.checkPatternAt 开头调，
            //   `_census/_jtmp/BlockPattern_c.txt:188`）⇒ 本 tick 如实放弃兑现，下一 tick/下一次方块变更再来。
            if (state.cache == null) {
                this.foldDebt = false;                       // 无触发格可挑 ⇒ 放弃兑现（不会误判成形）
                return;
            }
            var cacheIterator = state.cache.longIterator();
            while (cacheIterator.hasNext()) {
                BlockPos pos = BlockPos.of(cacheIterator.nextLong());
                if (pos.equals(getPos())) {
                    continue;                                // 控制器那一格走的是「失效」分支
                }
                BlockState bs = level.getBlockState(pos);
                if (bs.getBlock() instanceof ActiveBlock
                        || level.getBlockEntity(pos) instanceof IMachineBlockEntity) {
                    continue;                                // 会被 gtlcore/gtladditions 的早退规则吃掉
                }
                this.foldDebt = false;
                state.onBlockStateChanged(pos, bs);          // ★ 判定与后续分支全部由 gtlcore 自己执行
                return;
            }
        } finally {
            getPatternLock().unlock();
        }
        this.foldDebt = false;                               // 找不到合格触发格 ⇒ 放弃兑现（不会误判成形）
    }

    /** 上一次「真判定」落在哪个服务端 tick；{@link #FOLD_NO_TICK} = 本机还没跑过真判定。 */
    private volatile long foldTick = FOLD_NO_TICK;

    /** 上一次真判定的返回值 —— 只对 {@link #foldTick} 那一个 tick 有效。 */
    private volatile boolean foldValue;

    /** 上一次真判定是「成形」，且之后本 tick 内又被折叠过 ⇒ 下一 tick 必须补一次真判定。 */
    private volatile boolean foldDebt;

    /** 「还没有过真判定」的哨兵（只做 {@code ==} 比较，不做减法 ⇒ 不会像 gameTime 那样溢出）。 */
    private static final long FOLD_NO_TICK = Long.MIN_VALUE;

    @Override
    public void asyncCheckPattern(long periodID) {
        // 成型判定只由结构定义（pattern）决定：预检 / 变化闸 / 计数节流都不再参与，
        // 重扫节奏交还上游 searchingTask（它自带 (offset+periodID)%4 过滤）。
        // 注意：未成形的逐格原因日志必须照旧打，那是玩家唯一能看懂「为什么不成形」的地方。
        super.asyncCheckPattern(periodID);
        if (!isFormed()) {
            logUnformedReason();
        }
        reportRefreshResult();
    }

    /**
     * 未成形侧原样的判定链（<b>只在</b>{@link #NAC_MODULE_FORMATION_BY_UPSTREAM} 为 {@code false} 时进入）：
     * <b>只读相位观测 → 变化闸 → 计数节流</b>，返回 {@code true} = 可以跑上游的昂贵整图案匹配。
     *
     * <p>开关默认 {@code true} ⇒ 本方法<b>没有调用点</b>（保留原样，一行开关即可复活；这类方法体
     * 留在这里不产生任何运行期影响，也不与既有的"只在变化时重跑"口径冲突）。
     *
     * @param periodID   上游这一轮的 periodID（原样喂给只读的相位观测）
     * @param startNanos 本轮起点（调用方取一次，供闸算间隔/耗时）
     * @return 是否允许调用 {@code super.asyncCheckPattern(periodID)}
     */
    private boolean asyncCheckPatternUnformed(long periodID, long startNanos) {
        Level unformedLevel = getLevel();
        if (unformedLevel == null) {
            return false;
        }
        // ★ 2026-09-21（队长要的"数字"）：本行只数一次"未成形侧轮询"，**不参与任何判定**。
        //   模块侧没有预检 ⇒ 裸 return 恒为 0（本计数与"到达变化闸的次数"相等），这是对的。
        //   第二个参数是只读的相位观测（上游自带的 (offset+periodID)%4 过滤），
        //   理由见 NanochipStructureCheckGate 的 FASTPASS_PHASE_VALVE_MS javadoc。
        this.structureCheckGate.noteUnformedPoll(getPos(),
                NanochipStructureCheckGate.gtceuReachesFullMatchThisPoll(getHolder().getOffset(), periodID));
        if (!this.structureCheckGate.shouldRunFullMatch(unformedLevel,
                NanochipStructureCheckGate.UNKNOWN_SIGNATURE, startNanos)) {
            return false;
        }
        return this.structureCheckThrottle.shouldRun(false);
    }

    // ---------- 手动「立即更新结构」（★ 2026-09-19 队长需求） ----------

    /** 上一次点「立即更新结构」的人（服务端专用，**不同步**）。 */
    private ServerPlayer pendingRefreshRequester;

    /**
     * 「立即更新结构」按钮的服务端入口（{@link NanochipStructureRefresh.Refreshable}）。
     *
     * <p>与主机同款：只解开三道门（变化闸 / 计数节流 / 未成形日志限流），自己不跑结构查询 ——
     * 门一开，GTCEu 的 searchingTask 下一轮必扫到本机。<b>模块侧没有 16 点预检</b>，
     * 所以本方法之后必然真的跑一次全量匹配（{@link #asyncCheckPattern} 的 {@code attemptedUnformed} 分支
     * 一定会到）⇒ 请求人最迟约 1 秒就拿到结论，不需要额外兜底。
     *
     * <p>★★ 2026-11-12【成型判定交回上游】：{@link #NAC_MODULE_FORMATION_BY_UPSTREAM} 默认开时，
     * 变化闸与计数节流都<b>不参与</b>判定 ⇒ 上游的 searchingTask 本来就每轮都扫到本机，
     * 本方法的作用退化成"把限流日志的记忆清零 + 立刻用上游读数回一次结论"（见
     * {@link #asyncCheckPattern} 里那个 {@code !loggedFormed} 分支）—— 按钮的功能与结论**照旧**，
     * 只是结构匹配的节奏改由上游自己掌握。上面两句 {@code reset()} 保留不删：一行开关（置 false）
     * 就要立刻恢复到"由我们放行"的旧行为，删掉会让回退路径不完整。
     */
    @Override
    public void requestStructureRefresh(ServerPlayer requester) {
        this.pendingRefreshRequester = requester;
        this.structureCheckGate.reset();
        this.structureCheckThrottle.reset();
        this.lastUnformedLogTick = -10_000L;
        if (isFormed() && !getMultiblockState().hasError()) {
            reportRefreshResult();
            return;
        }
        if (requester != null) {
            requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_REQUESTED)
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    /** 把「手动刷新」的结论回给请求人（成形 / GTCEu 报出的首个不符处），并清掉请求人。 */
    private void reportRefreshResult() {
        ServerPlayer requester = this.pendingRefreshRequester;
        if (requester == null) {
            return;
        }
        this.pendingRefreshRequester = null;
        if (isFormed()) {
            requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_OK)
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_FAIL)
                .withStyle(ChatFormatting.RED));
        String detail = NanochipAssemblyComplexMachine.describeStructureFailure(getMultiblockState());
        requester.sendSystemMessage(Component.literal("§c· §7" + kind.nameZh() + " @"
                + getPos().toShortString() + "：" + (detail == null
                ? "GTCEu 没留下逐格原因：数量类失败 / 区块未加载等路径不写 error"
                : detail)));
    }

    /** 模块"一直没成形"时的限流诊断（每 {@value #UNFORMED_LOG_INTERVAL_TICKS} tick 最多一条）。 */
    private void logUnformedReason() {
        Level lv = getLevel();
        if (lv == null || lv.isClientSide || isFormed()) {
            return;
        }
        long now = lv.getGameTime();
        if (now - this.lastUnformedLogTick < UNFORMED_LOG_INTERVAL_TICKS) {
            return;
        }
        this.lastUnformedLogTick = now;
        String detail = NanochipAssemblyComplexMachine.describeStructureFailure(getMultiblockState());
        String reason = detail == null
                ? "GTCEu 没留下逐格原因：数量类失败 / 区块未加载等路径不写 error"
                : detail;
        // ★ 2026-09-21 降噪（队长：「…要好久，尤其是主机」+ 日志每分钟 90 条）：
        //   原来这里只按本机 10 秒限流 ⇒ 12 台模块各自到点 ⇒ 72 条/分，且结论/原因往往一个字都没变。
        //   现在交给日志闸：同一结论 + 同一原因 10 秒内只打一条 INFO，重复降到 debug；
        //   原因变了（换了另一格不符 / 从"没原因"变成有原因）⇒ 照打。
        if (!this.structureLogGate.shouldEmitInfo(LOG_CONCLUSION_UNFORMED, reason,
                structureLogLabel(), now)) {
            if (com.gtlmodular.GTLModular.LOGGER.isDebugEnabled()) {
                com.gtlmodular.GTLModular.LOGGER.debug("[gtlmodular] NAC 模块 尚未成形 @{}（{}）：{}",
                        getPos(), kind.nameZh(), reason);
            }
            return;
        }
        com.gtlmodular.GTLModular.LOGGER.info("[gtlmodular] NAC 模块 尚未成形 @{}（{}）：{}",
                getPos(), kind.nameZh(), reason);
    }


    // 【已删除】模块找 NAC 的半径扫描（HOST_SCAN_R/HOST_SCAN_VER）：
    // 连接改由 NAC 按结构的 F 位注入（见 findAndConnectToHost 的注释），
    // 原版同样没有任何半径扫描 —— 不留兼容回退，避免两条路径互相打架。

    /** 模块配方时长下限：与原版一致 5 秒。 */
    public static final int MIN_DURATION_TICKS = 100;

    /**
     * ★ 2026-09-19（队长「产出要降低到 1 出 8」）：简单物流模式下<b>组装矩阵</b>的产出倍率。
     *
     * <p>当时的做法是：配方数据（JSON 与两份规格 json 的 {@code outCount}）仍是 <b>16</b>，
     * 只有简单模式在结算处乘本值 ⇒ 实出 <b>8</b>。详见 {@link #scaleOutputsForSimpleLogistics}。
     *
     * <p>★ <b>2026-09-21 队长拍板：本值改为 {@code 1.0D}（= 运行期不再缩放）</b>。
     * 队长原话：「组装矩阵的 JSON 16 → 8（让 JEI 也显示 8，跟运行期一致）」——
     * 于是产出<b>直接写进配方</b>（30 条 {@code assembly_matrix/*.json} 与两份规格
     * {@code _census/nac_matrix_spec.json} / {@code _census/nac_cw_matrix_spec.json} 的
     * {@code outCount} 全部 16 → 8，留痕见规格的 {@code outCountBefore16} / {@code outCountBefore8}），
     * 本值置 {@code 1.0D} 后 {@link #scaleOutputsForSimpleLogistics} 最上面那条
     * {@code >= 1.0D} 短路会原样返回配方。
     *
     * <p>★ <b>同一天再按队长令改成阶梯</b>：「修改电路产出的数量，让每个系列最基础的产出八，4，2，1
     * 最后每个系列的主机产出一份」⇒ 配方里的产出由「一律 8」变成
     * <b>处理器 8 / 装配 4 / 电脑 2 / 主机 1</b>（脚本 {@code _census/_apply_matrix_output_ladder.js}，
     * 规格留痕 {@code outCountBeforeLadder}）。<b>本值仍是 {@code 1.0D}</b> —— 阶梯已经在配方里，
     * 运行期再乘一层就会把每一档都缩错（处理器 8→4、主机 1→0）。
     *
     * <p>⇒ 三处一致：<b>JEI = 简单模式运行期 = 原版模式运行期 = 同一套阶梯</b>
     * （不再是「JEI 显示配方原文 16 / 机器实出 8」的两个真相）。
     * 原先写 {@code 0.5D} 的那一版属历史，保留本注释只为留痕。
     */
    public static final double SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER = 1.0D;

    private final NanochipModuleKind kind;

    /** 分流器规则表（只有 {@link NanochipModuleKind#SPLITTER} 会用）。 */
    private final NanochipSplitterRules splitterRules = new NanochipSplitterRules();

    /** 「尚未成形」诊断的限流间隔（tick）；200 = 10 秒。 */
    private static final long UNFORMED_LOG_INTERVAL_TICKS = 200L;

    /**
     * 上次打「尚未成形」诊断的 gameTime。
     *
     * <p>⚠️ 哨兵**不能**用 {@code Long.MIN_VALUE}：判定式是 {@code now - last < INTERVAL}，
     * 而 {@code gameTime − Long.MIN_VALUE} 会溢出成负 ⇒ 恒小于阈值 ⇒ 日志永远不打（2026-09-19 实测踩过）。
     */
    private long lastUnformedLogTick = -10_000L;

    /**
     * ★ 2026-09-21 本机的结构状态日志闸（见 {@link NanochipStructureLogGate}）。
     *
     * <p>队长原话（2026-09-21）：「唉，现在一进游戏NAC相关的，都要重新加载结构，而且要好久，尤其是主机。」
     * 队长给的实机证据：{@code latest.log} 里进世界后 NAC 结构消息
     * {@code 19:35=23、19:38=28、19:39=77、19:40=91、19:41=90}，之后每分钟仍 80~90 条，
     * 绝大多数是**同一台机器、同一结论、同一原因**的重复。
     *
     * <p>本字段就是"本机上一次报过什么"的记忆：同一结论 10 秒内只打一条 INFO，
     * 重复的降到 {@code debug}；<b>状态变化（成形↔未成形）或原因变了照打</b>（队长要靠日志看状态）。
     * 记忆随机器实例/重载清零（{@link #onLoad()} 里 reset）⇒ 进世界后每台机器先报一次当前状态。
     */
    private final NanochipStructureLogGate structureLogGate = new NanochipStructureLogGate();

    /** 本机结构状态日志的结论名（同一个字段被"已成形/结构失效/尚未成形"三条共用，见闸门注释）。 */
    private static final String LOG_CONCLUSION_FORMED = "已成形";

    private static final String LOG_CONCLUSION_INVALID = "结构失效";

    private static final String LOG_CONCLUSION_UNFORMED = "尚未成形";

    /** 汇总行里本机的短标签（例：{@code 纳米芯片组装矩阵@(-74,69,-105)}）。 */
    private String structureLogLabel() {
        return kind.nameZh() + "@" + getPos().toShortString();
    }

    private long lastRouterTick = Long.MIN_VALUE;
    private TickableSubscription routerTickSub;

    /**
     * 主机的世界坐标（<b>只由主机写</b>：{@code IModularMachineModule.connectToHost/removeFromHost}）。
     *
     * <p>★【2026-09-16 修「连接不稳」】本机<b>不再</b>在结构失效时 {@code removeFromHost} ——
     * 「已连接模块集合」的唯一权威是主机（见
     * {@link NanochipAssemblyComplexMachine#isModuleConnected}），模块侧只读结果。
     *
     * <h3>★【2026-10 模块框架对齐 · A4】补上 {@code @Persisted}（新增一个存档键）</h3>
     * <b>为什么</b>：官方那套（gtlcore {@code IModularMachineModule} + gtladditions 的
     * {@code forge_of_the_antichrist} / {@code SubspaceCorridorHubIndustrialArray} /
     * {@code SuprachronalAssemblyLine} 等模块）里，本字段是<b>持久化</b>的 ——
     * {@code _census/enhancedcore-src/.../API标准.md:232} 逐字写着
     * 「hostPosition（实现类 {@code @Persisted}）/host 引用」，且 EnhancedCore 对**真实 Anvil 存档**
     * 的取证给出它的落盘形态：{@code hostPosition} = <b>{@code {X:145,Y:101,Z:2}}</b>
     * （{@code _census/enhancedcore-src/docs/玩家向提示与Jade验收-2.8.9-20261001.md:277}）。
     * 我们原来只有 {@code @DescSynced} 那半边，导致"模块凭持久化坐标自己接回主机"的官方语义在本机不成立。
     *
     * <h3>键名与形态（新增存档键，向后兼容）</h3>
     * <ul>
     *   <li><b>键名 = {@code hostPosition}</b>（LDLib 托管字段机制用<b>字段名</b>做键；与
     *       {@code ItemStackTransfer} 的 {@code patternInventory.Items} 同一规则，
     *       见 {@code LazyPatternAssemblyPartMachine:298-303}）；</li>
     *   <li><b>形态 = {@code CompoundTag {X:int, Y:int, Z:int}}</b>（原版
     *       {@code NbtUtils.writeBlockPos} 布局，也正是上面那份 Anvil 证据的形态）
     *       —— LDLib 对 {@code BlockPos} 走 {@code syncdata/accessor/PositionAccessor}
     *       （{@code _ldlib_x/com/lowdragmc/lowdraglib/syncdata/accessor/PositionAccessor.class} 存在即证
     *       LDLib 认识这个类型）＋ {@code syncdata/payload/BlockPosPayload.class}；</li>
     *   <li><b>旧档缺键 ⇒ 不影响任何东西</b>：字段无初值 ⇒ 读档后仍是 {@code null} ⇒
     *       {@code getHostPosition()==null} ⇒ {@code findAndConnectToHost()} 的第一分支自然跳过、
     *       退到 {@code getHostScanPositions()}（本机返回空表）⇒ 返回 {@code false}。
     *       <b>不报错、不抛异常、不改任何判定</b>（退化 = 完全等价于本次改动之前的行为，
     *       连接照旧由主机侧按结构 F 位注入）。</li>
     *   <li><b>掉落物不带</b>：LDLib {@code @Persisted} 默认 {@code drop=false} ⇒ 把模块拆成物品时
     *       不会把"我在某某坐标属于某台主机"随身带走（与主机侧 {@code NBT_MODULE_SLOTS} 的
     *       {@code !forDrop} 同一条纪律）。</li>
     * </ul>
     * <p>字段本身仍<b>只由主机写</b>（{@code connectToHost} → {@code setHostPosition}；
     * {@code removeFromHost} → {@code setHostPosition(null)}）—— 持久化不改变写入者。
     */
    @Persisted
    private BlockPos hostPosition;

    /** 主机引用（<b>只由主机写</b>，同上）。 */
    private NanochipAssemblyComplexMachine host;

    // ---------- 界面用的「复合体连接」同步快照（★ 2026-09-16） ----------
    //
    // 为什么必须同步：GTCEu 的界面文案是在**客户端**由
    // IDisplayUIMachine.createUI 里挂的 ComponentPanelWidget.textSupplier 回调里调 addDisplayText 生成的，
    // 而 host / hostPosition 这两个字段只存在于服务端（不是 @DescSynced）⇒ 原来模块界面在客户端
    // 恒为 host == null ⇒ **永远显示「复合体连接: 未连接」**（玩家实测正是如此）。
    // 所以这里由服务端把"权威结果"算好，推给客户端；界面只读它。

    /** 权威连接判定结果（服务端算、客户端读；见 {@link #hostConnected()}）。 */
    @DescSynced
    private boolean syncedHostConnected;

    /** 连接详情文案（主机坐标 / 档位 / 已识别模块数；服务端算）。 */
    @DescSynced
    private String syncedHostInfo = "尚未被复合体登记；把它建在复合体基座的模块位上，或先让复合体成形";

    /** 是否正在吃复合体的电（服务端算）。 */
    @DescSynced
    private boolean syncedDrawingFromHost;

    /** 主机的配方等级（吃复合体电时，界面上的「最大配方等级」用；服务端算）。 */
    @DescSynced
    private int syncedHostTier;

    // ---------- ★ 2026-09-21 新增：能量缓存（wiki「能量缓存」段逐字实现） ----------
    //
    // wiki 原文（各模块页 |数据= 段）：
    //   ①「复合体检查结构时为每个模块设置缓存容量，公式为：模块缓存 = NAC最大输入功率 × (2 × 模块最大配方时间)」；
    //   ②「复合体每 20 tick 向各模块分配缓存电量；本模块优先级为 1（组装矩阵为 −1，最后补）」；
    //   ③（原版 tooltip）「模块若在等电，需要先把自己的缓存充到能容纳**整条配方**，保证不掉电」。
    //
    // 因此这里的模型是：**模块只花自己缓存里的电，缓存由主机每 20 tick 按优先级灌**；
    // 起跑前要求「缓存 ≥ 整条配方所需的电」⇒ 配方跑到一半绝不会因为主机抽不出电而停机。

    /** 本模块当前缓存（EU，服务端权威、同步给客户端做显示）。 */
    @DescSynced
    private long syncedBufferEU;

    /** 本模块缓存上限（EU）＝ NAC最大输入功率 × 2 × {@link NanochipModuleKind#maxRecipeTicks()}（同步给客户端做显示）。 */
    @DescSynced
    private long bufferCapacityEU;

    public long getBufferEU() {
        return syncedBufferEU;
    }

    public long getBufferCapacityEU() {
        return bufferCapacityEU;
    }

    /**
     * 主机在结构检查 / 每 20 tick 分配前设置缓存上限。
     *
     * <p>★ 单调放大：{@code capacity = max(公式值, 已设过的值)}，避免"公式值比实际最长配方还小 ⇒ 模块永远
     * 攒不满、判死"（我方配方的时长是按 GTL 真件推的，个别比 NH 页上的最大配方时间还长）。
     * 另外把已存电量夹到上限内。
     */
    public void setBufferCapacity(long capacity) {
        if (capacity > bufferCapacityEU) {
            bufferCapacityEU = capacity;
        }
        if (syncedBufferEU > bufferCapacityEU) {
            syncedBufferEU = bufferCapacityEU;
        }
    }

    /**
     * 保证缓存上限**至少**能覆盖整条配方（否则该配方永远起不来）。
     *
     * <p>只在主机真的按公式定过容量之后才允许放大（{@code bufferCapacityEU > 0}），
     * 免得脱机状态下自己长出一个容量来。
     */
    public void ensureBufferCovers(long fullRecipeEU) {
        if (bufferCapacityEU > 0L && fullRecipeEU > bufferCapacityEU) {
            bufferCapacityEU = fullRecipeEU;
        }
    }

    /** 主机灌电进本模块缓存（返回实际灌入量；电量已由主机自己的蓄电里扣掉）。 */
    public long fillBufferEU(long amount) {
        if (amount <= 0L || bufferCapacityEU <= 0L) {
            return 0L;
        }
        long room = bufferCapacityEU - syncedBufferEU;
        long take = Math.min(room, amount);
        if (take <= 0L) {
            return 0L;
        }
        syncedBufferEU += take;
        return take;
    }

    /** 从本模块缓存扣电（不够则一点不扣，返回 false ⇒ 调用方去置「等电」）。 */
    public boolean consumeBufferEU(long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (syncedBufferEU < amount) {
            return false;
        }
        syncedBufferEU -= amount;
        return true;
    }

    /** 清空缓存（结构失效 / 断开主机时调用：缓存是派生状态，不随存档走）。 */
    public void clearBufferEU() {
        syncedBufferEU = 0L;
    }

    /**
     * 能量缓存分配优先级：<b>组装矩阵 = −1</b>（最后补，别的模块先拿电），其余模块 = 1。
     * 见 wiki 组装矩阵页：「在所有模块中具有最低的能量缓存补充优先级（−1），其他模块会优先获得电力」。
     */
    public int energyPriority() {
        final boolean lowPriority = kind == NanochipModuleKind.ASSEMBLY_MATRIX;
        return lowPriority ? -1 : 1;
    }

    // ---------- ★ 2026-09-18 新增：本模块的仓位坐标清单（同步给客户端做「别的 mod 的仓也连片」） ----------
    //
    // 与 NanochipAssemblyComplexMachine 同款：模块自己也是一台多方块，插在它 V 位上的仓（不管来自哪个
    // mod）同样要吃「外壳换成接口机壳 47 格变体 + 与周围机壳连片」。清单格式 / 上限 / 同步纪律
    // 与主机**完全一致**（自检 _census/check_ctm_foreign.js 会现读比对两个 encodeNacSlots 方法体）。

    /**
     * ★ <b>本模块这一刻的仓位坐标清单</b>（服务端权威 → {@code @DescSynced} 推给客户端）。
     *
     * <p>格式与语义见 {@code NanochipAssemblyComplexMachine#syncedNacSlots}（同一个客户端解码器
     * {@code NacSlotIndex.decode}）：逗号分隔的十进制 {@code BlockPos.asLong()}，空串 = 未成形。
     *
     * <p>★ 2026-09-26（0.2.5）补上 {@code @Persisted}，理由与主机侧逐字相同（见那边字段注释）：
     * 只有 {@code @DescSynced} 时，重进世界后本清单不会再发布，客户端拿不到 ⇒ 吃这份清单的
     * 我们自己的方块掉回原材质。
     */
    @DescSynced
    @Persisted
    private String syncedNacSlots = "";

    /** 仓位清单上限（条）：超出即截断 + WARN（与主机同口径）。 */
    public static final int MAX_NAC_SLOTS = 256;

    /** 客户端只读：本模块发布给客户端的仓位清单（空串 = 拿不到 ⇒ 客户端按"不是仓位"处理）。 */
    public String nacSlotsEncoded() {
        return syncedNacSlots;
    }

    /** 服务端：结构状态变化时重建并发布清单（只在**变化**时写字段，避免刷爆同步通道）。 */
    private void publishNacSlots() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        String encoded = encodeNacSlots();
        if (!encoded.equals(syncedNacSlots)) {
            syncedNacSlots = encoded;
            // 诊断（0.2.5）：见证「重进世界后到底有没有再发布」这一次；条数=0 说明是空串。
            GTLModular.LOGGER.info("[gtlmodular] NAC仓位清单发布(模块): pos={} 条数={}",
                    getPos(), encoded.isEmpty() ? 0 : encoded.split(",").length);
        }
    }

    /** 结构失效 / 解散 / 拆除：清空清单（客户端索引随之丢掉本模块这一份）。 */
    private void clearNacSlots() {
        Level level = getLevel();
        if (level != null && level.isClientSide) {
            return;
        }
        if (!syncedNacSlots.isEmpty()) {
            syncedNacSlots = "";
            GTLModular.LOGGER.info("[gtlmodular] NAC仓位清单清空(模块): pos={}", getPos());
        }
    }

    /**
     * ★ 2026-09-27 <b>仓位清单心跳</b>（与 {@code NanochipAssemblyComplexMachine#tickNacSlotHeartbeat}
     * 逐条同构；本机 tick 钩子在 {@link #tickHostState()} 里每 tick 调一次）。
     *
     * <p>语义/性能口径见主机侧那份注释：稳态 = 每 20 tick 一次 {@code getParts().size()} 读；
     * 个数没变就 return（不建字符串、不碰世界、不重跑匹配）；变了才重建，写入仍走去重。
     *
     * @return {@code true} = 本次真的重建并（可能）发布了清单
     */
    private boolean tickNacSlotHeartbeat() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        if (++this.nacSlotHeartbeat % NAC_SLOT_HEARTBEAT_INTERVAL != 0) {
            return false;
        }
        if (!isFormed()) {
            return false;
        }
        int parts = getParts() == null ? 0 : getParts().size();
        if (parts == this.lastNacSlotPartCount) {
            return false; // ★ 稳态快路径（O(1)）
        }
        int previous = this.lastNacSlotPartCount;
        this.lastNacSlotPartCount = parts;
        publishNacSlots();
        GTLModular.LOGGER.info("[gtlmodular] NAC外壳连片：仓位清单心跳重发(模块) pos={} part数 {}→{} 清单={}",
                getPos(), previous, parts,
                syncedNacSlots.isEmpty() ? "空（0 条）" : syncedNacSlots.split(",").length + " 条");
        return true;
    }

    /**
     * ★ 把「结构里真实站着的 part」编码成逗号分隔的 {@code BlockPos.asLong()}。
     *
     * <p>与 {@code NanochipAssemblyComplexMachine#encodeNacSlots()} <b>逐字节一致</b>（客户端只有一个
     * 解码器）。数据来源 = GTCEu 的 {@code MultiblockControllerMachine.getParts()}，<b>不区分仓的来源</b>。
     */
    private String encodeNacSlots() {
        List<IMultiPart> parts = getParts();
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        int dropped = 0;
        for (IMultiPart part : parts) {
            if (part == null || part.self() == null) {
                continue;
            }
            if (kept >= MAX_NAC_SLOTS) {
                dropped++;
                continue;
            }
            if (kept > 0) {
                sb.append(',');
            }
            sb.append(part.self().getPos().asLong());
            kept++;
        }
        if (dropped > 0) {
            GTLModular.LOGGER.warn("[gtlmodular] CTM: 仓位清单超过上限 {} 条（结构里 {} 个 part @{}），"
                            + "已截断 {} 条 —— 被截断的那几格外壳保持原样（不参与连接纹理）。",
                    MAX_NAC_SLOTS, parts.size(), getPos(), dropped);
        }
        return sb.toString();
    }

    /** 连接快照的刷新节流（20 tick 一次，与主机侧的 F 位重算同频）。 */
    private static final int HOST_STATE_REFRESH_INTERVAL = 20;

    // ================= ★ 2026-09-27 仓位清单心跳（修「外壳连片刷新时机」，与主机同款） =================
    //
    // 模块自己也是一台多方块：插在它 V 位上的仓同样要吃「外壳换成接口机壳 + 与周围机壳连片」。
    // 判据用的还是它发布的 syncedNacSlots，而那份清单原来只在 onStructureFormed() 里发布一次
    // ⇒ 与主机侧完全同一条风险（见 NanochipAssemblyComplexMachine#tickNacSlotHeartbeat 的注释）。
    // 这里补同样一条**有界**心跳：每 NAC_SLOT_HEARTBEAT_INTERVAL tick 比一次 part **个数**，
    // 个数没变就 O(1) 返回；变了才重建字符串，写入仍受 publishNacSlots() 自身的去重保护。

    /** 仓位清单心跳间隔（tick）：1 秒一次。 */
    private static final int NAC_SLOT_HEARTBEAT_INTERVAL = 20;

    /** 仓位清单心跳的计数器（只增不减；初值 -1 = 下一次 tick 就跳一次）。 */
    private int nacSlotHeartbeat = -1;

    /** 上一次心跳时结构里的 part 个数（{@code -1} = 还没测过 ⇒ 下一次无条件测一次）。 */
    private int lastNacSlotPartCount = -1;

    private long lastHostStateTick = Long.MIN_VALUE;
    private TickableSubscription hostStateSub;

    public NanochipModuleMachine(IMachineBlockEntity holder, NanochipModuleKind kind) {
        super(holder);
        this.kind = kind;
    }

    public NanochipModuleKind getModuleKind() {
        return kind;
    }

    /** 分流器规则表（供界面读写）。 */
    public NanochipSplitterRules splitterRules() {
        return splitterRules;
    }

    // ---------- 原版机制 A：全板浸没的「浸泡液杂质」 ----------

    /** 每完成一次配方上涨的杂质百分点（配置可改，原版由流体量/槽容决定）。 */
    public static final double DEFAULT_IMPURITY_PER_RECIPE = 0.5D;

    /** 浸泡液杂质（%，原版：0-15% 省电、15-65% 正常、65-100% 多耗电）。 */
    @DescSynced
    private double impurity;

    /** 自动排液阈值（%，默认 100 = 跑满才排；到阈值就把浸泡液排掉、杂质归零）。 */
    @DescSynced
    private int autoFlushThreshold = 100;

    public double impurity() {
        return impurity;
    }

    public int autoFlushThreshold() {
        return autoFlushThreshold;
    }

    public void setAutoFlushThreshold(int threshold) {
        this.autoFlushThreshold = Math.max(1, Math.min(100, threshold));
    }

    /**
     * 杂质带来的 EU 倍率（照抄原版三段式）：
     * <pre>
     *   0%–15%   : EU ×(1 − (30 − 2y)/100)    y=0 时省 30%，y=15 时回到 1.00
     *   15%–65%  : EU ×1.00（默认行为）
     *   65%–100% : EU ×(1 + (y − 65)×2%)      y=100 时多耗 70%
     * </pre>
     */
    public double impurityEuMultiplier() {
        double y = impurity;
        if (y <= 15.0D) {
            return 1.0D - Math.max(0.0D, (30.0D - 2.0D * y) / 100.0D);
        }
        if (y < 65.0D) {
            return 1.0D;
        }
        return 1.0D + (y - 65.0D) * 0.02D;
    }

    /** 记录一次产出：涨杂质，到阈值自动排液（杂质归零）。 */
    private void onRecipeProduced() {
        if (kind != NanochipModuleKind.BOARD_PROCESSOR) {
            return;
        }
        double step = com.gtlmodular.common.config.GTLModularConfig.getNanochipImpurityPerRecipe();
        if (step <= 0.0D) {
            return;
        }
        impurity = Math.min(100.0D, impurity + step);
        if (impurity >= autoFlushThreshold) {
            impurity = 0.0D; // 排液：把用脏的浸泡液排掉
            if (getLevel() != null && !getLevel().isClientSide
                    && com.gtlmodular.common.config.GTLModularConfig.isNanochipDebugLogsEnabled()) {
                com.gtlmodular.GTLModular.LOGGER.info("[gtlmodular] 全板浸没装置自动排液 @{}（阈值 {}%）",
                        getPos(), autoFlushThreshold);
            }
        }
    }

    // ---------- 原版机制 B：组织器一族的「净化水等级」（光学 + 奇异/寰宇/超因果三台） ----------

    /**
     * 本机输入仓里等级最高的净化水等级（1~16；0 = 没供净化水）。
     * 原版：{@code Will drain X L/s of Waters when running to provide Bonuses}，
     * 供水等级越高加成越大；整合包里 {@code gtceu:grade_1..16_purified_water} 都在。
     *
     * <p>消费者 = {@link NanochipModuleKind#isWaterScaledOrganizer()} 那一族（光学 + 2026-09-18
     * 新增的奇异 / 寰宇 / 超因果优化组织器，它们的 {@code upkeepFluidId} 就是净化水）。
     */
    public int highestPurifiedWaterGrade() {
        int best = 0;
        for (com.lowdragmc.lowdraglib.side.fluid.FluidStack fs
                : NanochipModuleUpkeep.scanInputFluids(this)) {
            var id = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(fs.getFluid());
            if (id == null) {
                continue;
            }
            java.util.regex.Matcher m = PURIFIED_WATER.matcher(id.getPath());
            if (m.matches()) {
                best = Math.max(best, Integer.parseInt(m.group(1)));
            }
        }
        return best;
    }

    private static final java.util.regex.Pattern PURIFIED_WATER =
            java.util.regex.Pattern.compile("grade_(\\d+)_purified_water");

    /**
     * 水加成强度倍率（1.0 ~ 2.0）：原版「同类别加成互相叠加」，这里按供水等级线性放大，
     * 1 级 = 基础加成，16 级 = 基础加成的两倍。光学满校准时再翻倍（见 NAC 侧）。
     */
    public double waterStrength() {
        int grade = highestPurifiedWaterGrade();
        if (grade <= 0) {
            return 0.0D; // 没供水 → 这条模块不给加成（原版：Requires 2 Purified Waters to be supplied）
        }
        return Math.min(2.0D, 1.0D + (grade - 1) * (1.0D / 15.0D));
    }

    // ---------- 原版机制 C：蚀刻阵列的「激光源仓」 ----------

    /**
     * 本模块结构里等级最高的激光源仓等级（0 = 没装）。
     * 原版（GT5U {@code GT5U.tooltip.nac.module.etching_array.body.*}）：
     * <pre>
     *   In a fully calibrated Crystal NAC, the laser source hatch provides bonuses:
     *   Divides EU Cost by each Amperage Interval over 256A
     *   Divides Recipe Duration by (Hatch Tier - 9)
     * </pre>
     * GTCEu 的激光仓安培数按等级阶梯（LuV=256A 起，每级 ×4），所以等级就能推出安培数。
     */
    public int laserHatchTier() {
        int best = 0;
        for (var part : getParts()) {
            MetaMachine m = part.self();
            if (m instanceof com.gregtechceu.gtceu.common.machine.multiblock.part.LaserHatchPartMachine
                    && m instanceof com.gregtechceu.gtceu.api.machine.feature.ITieredMachine tiered) {
                best = Math.max(best, tiered.getTier());
            }
        }
        return best;
    }

    /** 激光仓安培数（LuV(6)=256A，每高一级 ×4）。 */
    public long laserAmperage() {
        int tier = laserHatchTier();
        return tier <= 0 ? 0L : (256L << (2 * Math.max(0, tier - 6)));
    }

    // ---------- 中央供电：模块没装能源仓时直接吃复合体的电 ----------
    //
    // 做法与「生态园」那套（本模组 MoltenModuleMachineBase + 地心涌动能量转换枢纽）完全一致 ——
    // 那是本整合包里已经跑通的成熟方案：
    //   ① getEnergyContainer() 换成主机代理（蓄电/电压/容量都读主机）；
    //   ② getTier()/getOverclockVoltage() 代理主机（gtlcore 算并行、GT 算超频都读这两个）；
    //   ③ 自建 RecipeLogic 覆写 handleRecipeWorking()：每 tick 直接向主机要电、自己推进进度，
    //      绕开「EU 由零件的能源仓 trait 消耗」那条路（模块没有能源仓时那条路上没有处理器）。
    // 装了能源仓的模块仍然走自己的电（两条路互斥，见 drawingFromHost）。

    private transient com.gregtechceu.gtceu.api.misc.EnergyContainerList hostEnergyList;

    /**
     * 能量容器：自带能源仓优先；没装就用复合体代理。
     * （界面那两行「最大功率/最大配方等级」读的也是它，所以模块界面能显示复合体的电压。）
     *
     * <p>★ 2026-09-16：判定条件由「host 引用非空 + host 成形」改为 {@link #hostConnected()}
     * （= 主机权威集合里有本机）—— 与界面上的「复合体连接」同一把尺子。
     */
    @Override
    public com.gregtechceu.gtceu.api.misc.EnergyContainerList getEnergyContainer() {
        com.gregtechceu.gtceu.api.misc.EnergyContainerList own = super.getEnergyContainer();
        if (own != null && own.getEnergyCapacity() > 0L) {
            return own;
        }
        NanochipAssemblyComplexMachine host = getHost();
        if (!hostConnected() || host == null) {
            return own;
        }
        if (hostEnergyList == null) {
            hostEnergyList = new com.gregtechceu.gtceu.api.misc.EnergyContainerList(
                    List.of(new HostEnergyContainer(this)));
        }
        return hostEnergyList;
    }

    /** 是否正在吃复合体的电（自带能源仓的模块为 false）。 */
    public boolean drawingFromHost() {
        var own = super.getEnergyContainer();
        return (own == null || own.getEnergyCapacity() <= 0L) && hostConnected();
    }

    /** 等级/电压代理复合体：gtlcore 的并行公式与 GT 的超频都读这两个值。 */
    @Override
    public int getTier() {
        if (drawingFromHost()) {
            NanochipAssemblyComplexMachine host = getHost();
            if (host != null) {
                return host.getTier();
            }
        }
        return super.getTier();
    }

    @Override
    public long getOverclockVoltage() {
        if (drawingFromHost()) {
            NanochipAssemblyComplexMachine host = getHost();
            if (host != null) {
                return host.getOverclockVoltage();
            }
        }
        return super.getOverclockVoltage();
    }

    /** 代理容器：蓄电/耗电都走复合体。 */
    private static final class HostEnergyContainer
            implements com.gregtechceu.gtceu.api.capability.IEnergyContainer {
        private final NanochipModuleMachine module;

        HostEnergyContainer(NanochipModuleMachine module) {
            this.module = module;
        }

        private NanochipAssemblyComplexMachine host() {
            NanochipAssemblyComplexMachine h = module.getHost();
            return (h != null && h.isFormed()) ? h : null;
        }

        @Override
        public long getEnergyStored() {
            NanochipAssemblyComplexMachine h = host();
            return h == null ? 0L : h.getTotalStoredEU();
        }

        @Override
        public long getEnergyCapacity() {
            NanochipAssemblyComplexMachine h = host();
            return h == null ? 0L : h.getTotalCapacityEU();
        }

        @Override
        public long getInputVoltage() {
            NanochipAssemblyComplexMachine h = host();
            return h == null ? 0L : h.getMaxInputVoltage();
        }

        @Override
        public long getInputAmperage() {
            return 1L;
        }

        @Override
        public boolean inputsEnergy(net.minecraft.core.Direction side) {
            return false; // 不从旁路充能，只由复合体供给
        }

        @Override
        public long acceptEnergyFromNetwork(net.minecraft.core.Direction side, long amount, long voltage) {
            return 0L; // 拒绝直接注入
        }

        @Override
        public long changeEnergy(long amount) {
            if (amount >= 0L) {
                return 0L; // 模块不回充复合体
            }
            // ★ 2026-09-21：吃主机的电改走**本模块自己的能量缓存**（缓存由主机每 20 tick 按优先级灌），
            //   所以这里不再直接向主机要电 —— 缓存不够就一点不扣，走 GT 的「等电」分支。
            long need = -amount;
            return module.consumeBufferEU(need) ? -need : 0L;
        }
    }


    // ---------- 分流器：通道调度 ----------

    @Override
    public void onLoad() {
        super.onLoad();
        // ★ 2026-09-21（队长：「一进游戏 NAC 相关的都要重新加载结构，而且要好久」）：
        //   本机 onLoad = 这台模块进世界的那一刻 ⇒ 清掉结构日志闸的本机记忆，
        //   让"进门后每台机器先把自己当前的结构状态报一条"这条硬需求成立
        //   （队长就是靠这几条确认谁成形了、谁还没成形；见 NanochipStructureLogGate#reset）。
        this.structureLogGate.reset();
        if (getLevel() != null && !getLevel().isClientSide) {
            // ★ 2026-09-27（外壳连片刷新时机）：跨世界/重载 ⇒ 心跳的 part 数基线作废，
            //   进世界后的第一次心跳无条件重测一次（清单本身由 @Persisted 从存档恢复）。
            this.nacSlotHeartbeat = -1;
            this.lastNacSlotPartCount = -1;
            // ★【2026-09-16】所有模块都订一个"连接快照"刷新（每 20 tick 一次）：
            //   主机的连接变化由 onHostLinked/onDisconnected 立刻推一次，这里只是兜底
            //   （同时也让界面上的"已识别 N 个模块"最多 1 秒陈旧）。
            unsubscribeHostStateTick();
            hostStateSub = subscribeServerTick(this::tickHostState);
            refreshHostSync();
        }
        if (kind.isRouter() && getLevel() != null && !getLevel().isClientSide) {
            unsubscribeRouterTick();
            routerTickSub = subscribeServerTick(this::tickRouter);
        }
    }

    @Override
    public void onUnload() {
        unsubscribeRouterTick();
        unsubscribeHostStateTick();
        super.onUnload();
    }

    private void unsubscribeHostStateTick() {
        if (hostStateSub != null) {
            hostStateSub.unsubscribe();
            hostStateSub = null;
        }
    }

    /** 每 {@value #HOST_STATE_REFRESH_INTERVAL} tick 把权威连接结果算一次并推给客户端。 */
    private void tickHostState() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        long now = level.getGameTime();
        // ★【2026-09-21 队长拍板「上」】本订阅是**每 tick** 都被叫到的服务端钩子（下面的间隔只门住
        //   "推给客户端"那件事）⇒ 这里就是「同 tick 折叠」的兑现点：上一 tick 折叠过一次「成形」判定，
        //   此刻补一次真判定（下一次方块变更也会顺手结清它 ⇒ 稳态下通常只是一次 volatile 读）。
        confirmFoldedPattern();
        // ★ 2026-09-27：仓位清单心跳（修「外壳连片刷新时机」）—— 每 20 tick 比一次 part **个数**，
        //   个数没变就 O(1) 返回；变了才重建并（去重后）发布。放在下面的间隔判断**之前**：
        //   它自己不重跑结构匹配，不受 HOST_STATE_REFRESH_INTERVAL 影响。
        tickNacSlotHeartbeat();
        if (lastHostStateTick != Long.MIN_VALUE && now - lastHostStateTick < HOST_STATE_REFRESH_INTERVAL) {
            return;
        }
        lastHostStateTick = now;
        refreshHostSync();
    }

    /**
     * 把「复合体连接」的权威结果算一次并同步给客户端（服务端调用；客户端直接返回）。
     *
     * <p>调用点：{@link #onLoad}、结构成形/失效、{@link #onHostLinked()}（主机连上本机时）、
     * {@link #onDisconnected()}（主机断开本机时），以及每 {@value #HOST_STATE_REFRESH_INTERVAL} tick 的兜底刷新。
     */
    private void refreshHostSync() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        NanochipAssemblyComplexMachine h = host;
        boolean connected = hostConnected();
        syncedHostConnected = connected;
        syncedDrawingFromHost = drawingFromHost();
        syncedHostTier = connected && h != null ? h.getTier() : 0;
        if (connected && h != null) {
            syncedHostInfo = " · " + (h.fullMode() ? "全流程档" : "直通档")
                    + " · 已识别 " + h.formedModuleCount() + " 个模块";
        } else if (h != null) {
            syncedHostInfo = "复合体未成形，或尚未把本机登记进模块集合";
        } else {
            syncedHostInfo = "尚未被复合体登记；复合体成形后会自动登记";
        }
    }

    private void unsubscribeRouterTick() {
        if (routerTickSub != null) {
            routerTickSub.unsubscribe();
            routerTickSub = null;
        }
    }

    /**
     * 每 20 tick 按规则把虚拟件在颜色通道之间搬一次
     * （原版：{@code Rules can be set by input color(s), output color(s), supplied redstone signal,
     * and item filters}；{@code will be transferred after all conditions of a Rule are met}）。
     */
    private void tickRouter() {
        if (!kind.isRouter()) {
            return;
        }
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        // ★ 2026-09-16：分流搬运同样只认主机权威的连接结果（断开时不再搬虚拟件）
        NanochipAssemblyComplexMachine nac = getHost();
        if (!hostConnected() || nac == null || splitterRules.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        if (lastRouterTick != Long.MIN_VALUE && now - lastRouterTick < 20L) {
            return;
        }
        lastRouterTick = now;
        int signal = level.getBestNeighborSignal(getPos());
        boolean moved = false;
        for (NanochipSplitterRules.Rule rule : splitterRules.rules()) {
            if (!rule.effective()) {
                continue;
            }
            if (rule.redstoneThreshold() > 0 && signal < rule.redstoneThreshold()) {
                continue;
            }
            long amount = nac.vacuumNetwork().transfer(rule.in(), rule.out(),
                    rule.itemFilter(), NanochipSplitterRules.MAX_TRANSFER_PER_RULE);
            if (amount > 0L) {
                moved = true;
            }
        }
        if (moved) {
            nac.markVacuumDirty();
        }
    }

    /** 原版规格：模块天生无限并行。 */
    @Override
    public int getMaxParallel() {
        return Integer.MAX_VALUE;
    }

    // ---------- 配方逻辑：组装矩阵的产出要记进复合体的纳米校准 ----------

    /**
     * 走完整链路时，电路是<b>组装矩阵</b>产出的（原版如此），所以校准记录必须挂在它身上：
     * 每次配方完成把产物报给宿主复合体，由复合体记入 10 万电路历史窗口。
     */
    private static final class ModuleRecipeLogic extends MultipleRecipesLogic {
        private final NanochipModuleMachine module;

        ModuleRecipeLogic(NanochipModuleMachine module) {
            super(module);
            this.module = module;
        }

        /**
         * ★★【2026-09-21 0.1.23】简单模式下把组装矩阵的产出缩到「1 出 8」—— 挂在这里，因为这里是
         * <b>唯一真正会生效的地方</b>。
         *
         * <h4>为什么以前挂的那处（注册的 {@code recipeModifier}）一次都没生效（javap 取证，不是猜）</h4>
         * GTCEu 只在 {@code RecipeLogic.checkMatchedRecipeAvailable(...)} 里调
         * {@code machine.fullModifyRecipe(...)} —— <b>那一步才会执行</b>
         * {@code MultiblockMachineBuilder.recipeModifier(...)} 注册的函数。
         * 而本模组的主机与模块都继承 gtlcore 的 {@code WorkableElectricMultipleRecipesMachine}，
         * 它的配方逻辑 {@code MultipleRecipesLogic} <b>覆盖了 {@code findAndHandleRecipe()}</b>，
         * 自己走 {@code getRecipe()} / {@code checkRecipe()} / {@code setupRecipe()} 这条链，
         * <b>从头到尾没有调用过 {@code fullModifyRecipe}</b>
         * （反编译该类全文：只有 gtlcore 自己给并行用的 {@code ContentModifier.multiplier}，
         * 没有任何 {@code fullModifyRecipe} / {@code doModifyRecipe} 调用；
         * 且它的 {@code onRecipeFinish()} 也<b>不</b> chain 到 GTCEu 那条会调 {@code fullModifyRecipe} 的 super）。
         * ⇒ 注册在 builder 上的 {@code nacRecipeModifier}（主机）与 {@code moduleFlexibleOverclock}（模块）
         * <b>从未被执行</b>；队长的实测现象「简单模式产出依然是 16」就是它的直接后果。
         *
         * <h4>为什么 {@code setupRecipe} 是正确的作用点</h4>
         * {@code setupRecipe(GTRecipe)} 是 gtlcore 每次拿到配方后<b>唯一的落地口</b>
         * （{@code findAndHandleRecipe()} 与 {@code onRecipeFinish()} 两条路径都经它），
         * 进来的配方就是 {@code lastRecipe}；而 {@code onRecipeFinish()} 正是拿 {@code lastRecipe}
         * 去 {@code RecipeRunnerHelper.handleRecipeOutput(...)} 发货。
         * ⇒ 在 {@code setupRecipe} 里缩放，等价于「在发货口缩放」，两处循环都覆盖、且只有这一个作用点。
         *
         * <p>本覆写<b>只动产出的数量</b>（{@link #scaleOutputsForSimpleLogistics} 只替换 {@code outputs}），
         * 不动 EU / 时长 —— 那两样的接法涉及与 gtlcore 自己的并行 EU 算法是否叠加，
         * 属于另一个决定，不在本轮。
         *
         * <p>★ <b>2026-09-21 队长拍板（本覆写的实测效果 = 空操作）</b>：队长原话「组装矩阵的 JSON 16 → 8
         * （让 JEI 也显示 8，跟运行期一致）」。产出已<b>直接写进配方</b>（同日再按队长令改成阶梯
         * 处理器 8 / 装配 4 / 电脑 2 / 主机 1，见 {@link #SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER} 的注释），
         * {@link #SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER} 置 {@code 1.0D} ⇒
         * {@link #scaleOutputsForSimpleLogistics} 会在 {@code >= 1.0D} 短路处原样返回入参，
         * 本覆写因此<b>不再改变任何配方</b>（保留它只为守住"作用点唯一"这条结构纪律：将来若又要
         * 恢复运行期缩放，唯一允许的挂点仍然是这里，不能挂回 {@code recipeModifier} 链）。
         * 三处因此一致：JEI = 简单模式运行期 = 原版模式运行期 = 同一套阶梯。
         */
        @Override
        public void setupRecipe(GTRecipe recipe) {
            super.setupRecipe(scaleOutputsForSimpleLogistics(module, recipe));
        }

        @Override
        public void onRecipeFinish() {
            NanochipAssemblyComplexMachine host = module.getHost();
            // ★ 2026-09-16：只认主机权威的连接结果（原来只看 host.isFormed()，模块自己失效后仍可能记账）
            if (host != null && module.hostConnected()
                    && module.kind == NanochipModuleKind.ASSEMBLY_MATRIX) {
                host.recordExternalProduction(getLastRecipe());
            }
            // 全板浸没：每完成一次配方涨杂质（到阈值自动排液）
            module.onRecipeProduced();
            super.onRecipeFinish();
        }

        /**
         * 中央供电的实际扣电：模块没装能源仓时，走**本模块的能量缓存**（缓存由主机每 20 tick 按优先级灌）。
         * 装了能源仓的模块走 {@code super}（行为与以前完全一致）。
         *
         * <p>★ 2026-09-21 改为「缓存模型」，逐字实现 wiki 两句话：
         * <ol>
         *   <li>「模块缓存 = NAC最大输入功率 × (2 × 模块最大配方时间)」（容量由主机设，见
         *       {@link #setBufferCapacity(long)}）；</li>
         *   <li>「If a module is 'Waiting for Power', it needs to charge its buffer so that it can
         *       accommodate the full recipe that it is about to do and ensure no power-fail.」
         *       ⇒ 起跑（{@code progress == 0}）要求缓存 ≥ 整条配方的电；</li>
         * </ol>
         * 起跑后每 tick 从缓存里扣，<b>不再向主机逐 tick 要电</b> —— 这就是「NAC 永不跳电」：
         * 配方一旦开始，电量已经预留在本模块，主机中途抽不出电也不会把这条配方打断。
         *
         * <p>组装矩阵仍是 {@link NanochipModuleKind#ASSEMBLY_MATRIX} 最低优先级（−1），但优先级现在体现在
         * **主机每 20 tick 补缓存的顺序**上（{@link #energyPriority()}），不在逐 tick 要电上。
         */
        @Override
        public void handleRecipeWorking() {
            GTRecipe recipe = getLastRecipe();
            if (recipe == null || !module.drawingFromHost()) {
                super.handleRecipeWorking();
                return;
            }
            NanochipAssemblyComplexMachine host = module.getHost();
            if (host == null || !module.hostConnected() || !host.isWorkingEnabled()) {
                setWaiting(Component.translatable("gtlmodular.recipe.fail.host_unavailable"));
                if (getStatus() == RecipeLogic.Status.WAITING) {
                    doDamping();
                }
                return;
            }
            long perTick = Math.max(1L, RecipeHelper.getInputEUt(recipe));
            long full = perTick * (long) Math.max(1, recipe.duration);
            // ★ 2026-10-08【无线电网供电兼容】把「等待复合体蓄电」这道判定从「按**已存**电量」改成
            //   「按主机**可获取能力**」。
            //
            //   现象（实测）：主机装 GTL Additions「无线电网输入终端」时，主机界面「中央供电:
            //   9,223,372,036,854,775,807 / 9,223,372,036,854,775,807 EU · 模块实付 0 EU/t」看似充足，
            //   模块界面却恒显示 lang 键 gtlmodular.nanochip.module.buffer 那句
            //   「等待复合体蓄电：本条配方整条需要 … EU，复合体当前 0 EU；攒够才开始」⇒ 永不开工。
            //
            //   根因（两侧口径不一致）：本判据读的是 `module.getBufferEU()` —— **本模块自己的缓存字段**
            //   `syncedBufferEU`（NanochipModuleMachine.java:569/575），由主机每 20 tick 灌；
            //   而无线电网是「不储能、按需现取」型能源 ⇒ 主机那条灌电链（
            //   NanochipAssemblyComplexMachine.distributeModuleEnergy）在它上面拿不到可交割的电量，
            //   缓存恒为 0 ⇒ 判定恒失败。（主机侧的电源容器是 GTL Additions 的
            //   `com.gtladd.gtladditions.common.machine.trait.NetworkEnergyContainer`，
            //   签名 `NotifiableRecipeHandlerTrait<Long> + IEnergyContainer + IInt128EnergyContainer`，
            //   即「容量 Long.MAX 级、无已存储量」那一类。）
            //
            //   改法（用户指定的「优先」档）：容量为 Long.MAX 级 / 供应源为无限 ⇒ 视为已充足、直接放行。
            //   缓存模型的本意是「起跑前预留整条配方、避免中途断电」；对一个不会断电的无限源，
            //   这个预留是空条件，不适用。
            //
            //   保守第一 + 真无电仍拦：容量为 0 且存量为 0 ⇒ hostUnlimited == false ⇒ 下面两道判定
            //   与改动前**逐字一致**（照样置等待、照样走 FAIL_NO_ENOUGH_EU_IN）。
            final boolean hostUnlimited = host.isEnergySourceEffectivelyUnlimited();
            if (getProgress() == 0) {
                module.ensureBufferCovers(full);   // 我方个别配方比 NH 页上的最大配方时间还长 ⇒ 容量至少覆盖它
                if (module.getBufferEU() < full) {
                    if (!hostUnlimited) {
                        setWaiting(Component.translatable("gtlmodular.nanochip.module.buffer",
                                com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(full),
                                com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(module.getBufferEU())));
                        if (getStatus() == RecipeLogic.Status.WAITING) {
                            doDamping();
                        }
                        return;
                    }
                    // 需要量 > 已存，但源头是无限/按需现取 ⇒ 记一行「按可获取能力放行」（限流）
                    host.logUnlimitedEnergyAssist("模块起跑缓存判定", full, module.getBufferEU());
                }
            }
            final boolean paidFromBuffer = module.consumeBufferEU(perTick);
            if (paidFromBuffer || hostUnlimited) {
                if (!paidFromBuffer) {
                    // 缓存一时没攒够，但源头按需现取 ⇒ 放行（同一句诊断，限流在主机侧）
                    host.logUnlimitedEnergyAssist("模块逐 tick 扣电", perTick, module.getBufferEU());
                }
                setStatus(RecipeLogic.Status.WORKING);
                if (!machine.onWorking()) {
                    interruptRecipe();
                    return;
                }
                progress++;
                totalContinuousRunningTime++;
            } else {
                setWaiting(org.gtlcore.gtlcore.api.recipe.RecipeResult.FAIL_NO_ENOUGH_EU_IN.reason());
                if (getStatus() == RecipeLogic.Status.WAITING) {
                    doDamping();
                }
            }
        }
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new ModuleRecipeLogic(this);
    }

    /**
     * 该模块的「可用输入功率」（wiki：「超频后的功率不能超过 NAC 可用输入功率」）。
     *
     * <p>口径：模块自己装了能源仓 ⇒ 用它自己的电压；吃复合体的电（{@code drawingFromHost}）
     * ⇒ 取「模块电压」与「NAC 最大输入功率
     * （能源仓电压 × 安培数，{@link NanochipAssemblyComplexMachine#getMaxInputPower()}）」中的较大者。
     *
     * <p>★ 2026-09-21（能量缓存这一轮）：原先这里是 **1 A 的保守下界**，注释里写明"算准安培数属于能量缓存那一轮的活"
     * —— 现在那一轮到了：主机侧已经把 {@code getMaxInputPower()}（电压 × 安培）暴露出来，本方法改用它。
     */
    private static long moduleAvailableInputPower(NanochipModuleMachine module, long ownVoltage) {
        long cap = ownVoltage;
        NanochipAssemblyComplexMachine host = module.getHost();
        if (host != null && module.hostConnected()) {
            long hostPower = host.getMaxInputPower();
            if (hostPower > cap) {
                cap = hostPower;
            }
        }
        return cap;
    }

    /**
     * 模块配方修正：2/2 封顶的**无损**电力超频 + 5 秒时长下限。
     * （原版注释：{@code Modules have capped 2/2 overclocks ... The recipe time can not be
     * overclocked below 5 seconds.}）
     */
    public static GTRecipe moduleOverclock(MetaMachine machine, GTRecipe recipe,
                                           OCParams params, OCResult result) {
        if (!(machine instanceof NanochipModuleMachine module)) {
            return null;
        }
        long maxVoltage = module.getOverclockVoltage();
        if (maxVoltage <= 0L) {
            return null;
        }
        GTRecipe copy = recipe.copy();
        long eut = Math.max(1L, RecipeHelper.getInputEUt(copy));
        int duration = Math.max(1, copy.duration);
        // ★★ 2026-09-21（0.1.27）对齐 NH 官方规格「无损超频(2/2)」★★
        //   本地 wiki 底本逐页写着（`_census/wiki_抓取/raw/*.wiki.txt`，19 页，同一段话）：
        //   「取 NAC能源仓等级 与 配方等级 之差，单次耗时除以2、**功率乘以2**；
        //     处理时间不会低于5秒，且超频后的功率不能超过 NAC 可用输入功率。」
        //   ⇒ 两条都必须改：
        //   ① 我方旧实现是 `eut *= 4L`（GT 的"完美超频"口径），**与"无损"矛盾** —— 无损是 ×2；
        //   ② 超频**次数**来自「能源仓等级 − 配方等级」，而不是只看"功率还够不够"。
        int hatchTier = GTUtil.getTierByVoltage(maxVoltage);
        int recipeTier = GTUtil.getTierByVoltage(eut);
        // ⚠️ **不再套 MAX_OVERCLOCKS(2) 封顶**：主机 wiki 页「超频与缓存」写的就是
        //    「取 NAC能源仓等级与配方等级之差」——**没有封顶**；我们自己的 parity 体检
        //    `check_nac_mechanics_parity.js` 的 X4 分歧条目也早用 NH 字节码写明了
        //    「NH = 时长÷2 / EU×2，次数 = 主机能源仓等级 − 配方等级（**无 2 次封顶**），地板 100 tick」。
        //    (2/2) 那个记法是 GTCEu 对"无损超频"的标注（EU×2 / 时长÷2），**不是"最多两次"**。
        int maxOc = Math.max(0, hatchTier - recipeTier);
        long powerCap = moduleAvailableInputPower(module, maxVoltage);
        int oc = 0;
        while (oc < maxOc
                && duration / 2 >= MIN_DURATION_TICKS
                && eut <= powerCap / 2L) {
            eut *= 2L;
            duration /= 2;
            oc++;
        }
        // 组装矩阵享受复合体的纳米校准与模块加成（原版：模块的 Speed / EU cost / running cost
        // 由模块自身的机制与 NAC 的 Calibration 共同给出）
        int floorTicks = MIN_DURATION_TICKS;
        if (module.kind == NanochipModuleKind.ASSEMBLY_MATRIX) {
            NanochipAssemblyComplexMachine host = module.getHost();
            if (host != null && module.hostConnected()) {
                NanochipCalibration.Family family = NanochipAssemblyComplexMachine.familyOf(recipe);
                double euMul = host.getCalibration().euMultiplier() * host.moduleEuMultiplier(family);
                double durMul = host.getCalibration().durationMultiplier(family)
                        * host.moduleDurationMultiplier(family);
                eut = (long) Math.max(1.0D, Math.min((double) Long.MAX_VALUE, eut * euMul));
                duration = (int) Math.max(floorTicks,
                        Math.min(Integer.MAX_VALUE, Math.round(duration * durMul)));
                floorTicks = host.minDurationTicks();
            }
        }
        // 蚀刻阵列：晶体满校准后「激光源仓」开始生效（原版机制，1:1 照抄两条公式）
        if (module.kind == NanochipModuleKind.ETCHING_ARRAY) {
            NanochipAssemblyComplexMachine host = module.getHost();
            if (host != null && module.hostConnected()
                    && host.getCalibration().specialActive(NanochipCalibration.Family.CRYSTAL)) {
                long amperage = module.laserAmperage();
                if (amperage >= 512L) {
                    // Divides EU Cost by each Amperage Interval over 256A
                    eut = Math.max(1L, eut / (amperage / 256L));
                }
                int laserTier = module.laserHatchTier();
                if (laserTier > 9) {
                    // Divides Recipe Duration by (Hatch Tier - 9)
                    duration = Math.max(floorTicks, duration / (laserTier - 9));
                }
            }
        }
        RecipeHelper.setInputEUt(copy, eut);
        copy.duration = Math.max(floorTicks, duration);
        copy.ocTier = oc;
        return copy;
    }

    /** 备用入口：电压不足时仍按标准非完美超频处理（模块可单独使用）。 */
    public static GTRecipe moduleFlexibleOverclock(MetaMachine machine, GTRecipe recipe,
                                                   OCParams params, OCResult result) {
        GTRecipe direct = moduleOverclock(machine, recipe, params, result);
        GTRecipe modified;
        if (direct != null) {
            modified = direct;
        } else {
            modified = GTRecipeModifiers.ELECTRIC_OVERCLOCK
                    .apply(OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK)
                    .apply(machine, recipe, params, result);
        }
        // ★★ 2026-09-21（0.1.23）**产出 0.5 缩放已从这条链上搬走** ★★
        //   理由见 ModuleRecipeLogic.setupRecipe 的注释：这条链 = 注册在 builder 上的
        //   `recipeModifier`，而在 gtlcore 的 `MultipleRecipesLogic` 下它**根本不会被调用**
        //   （javap：该类全文无 fullModifyRecipe / doModifyRecipe 调用）⇒ 挂在这里等于没挂，
        //   队长看到的"简单模式产出依然是 16"就是这么来的。
        //   现在缩放只有**一个**作用点（`ModuleRecipeLogic.setupRecipe`）⇒ 结构上不可能被缩两次。
        return modified != null ? modified : recipe;
    }

    // ---------- 组装矩阵产出：**阶梯 8/4/2/1 已直接写进配方**（2026-09-21 队长拍板）----------
    // ★ 本节原先叫「简单物流模式：组装矩阵产出缩放（2026-09-19 队长『产出要降低到 1 出 8』）」——
    //   2026-09-21 队长把 30 条矩阵配方的 count 从 16 改成 8，同日再改成**按档位阶梯**
    //   （最基础的处理器 8 / 装配 4 / 电脑 2 / 主机 1；脚本 `_census/_apply_matrix_output_ladder.js`），
    //   并令运行期不再缩放
    //   （`SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER` 0.5D → **1.0D**），本节的乘法器因此**恒为空操作**
    //   （`>= 1.0D` 短路），下面注释里"配方仍是 count=16 / 乘 0.5 ⇒ 实出 8"是**当时的实况留痕**，
    //   自 2026-09-21 起作废，保留只为可追溯（详见 O.87 与 `check_module_output_scaling.js` A4）。

    /**
     * 简单模式（{@code nanochipComplex.simpleLogisticsMode = true}）下，<b>组装矩阵</b>的物品与流体
     * 产出按本倍率缩放：配方 JSON 里仍是 {@code count = 16}，到机器结算这一步才乘 0.5 ⇒ <b>实出 8</b>。
     *
     * <p><b>为什么配方数据不动、改在结算处</b>（队长给的两个选项里选前者，理由三条）：
     * <ol>
     *   <li><b>一份数据两种模式都对</b>：{@code data/gtlmodular/recipes/nanochip/assembly_matrix/*.json}
     *       与两份规格 {@code _census/nac_matrix_spec.json} / {@code _census/nac_cw_matrix_spec.json}
     *       的 {@code outCount = 16} 全部<b>原样不动</b>，体检
     *       {@code check_nac_p1_recipes.js} / {@code check_nac_p2_recipes.js} / {@code check_nac_cc_items.js}
     *       的登记值一条都不用改（不会出现"规格 16 / 简单模式要 8"的两个真相）；
     *   <li><b>没有条件表达能力可用</b>：KubeJS/数据包侧的 {@code forge:mod_loaded} 只能表达"装了哪个模组"，
     *       <b>表达不了配置开关</b>；GTCEu 的配方 condition 里也没有"读本模组配置"的现成 condition
     *       （做过调查：{@code GTRecipeConditions} 只有 mod_loaded / 维度 / 研究 之类），
     *       硬做两套配方就得给整套数据包加一份 30 条的镜像 + 一套自定义 condition —— 风险远大于收益；
     *   <li><b>缩放点唯一且可证</b>：{@code moduleOverclock}/{@code moduleFlexibleOverclock} 已经是本模组
     *       唯一的模块配方入口（{@code NanochipMachines.java:534} 注册
     *       {@code .recipeModifier(NanochipModuleMachine::moduleFlexibleOverclock)}），改动只此一处。
     * </ol>
     *
     * <p>⚠️ <b>2026-09-21（0.1.23）更正</b>：上面第 3 条"缩放点唯一且可证"<b>当时是错的</b> ——
     * 那条链（注册的 {@code recipeModifier}）在 gtlcore 的 {@code MultipleRecipesLogic} 下
     * <b>从来不会被调用</b>（取证见 {@code ModuleRecipeLogic.setupRecipe} 的注释），
     * 所以 0.1.19～0.1.22 之间这段缩放<b>一次都没生效</b>（队长实测"简单模式产出依然是 16"）。
     * 现在真正的唯一作用点是 {@code ModuleRecipeLogic.setupRecipe(GTRecipe)} ——
     * gtlcore 每次拿到配方都会经过它，且只有它一处调用本方法。
     *
     * <p><b>为什么不会与配方解析冲突</b>：配方 JSON 照旧解析成 {@code outputs.item[0].content.count = 16}
     * 的 {@code GTRecipe}（注册表里那条共享对象<b>一个字节都不变</b>）；缩放发生在
     * {@code setupRecipe} 里，产出的是一条<b>深拷贝</b>（{@code GTRecipe.copy()} +
     * {@code copyContents(outputs, ContentModifier.multiplier(0.5))}），
     * 由 {@code RecipeLogic} 作为"本条要跑的配方"持有（{@code getLastRecipe()}）。
     * ⇒ 扣料 / 产出 / 纳米校准记账读的都是同一个对象，<b>实出多少就记多少</b>
     * （不会出现"界面写 16、实际给 8"或"校准按 16 记账"这种错位）。
     *
     * <p><b>⚠ 必须 copy()</b>：{@code GTRecipeModifiers.ELECTRIC_OVERCLOCK} 那条回落路径
     * <b>有可能原样返回入参</b>（= 配方注册表里那条共享对象，javap：{@code copyContents} 只在
     * {@code copy()} 里生成新 Map）—— 就地改它的 {@code outputs} 会污染所有机器。
     * 所以这里一律先 {@code copy()} 再整表替换 {@code outputs}。
     * （{@code copyContents} 的字节码已核：返回 {@code new HashMap} + 每个 {@code Content}
     * 都经 {@code Content.copy(cap, modifier)} 造新对象；{@code ItemRecipeCapability.copyWithModifier}
     * 走 {@code modifier.apply(amount).intValue()} ⇒ 16 × 0.5 = 8。装配矩阵 30 条配方均只有
     * <b>一个</b> item 输出（count=16），所以不存在"别的产物被缩成 0"的情形。）
     *
     * <p><b>不改这里的两件事</b>（如实登记）：
     * <ul>
     *   <li><b>模块层（元件 ×2）/ 换算层（真物品 ⇄ CC 1:1）</b>：一行没动；</li>
     *   <li><b>主机「直通档」</b>（{@code NanochipAssemblyComplexMachine.nacRecipeModifier}，
     *       跑的是 {@code circuit_assembler} 配方表）：<b>不在</b>本次缩放范围内 ——
     *       队长点名的是"组装矩阵"那 16 个，直通档是另一条路（它的产出由配方 JSON 决定）。</li>
     * </ul>
     *
     * <p>★ <b>2026-09-21 队长拍板：产出直接写进配方，运行期不再缩放</b>。
     * 队长原话：「组装矩阵的 JSON 16 → 8（让 JEI 也显示 8，跟运行期一致）」——
     * 本函数这一层 ×0.5 <b>停用</b>：
     * <ul>
     *   <li><b>产出落在配方里</b>：30 条 {@code data/gtlmodular/recipes/nanochip/assembly_matrix/*.json}
     *       的 {@code outputs.item[0].content.count} 与两份规格（{@code _census/nac_matrix_spec.json}
     *       20 行 / {@code _census/nac_cw_matrix_spec.json} 10 行）的 {@code outCount}
     *       先 <b>16 → 8</b>（规格留痕：{@code outCountBefore16 = 2}、{@code outCountBefore8 = 16}，
     *       顶层 {@code outCountRework20260921}），
     *       ★ 同日再按队长令「修改电路产出的数量，让每个系列最基础的产出八，4，2，1最后每个系列的主机产出一份」
     *       改成<b>按档位阶梯</b>：<b>处理器 8 / 装配 4 / 电脑 2 / 主机 1</b>
     *       （7 个系列同构；规格留痕 {@code outCountBeforeLadder = 8}，顶层 {@code outCountReworkLadder20260921}）；
     *       配方<b>条数不变</b>（仍 30 条），只改数值。JEI 读的就是这条数据 ⇒ JEI 显示的就是阶梯值；</li>
     *   <li><b>运行期不再缩放</b>：{@link #SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER} 置 {@code 1.0D}，
     *       本函数上面那条 {@code >= 1.0D} 短路直接 {@code return recipe;} ⇒ 下面的深拷贝 +
     *       {@code ContentModifier.multiplier} 代码<b>保留但不再执行</b>（留作将来若又要
     *       "运行期有别于 JEI"的现成挂点，也让 {@code _census/check_module_output_scaling.js}
     *       的 A1/A2/A3/A5 结构判据继续成立：作用点唯一、只动产出、不误伤主机）；</li>
     *   <li>⇒ <b>JEI = 简单模式运行期 = 原版模式运行期 = 同一套阶梯（8/4/2/1）</b>，三处一致，
     *       不会再被缩两次（阶梯值再 ×0.5 会把处理器缩成 4、主机缩成 0 这条歧路已堵死）；</li>
     *   <li>上文提到的「配方 JSON 照旧解析成 {@code count = 16}」「16 × 0.5 = 8」
     *       「一个 item 输出（count=16）」是 <b>2026-09-19 当时的实况</b>，自本次起作废
     *       （原文保留，只为留痕）。</li>
     * </ul>
     */
    public static GTRecipe scaleOutputsForSimpleLogistics(MetaMachine machine, GTRecipe recipe) {
        if (recipe == null || !GTLModularConfig.isNanochipSimpleLogistics()) {
            return recipe;   // 原版模式：原样返回，行为逐语义不变
        }
        if (!(machine instanceof NanochipModuleMachine module)
                || module.getModuleKind() != NanochipModuleKind.ASSEMBLY_MATRIX) {
            return recipe;   // 只缩放组装矩阵；其余 13 台模块一字不动
        }
        if (SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER >= 1.0D) {
            return recipe;
        }
        GTRecipe copy = recipe.copy();
        java.util.Map<RecipeCapability<?>, java.util.List<Content>> scaled =
                recipe.copyContents(recipe.outputs,
                        ContentModifier.multiplier(SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER));
        copy.outputs.clear();
        copy.outputs.putAll(scaled);
        return copy;
    }

    // ---------- 宿主连接 ----------

    /**
     * ★【2026-09-16】「本机连着复合体吗」—— <b>只读主机侧的权威结果</b>，模块自己不再写那个集合。
     *
     * <p>三个条件全都要满足：
     * <ol>
     *   <li>{@code isFormed()}：模块自己成形（没成形不可能被主机收进去；GTCEu 的
     *       {@code IModularMachineHost.isValidModule} 也是这么要求的）；</li>
     *   <li>主机成形；</li>
     *   <li><b>主机的「已连接模块集合」里有本机</b>（{@link NanochipAssemblyComplexMachine#isModuleConnected}）
     *       —— 这才是唯一权威。</li>
     * </ol>
     * <p>主机在结构成形那一刻、以及此后每 20 tick 按结构 {@code F} 位重算该集合，所以本机成形后
     * 最坏 1 秒内会被登记（旧代码里模块自己是"谁后跑谁覆盖"的一方，正是连接抖动的根因）。
     */
    public boolean hostConnected() {
        if (!isFormed()) {
            return false;
        }
        NanochipAssemblyComplexMachine h = host;
        return h != null && h.isFormed() && h.isModuleConnected(this);
    }

    /**
     * ★★【A · 连接发起方反转】<b>模块侧是否还允许"主动与主机尝试连接"</b>（★ 现行值：{@code false} = 不允许；{@code true} = 允许）★★
     *
     * <p>用户口径（原话）：「模块一进游戏，它跟主机的连接必须自己手点才行，干脆改成模块<b>不再主动</b>与主机
     * 尝试连接，<b>主机自己建立连接</b>吧」⇒ 本开关就是这一条的落点，管住模块侧<b>两</b>件事：
     * <ol>
     *   <li>{@link #findAndConnectToHost()}：{@code false} 时不再做任何连接动作，退化成"只读回答
     *       {@link #hostConnected()}"（= 2026-09-16 那一版的语义）。它当前在本仓库<b>没有调用点</b>
     *       （全工程 grep），本条是为"以后有人在服务端线程/第三方代码里调它"留的闸 ——
     *       让"模块不会自己去找主机"成为结构上的事实，而不是靠没人调用；</li>
     *   <li>{@link #requestHostResync()}：{@code false} 时<b>不再催促主机</b>重算模块集合
     *       （{@code onStructureFormed / onStructureInvalid / onMachineRemoved} 三处调用点原样保留，
     *       但整条催促变成 no-op）⇒ 集合的每一次写入都只由主机自己的服务端 tick 发起
     *       （{@code NanochipAssemblyComplexMachine#tickModuleRefresh} → {@code scanAndConnectModules}，
     *       每 {@code MODULE_REFRESH_INTERVAL} = 20 tick 一次；成形那一刻另有一次）。</li>
     * </ol>
     *
     * <h3>为什么这不损失任何时效（可论证，不是"应该没事"）</h3>
     * 被关掉的那条催促本来就<b>不是</b>快速通道：
     * ① 它在主机侧被 {@code requestCoalescedResync()} 去抖成 {@code RESYNC_COALESCE_TICKS} = 20 tick
     * 才落地一次（与周期重扫<b>同频</b>）；
     * ② 进世界后的头 {@code JOIN_QUIET_TICKS} = 10 秒里它被<b>整个丢掉</b>
     * （见 {@code consumePendingResync} 的静默窗口分支）——而那正是用户抱怨"要手点"的场景。
     * ⇒ 关掉它之后，"进游戏多久连上"完全由主机那条 20 tick 的周期重扫决定，
     * 与打开时<b>同一个量级、同一个上界（≤1 秒）</b>；读档场景甚至更快，因为不再和 10 秒静默窗口打架。
     *
     * <h3>一行回退</h3>
     * ★ 历史（保留）：2026-11-15【任务 A】本常量曾由 {@code false} 改为 {@code true}（队长实测逐字：
     * 「现在跟主机的连接还有问题，我拆掉重放以后，它的配方等级又变到了ULV」）；随后队长复核
     * 「那个连接自愈跟敲块失效，不要加上去」⇒ <b>本常量已回退为 {@code false}</b>，即逐字回到
     * 2026-11-12 的行为（本机不做任何连接动作、三处催促整条 no-op）。原 {@code false} 那一侧的说明
     * （"关掉它不损失时效"）作为该回退的完整依据原样保留在上一节。<br>
     * ⚠️ <b>只改本常量是无效的</b>：催促的消费端只有
     * {@code NanochipAssemblyComplexMachine#consumePendingResync(long)}，而它的唯一调用点在原初模式下
     * 也被早退挡住 ⇒ 2026-11-15 那一轮为此同时放行过
     * {@code NanochipAssemblyComplexMachine.ORIGINAL_MODE_CONSUME_PENDING_RESYNC}
     * （该常量已随本次回退删除 ⇒ 现与原初模式的早退口径一致）。
     * 编译期常量、不是 {@code @Persisted}、不进存档、不改存档格式、不新增 mixin。
     *
     * <p><b>上游契约（谁是发起方）</b>：{@code IModularMachineHost#scanAndConnectModules()}
     * 的默认实现就是"主机遍历 {@code getModuleScanPositions()} → {@code isValidModule} →
     * {@code module.connectToHost(this)}"（字节码 {@code gtl-dev/api-dump/gtlcore_IModularMachineHost_c.txt:65-116}）
     * ⇒ <b>上游本来就是"主机主动"</b>；模块侧的 {@code findAndConnectToHost()} 只是一条可选的自我接入路径
     * （同目录 {@code gtlcore_IModularMachineModule_c.txt:68-157}）。本开关把我们的姿态收回上游那一套。
     */
    public static final boolean NAC_MODULE_ACTIVE_CONNECT = false;

    /**
     * ★【2026-10 模块框架对齐 · A3】<b>恢复官方语义</b>：{@code IModularMachineModule} 的默认实现
     * —— <b>先试持久化坐标、再全扫描</b>（字节码全文
     * {@code gtl-dev/api-dump/gtlcore_IModularMachineModule_c.txt:68-157}）：
     * <ol>
     *   <li>非 {@code ServerLevel} ⇒ {@code false}（客户端不做连接动作）；</li>
     *   <li>{@code getHostPosition()} 非空 ⇒ {@code level.getBlockEntity(那一格)} instanceof
     *       {@code IMachineBlockEntity} ⇒ {@code isValidHost(machine)}（= {@code getHostType()} 匹配
     *       + {@code IModularMachineHost} + {@code host.isFormed()}）⇒ {@code connectToHost(host)}
     *       ⇒ {@code true}；</li>
     *   <li>否则遍历 {@code getHostScanPositions()}（反向扫描位）逐个试，成功即 {@code true}；</li>
     *   <li>都不成 ⇒ {@code false}。</li>
     * </ol>
     *
     * <h3>改前 / 改后</h3>
     * 改前（2026-09-16 起）：`return hostConnected();` —— <b>纯只读</b>，不做任何连接动作
     * （理由是当时"模块自己找主机"与主机侧按 F 位注入两条路互相覆盖，导致连接抖动）。
     * 改后：逐字回到框架默认行为。本机<b>没有</b>覆写 {@code connectToHost / removeFromHost /
     * isValidHost} ⇒ 走的就是框架那条原样的链。
     *
     * <h3>为什么这不违反「单一写入者 = 主机」</h3>
     * 其一，<b>本方法在本仓库当前没有任何调用点</b>（全工程 grep：{@code findAndConnectToHost} 只出现在
     * 本方法与 {@code MoltenModuleMachineBase} 里，NAC 侧无人调用）⇒ 恢复语义<b>不会</b>新增任何写入者。
     * 其二，即便被调用，{@code connectToHost(host)} 的落点仍是<b>主机自己的</b>
     * {@code host.addModule(this)}（接口默认实现，字节码 {@code :19-47}）——模块从来没有直接改
     * {@code modules} 集合，写集合的永远是主机对象自己。
     * 其三，<b>刻意不在本机 {@code onStructureFormed()} 里加调用点</b>：那条回调可能跑在 GTCEu 的
     * 异步结构检查线程上（{@code GTCEu Multiblock Async Thread-0}），而 {@code modules} 是非线程安全的
     * {@code LinkedHashSet} ⇒ 那正是 2026-09-21 用 {@code requestCoalescedResync()} 修掉的旧竞态；
     * NAC 的回连由<b>主机在服务端线程</b>按几何扫描位注入（见
     * {@code NanochipAssemblyComplexMachine#scanAndConnectModules()}）。
     * <br>本方法的存在价值 = ①与官方模块语义逐字对齐；②一旦有服务端线程上的调用点（本机或第三方），
     * 就能靠 {@code @Persisted} 的 {@code hostPosition} 立刻接回；③返回值语义由"读界面状态"
     * 改回"是否促成了一次连接"，与框架契约一致。
     */
    @Override
    public boolean findAndConnectToHost() {
        // ★★【A · 连接发起方反转】本常量置 {@code false} 时（现行值；2026-11-12 起的默认）⇒ 本机**不做任何连接动作**：
        //   只如实回答"我现在被主机登记着吗"（= 2026-09-16 那一版的纯只读语义，见上方 javadoc）。
        //   连接一律由主机自己发起（框架默认的 scanAndConnectModules + 本机所在结构成形那一刻）。
        if (!NAC_MODULE_ACTIVE_CONNECT) {
            return hostConnected();
        }
        return IModularMachineModule.super.findAndConnectToHost();
    }

    /**
     * ★【A · 连接发起方反转】主机把本机登记进集合时由 gtlcore 的
     * {@code IModularMachineModule#connectToHost(H)} 回调（<b>框架原生钩子</b>，字节码
     * {@code gtl-dev/api-dump/gtlcore_IModularMachineModule_c.txt:44-46}：{@code addModule(this)} 之后就是
     * {@code onConnected(host)}）。
     *
     * <p><b>为什么必须补这一条</b>：主机那条"框架原生几何扫描"路
     * （{@code NanochipAssemblyComplexMachine#scanAndConnectModules}）走的就是
     * {@code connectToHost}；而框架默认的 {@code onConnected} 是<b>空实现</b>（同文件 {@code :195-197}）。
     * 本机原来的界面/状态刷新只挂在 {@code onHostLinked()}（我们自己那条约等于快照兜底路的钩子）
     * 与每 20 tick 的 {@code tickHostState()} 上 ⇒ 主机主动连上来的那一刻，本机的
     * {@code @DescSynced} 快照要等下一轮兜底才更新。补上这一条之后：<b>主机一连上，模块当场就是"已连接"</b>，
     * 不再需要玩家去点任何按钮。
     *
     * <p>与既有的 {@link #onDisconnected()}（框架的断开回调）严格对称：一个方向推一次，
     * 兜底心跳仍在（{@code tickHostState} 每 {@code HOST_STATE_REFRESH_INTERVAL} tick 一次）。
     * 线程/只读边界与 {@code onHostLinked()} 完全一致：{@code refreshHostSync()} 自带
     * "世界为空 / 客户端侧 ⇒ 直接返回"的守卫，只写本机自己的同步快照字段，<b>不碰主机集合</b>。
     */
    @Override
    public void onConnected(NanochipAssemblyComplexMachine host) {
        refreshHostSync();
    }

    /** 挂上 NAC 后的回调（主机刚把本机登记进集合；用于刷新界面快照）。 */
    public void onHostLinked() {
        refreshHostSync();
    }

    /** 主机把本机移出集合时由 {@code IModularMachineModule.removeFromHost} 回调（刷新界面快照）。 */
    @Override
    public void onDisconnected() {
        refreshHostSync();
    }

    /**
     * ★【2026-09-16】催主机<b>立刻</b>按结构 F 位重算一次「已连接模块集合」。
     *
     * <p>纪律：这是"请求权威方重算"，<b>不是</b>模块自己去改集合（单一写入者 = 主机，见
     * {@link NanochipAssemblyComplexMachine#isModuleConnected}）。好处：
     * <ol>
     *   <li>本机成形/失效/被拆时主动催一次 ⇒ 连接状态不用等主机下一轮 20 tick 轮询（最坏 1 秒）；
     *       尤其是"模块刚建成形、主机早已成形"这条最常见的路径 —— 现在当场就连上（原来那句
     *       「已成形…复合体连接=否」正是玩家看到的症状）；</li>
     *   <li>若本机还没被主机认识（{@code host == null}，例如主机比本机后成形），就什么都不做，
     *       等主机自己轮询把本机收进去（≤20 tick）。</li>
     * </ol>
     *
     * <p>★ <b>2026-09-21 改为走去抖入口</b>（队长：「唉，现在一进游戏NAC相关的，都要重新加载结构，
     * 而且要好久，尤其是主机。」）：
     * <ul>
     *   <li>改前：这里<b>当场</b>调 {@code host.scanAndConnectModules()} —— 12 台模块成形/失效就是 12 次；
     *       而本方法可能正跑在 GTCEu 的<b>异步结构检查线程</b>上（日志里的
     *       {@code GTCEu Multiblock Async Thread-0} 会回调 {@code onStructureFormed}）⇒ 还会在非服务端线程写主机集合；</li>
     *   <li>改后：只调 {@link NanochipAssemblyComplexMachine#requestCoalescedResync()}（置一个原子标志，O(1)），
     *       真正的重算由主机在自己的服务端 tick 里按 20 tick 限频合并执行
     *       ⇒ 同一波催促 <b>12 次 → 1 次</b>，且写集合的动作 100% 留在服务端线程。</li>
     *   <li><b>语义不变</b>：这里改的只是"什么时候算"，主机按结构 F 位幂等覆盖集合的判据没动。</li>
     * </ul>
     *
     * <p>★★ <b>2026-11（A · 连接发起方反转）本方法由 {@link #NAC_MODULE_ACTIVE_CONNECT} 挂闸</b>：该常量
     * 2026-11-12 起为 {@code false}（整条停用）；2026-11-15【任务 A】曾短暂改为 {@code true}（恢复催促），
     * 随后已回退为 {@code false}。见
     * {@link #NAC_MODULE_ACTIVE_CONNECT}。用户口径「不要模块自己主动去连主机」⇒ 模块侧连"催主机"也不做，
     * 连接的发起方彻底收归主机自己的服务端 tick。上面整段（以及 2026-09-21 的去抖改造）作为
     * <b>一行回退</b>的完整依据原样保留 —— 把那个常量改成 {@code true} 即逐字恢复。
     */
    private void requestHostResync() {
        // ★★【A · 连接发起方反转】本常量置 {@code false} 时模块侧不催促主机（现行值；2026-11-12 起的默认。
        //   2026-11-15 任务 A 曾改为 {@code true} ⇒ 恢复催促，随后已回退；见 NAC_MODULE_ACTIVE_CONNECT 与
        //   主机侧原初模式早退说明的成对关系）。
        //   三个调用点（onStructureFormed / onStructureInvalid / onMachineRemoved）原样保留 ——
        //   它们各自还要做自己的界面快照刷新，只是"催主机重算"这一半整条停用；
        //   主机侧的每 20 tick 周期重扫（tickModuleRefresh → scanAndConnectModules）独立于本入口，
        //   所以连接/断开照旧在 ≤1 秒内被主机自己收敛（论证见该常量的 javadoc）。
        if (!NAC_MODULE_ACTIVE_CONNECT) {
            return;
        }
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return; // 客户端不做任何连接动作（主机侧集合只存在于服务端）
        }
        NanochipAssemblyComplexMachine h = host;
        if (h == null) {
            return;
        }
        // ★ 2026-09-21：去抖入口（只置待办；主机在服务端 tick 里按 RESYNC_COALESCE_TICKS 合并执行一次）
        h.requestCoalescedResync();
    }


    /**
     * 上一次的结构状态：GTCEu 会**反复**调用 onStructureFormed / onStructureInvalid
     * （结构成形后，任何方块/零件变化触发一次重查都会再调一次 onStructureFormed），
     * 所以模块这两条日志只记"状态翻转"，避免刷屏；主机侧则每次都打（见 NAC 的 onStructureFormed）。
     */
    private boolean loggedFormed;

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        publishNacSlots(); // ★ 新增：把本模块结构里真实站着的 part 坐标发布给客户端（见 syncedNacSlots）
        // ★ 2026-09-27（外壳连片刷新时机）：与主机侧同款 —— 清单刚发布过 ⇒ 对齐心跳基线并让下一次
        //   心跳马上再确认一遍。**不改变**下面那条紧邻关系（publishNacSlots() 仍紧贴 super）。
        this.nacSlotHeartbeat = 0;
        this.lastNacSlotPartCount = getParts() == null ? -1 : getParts().size();
        // ★ 2026-09-19：同主机侧 —— GTCEu 的 Jade `MultiblockStructureProvider` 读的是
        //   `MultiblockState.hasError()`（不是 isFormed()），而我们的变化闸会提前 return、不碰那条 error
        //   ⇒ 曾经失败过、之后成形的模块会长期显示"结构不完整"。成形即有效 ⇒ 主动清掉陈旧错误。
        //   ⚠️ 必须放在 publishNacSlots() **之后**（`check_ctm_foreign` ⑥ 钉着那条紧邻关系）。
        MultiblockState formedState = getMultiblockState();
        if (formedState != null && formedState.hasError()) {
            formedState.setError(null);
        }
        // ★ 补登记进上游结构映射表：走「方块变化 → checkPatternWithLock → onStructureFormed」成形时，
        //   上游不会自己登记（只有异步成形那条路会）⇒ 之后结构里拆一格没人通知本机，
        //   表现就是「拆掉一块仍然显示成型」。addMapping 以控制器坐标为键，重复登记幂等。
        if (getLevel() instanceof net.minecraft.server.level.ServerLevel formedServerLevel) {
            MultiblockState registerState = getMultiblockState();
            if (registerState != null) {
                com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData
                        .getOrCreate(formedServerLevel).addMapping(registerState);
            }
        }
        // ★ 先催主机按结构重算一次（主机是唯一写入者）⇒ 本机成形即被登记，界面/日志当场就是"已连接"
        requestHostResync();
        boolean linked = hostConnected();
        refreshHostSync();
        if (!loggedFormed) {
            loggedFormed = true;
            // ★ 2026-09-21 降噪：这条本身只在"状态翻转"时打（见 loggedFormed），日志闸必然放行；
            //   挂闸是为了让"已成形 / 结构失效 / 尚未成形"三条共用同一份本机状态记忆，
            //   从而"成因↔失效"反复横跳时，重复的同结论也能被折叠（队长：「要好久」）。
            Level formedLogLevel = getLevel();
            long formedLogNow = formedLogLevel == null ? 0L : formedLogLevel.getGameTime();
            if (this.structureLogGate.shouldEmitInfo(LOG_CONCLUSION_FORMED, "",
                    structureLogLabel(), formedLogNow)) {
                com.gtlmodular.GTLModular.LOGGER.info("[gtlmodular] NAC 模块 已成形 @{}（{}，复合体连接={}）"
                                + "{}",
                        getPos(), kind.nameZh(), linked ? "是" : "否",
                        // ★【A · 连接发起方反转】"显示即事实"：催促默认已停用，日志不许再写"本机已催促"。
                        linked ? "" : (NAC_MODULE_ACTIVE_CONNECT
                                ? "（复合体尚未成形或还没把本机登记 —— 连接由复合体按结构 F 位注入，"
                                + "本机已催促其立刻重算一次，最坏 1 秒内生效）"
                                : "（复合体尚未成形或还没把本机登记 —— 连接由复合体自己按结构 F 位注入，"
                                + "它每 20 tick 自查一次 ⇒ 最坏 1 秒内生效）"));
            }
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        clearNacSlots(); // ★ 新增：结构失效 ⇒ 清单清空（客户端索引随之丢掉本模块这一份）
        // ★ 2026-09-27（外壳连片刷新时机）：清单已清空 ⇒ 心跳的 part 数基线作废
        //   （否则"失效时 part 数恰好与失效前相同"会让心跳以为没变化而漏掉一次重发）。
        this.lastNacSlotPartCount = -1;
        // ★【2026-09-16 修「连接不稳」】这里**不再** removeFromHost(getHost())。
        //   原因（玩家实测证据）：模块自己的检查失败会去改主机的「已连接模块集合」，于是"谁后跑谁覆盖" ——
        //   11 台模块各自成形/失效一次，主机侧「已连接模块」数字就来回跳。
        //   现在集合的唯一权威是主机：本机失效就让主机按结构重算一次（requestHostResync），
        //   它自然会因为 isValidModule 要求 isFormed 而把本机移出集合；
        //   而界面/供电靠 hostConnected() 里的 isFormed() 立刻变"未连接"。
        requestHostResync();
        refreshHostSync();
        if (loggedFormed) {
            loggedFormed = false;
            String detail = NanochipAssemblyComplexMachine.describeStructureFailure(getMultiblockState());
            String invalidReason = detail == null ? "" : detail;
            // ★ 2026-09-21 降噪：同样是"真变化"（成形→失效）⇒ 闸必然放行；
            //   挂闸是为了一旦 GTCEu 在未成形期间重复回调本方法，重复的同结论不再写盘。
            Level invalidLogLevel = getLevel();
            long invalidLogNow = invalidLogLevel == null ? 0L : invalidLogLevel.getGameTime();
            if (this.structureLogGate.shouldEmitInfo(LOG_CONCLUSION_INVALID, invalidReason,
                    structureLogLabel(), invalidLogNow)) {
                com.gtlmodular.GTLModular.LOGGER.info("[gtlmodular] NAC 模块 结构失效 @{}（{}，复合体连接={}；"
                                + "只在状态翻转时打这条，避免刷屏）{}",
                        getPos(), kind.nameZh(), hostConnected() ? "是" : "否",
                        detail == null ? "" : "；GTCEu 首个不符处：" + detail);
            }
        }
        // ★【2026-09-18 第四轮】失效 ⇒ 变化闸归零（与主机同款理由：只有"曾经成形"的机器会走到这里，
        //   不会给"一直缺方块"的持久错误状态开后门）。
        //   注：本行**不能**挪到 super.onStructureInvalid() 与 clearNacSlots() 之间 ——
        //       check_ctm_foreign.js ⑥ 钉着那两行的紧邻关系。
        this.structureCheckGate.reset();
    }

    @Override
    public void onMachineRemoved() {
        // 方块被拆掉：只清本机的镜像字段 + 按下方闸决定是否催主机重算（**不**自己动主机的集合，单一写入者纪律）。
        NanochipAssemblyComplexMachine h = host;
        setHostPosition(null);
        setHost(null);
        // ★★【A · 连接发起方反转】这一处是模块侧**第四**个"催主机"的落点（不走 requestHostResync，
        //   所以单独挂闸）：本常量置 {@code false} 时不催（现行值；2026-11-12 起的默认），
        //   由主机自己在 ≤MODULE_REFRESH_INTERVAL（20 tick = 1 秒）内按结构 F 位重算把本机移出集合 ——
        //   那时 isValidModule 已经因为"这一格上不再是本机"而不成立，快照路（refreshModulesFromStructure）
        //   的"退"方向（stale.removeFromHost）会照旧收敛 ⇒ 不会留下一个"永远连着的幽灵模块"。
        if (NAC_MODULE_ACTIVE_CONNECT && h != null) {
            // ★ 2026-09-21（队长：「一进游戏 NAC 相关的都要重新加载结构，而且要好久，尤其是主机」）：
            //   这一格已经没有模块了 ⇒ 主机要按结构重算一次把本机移出集合；
            //   但同样走去抖入口 —— 拆一排模块就是十几次催促，合并后只落地一次
            //   （判据与合并窗口见 NanochipAssemblyComplexMachine#requestCoalescedResync）。
            h.requestCoalescedResync();
        }
    }


    // ---------- 存档（分流规则） ----------

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        tag.put("GTLModularSplitterRules", splitterRules.save());
        tag.putDouble("GTLModularImpurity", impurity);
        tag.putInt("GTLModularFlushThreshold", autoFlushThreshold);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains("GTLModularSplitterRules")) {
            splitterRules.load(tag.getCompound("GTLModularSplitterRules"));
        }
        impurity = tag.getDouble("GTLModularImpurity");
        if (tag.contains("GTLModularFlushThreshold")) {
            autoFlushThreshold = Math.max(1, Math.min(100, tag.getInt("GTLModularFlushThreshold")));
        }
    }

    @Override
    public BlockPos getHostPosition() {
        return hostPosition;
    }

    @Override
    public void setHostPosition(BlockPos hostPosition) {
        this.hostPosition = hostPosition;
    }

    @Override
    public NanochipAssemblyComplexMachine getHost() {
        return host;
    }

    @Override
    public void setHost(NanochipAssemblyComplexMachine host) {
        this.host = host;
    }

    @Override
    public Class<NanochipAssemblyComplexMachine> getHostType() {
        return NanochipAssemblyComplexMachine.class;
    }

    @Override
    public BlockPos[] getHostScanPositions() {
        return new BlockPos[0];
    }

    // ---------- 界面 ----------

    /**
     * ★【2026-09-21 0.1.24】模块塔：<b>既不覆写 {@code createUI(Player)}，也不覆写 {@code createUIWidget()}</b>，
     * 只往 GTCEu 自己的配置页图标列里加一格「立即更新结构」。
     *
     * <p>与主机同一个原因与同一条修法（取证见 {@code NanochipAssemblyComplexMachine.attachConfigurators}
     * 的注释）：基类 {@code WorkableElectricMultiblockMachine} 自带 fancy 界面
     * （{@code FancyMachineUIWidget} 画标题栏/左列 tab/背包），0.1.16 我覆写 {@code createUI}
     * 自绘朴素页把整套顶掉了 ⇒ 队长看到的"模块的UI也不是机器的样子"。
     *
     * <p>模块也会"半天不成型"（V/P/Z 位放错一格就整塔不成立，而它没有主机的 16 点预检，
     * 只能靠变化闸的地板自愈）。按钮的语义与主机完全一致，见 {@link NanochipStructureRefresh}。
     */
    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        panel.attachConfigurators(new NanochipStructureRefresh.RefreshConfigurator(this));
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        // 与生态园同款（{@code MoltenModuleMachineBase.addDisplayText}）：
        // gtceu 的「最大功率 / 最大配方等级」两行读的是基类 protected 字段 energyContainer / tier，
        // 而那两个字段只在结构成形时赋一次值 —— 吃复合体电的模块要按主机实况刷新，
        // 否则界面会一直显示 0 / ULV（看起来像"没接电"）。
        // ★ 2026-09-16：本方法是在**客户端**被 ComponentPanelWidget.textSupplier 回调的
        //   （见 IDisplayUIMachine.createUI），所以这里读服务端算好的同步快照（synced*），
        //   而不是 getHost()（那个字段只存在于服务端 ⇒ 客户端恒为 null ⇒ 永远显示"未连接"）。
        if (syncedDrawingFromHost) {
            this.tier = syncedHostTier;
            if (getHost() != null) {
                this.energyContainer = getEnergyContainer();
            }
        }
        super.addDisplayText(textList);
        textList.add(Component.literal("NAC 模块: ")
                .append(Component.literal(kind.nameZh()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" · " + kind.nameEn()).withStyle(ChatFormatting.DARK_GRAY))
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable("gtlmodular.nanochip.module.ui.role")
                .append(Component.translatable(kind.langKey("role")).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.literal("复合体连接: ")
                .append(Component.literal(syncedHostConnected ? "已连接" : "未连接")
                        .withStyle(syncedHostConnected ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal(syncedHostInfo).withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.GRAY));
        // 供电：自带能源仓优先；没装就吃复合体的电（组装矩阵会让电）——与生态园同款
        boolean fromHost = syncedDrawingFromHost;
        textList.add(Component.literal("供电: ")
                .append(Component.literal(fromHost ? "复合体中央供电" : "本机能源仓，或未成形")
                        .withStyle(fromHost ? ChatFormatting.AQUA : ChatFormatting.GRAY))
                .append(Component.literal(fromHost && kind == NanochipModuleKind.ASSEMBLY_MATRIX
                        ? " · 最低优先级：别的模块等电时让电" : "")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .withStyle(ChatFormatting.GRAY));
        // ★ 2026-09-21：能量缓存一行（wiki：模块缓存 = NAC最大输入功率 × (2 × 模块最大配方时间)；
        //   主机每 20 tick 按优先级灌；起跑前要求缓存覆盖整条配方）。客户端只读同步快照。
        if (fromHost) {
            long cap = getBufferCapacityEU();
            long cur = getBufferEU();
            textList.add(Component.literal("能量缓存: ")
                    .append(Component.literal(cap > 0L
                                    ? com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(cur)
                                      + " / " + com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(cap) + " EU"
                                    : "等主机设定容量")
                            .withStyle(cap > 0L ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY))
                    .append(Component.literal(" · 优先级 " + (kind == NanochipModuleKind.ASSEMBLY_MATRIX ? "-1：最后补" : "1"))
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (kind.hasUpkeep()) {
            // ★ 2026-09-21（连接专项研究 C 节 ①）：净化水族耗材**不再显示成"1级净化水"** ——
            //   判定早已是"1~16 级任一级都算够"，界面却还拿 seed 里的 grade_1 去查显示名，
            //   给玩家"只认 1 级"的假象。这里按族显示，与判定口径一致。
            boolean waterFamily = NanochipModuleUpkeep.isPurifiedWaterFamily(kind.upkeepFluidId());
            textList.add(Component.literal("运行耗材: ")
                    .append(Component.literal(kind.upkeepMbPerSecond() + " mB/s ").withStyle(ChatFormatting.GOLD))
                    .append((waterFamily
                            ? Component.translatable("gtlmodular.nanochip.module.upkeep.water_any_tier")
                            : NanochipModuleUpkeep.fluidDisplayName(kind.upkeepFluidId(), kind.upkeepFluidId()))
                            .copy().withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(kind == NanochipModuleKind.BIOLOGICAL_COORDINATOR
                            ? " · 湿件/生物满校准可豁免" : " · 从本机流体输入仓扣，检测到哪一级用哪一级")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (kind.isRouter()) {
            textList.add(Component.literal("分流规则: ")
                    .append(Component.literal(splitterRules.rules().size() + " / "
                            + NanochipSplitterRules.MAX_RULES).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" · 打开本仓 GUI 左侧「分流规则」页编辑；红石阈值读本机四周信号")
                            .withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        // 全板浸没：浸泡液杂质（原版三段式：省电 / 正常 / 多耗电）
        if (kind == NanochipModuleKind.BOARD_PROCESSOR) {
            String level = impurity <= 15.0D ? "省电" : (impurity < 65.0D ? "正常" : "多耗电");
            textList.add(Component.literal("浸泡液杂质: ")
                    .append(Component.literal(String.format(java.util.Locale.ROOT, "%.2f%%", impurity))
                            .withStyle(impurity <= 15.0D ? ChatFormatting.GREEN
                                    : (impurity < 65.0D ? ChatFormatting.YELLOW : ChatFormatting.RED)))
                    .append(Component.literal(" · " + level + " · EU ×"
                            + String.format(java.util.Locale.ROOT, "%.3f", impurityEuMultiplier()))
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" 杂质重置阈值 " + autoFlushThreshold + "%")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        // 净化水组织器一族（光学 + 奇异 / 寰宇 / 超因果三台）：供水等级（等级越高加成越大）
        if (kind.isWaterScaledOrganizer()) {
            int grade = highestPurifiedWaterGrade();
            textList.add(Component.literal("净化水等级: ")
                    .append(Component.literal(grade <= 0 ? "未供水" : grade + " 级")
                            .withStyle(grade <= 0 ? ChatFormatting.RED : ChatFormatting.AQUA))
                    .append(Component.literal(grade <= 0
                            ? " · 本模块不给加成，需供 1~16 级净化水"
                            : " · 加成强度 ×" + String.format(java.util.Locale.ROOT, "%.2f", waterStrength()))
                            .withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        // 蚀刻阵列：激光源仓（晶体满校准后生效）
        if (kind == NanochipModuleKind.ETCHING_ARRAY) {
            int laserTier = laserHatchTier();
            textList.add(Component.literal("激光源仓: ")
                    .append(Component.literal(laserTier <= 0 ? "未安装" : "等级 " + laserTier
                            + " · " + laserAmperage() + " A")
                            .withStyle(laserTier <= 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA))
                    .append(Component.literal(" · 晶体满校准后：EU ÷ 每 256A 一档，时长 ÷ 等级−9")
                            .withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * 注意：多方块（这个类）<b>没有</b>侧边配置面板 —— GTCEu 的 {@code IDisplayUIMachine} 里
     * 完全不涉及 {@code ConfiguratorPanel}（已按字节码核对），所以这里不能挂配置页。
     * 分流器规则页实际挂在<b>本模块结构里的真空传送带仓</b>上
     * （见 {@code NanochipVacuumHatchMachine#attachConfigurators}），
     * 那里是仓室（part），走的是带配置面板的 fancy UI。
     */

    /** 分流规则页：每条规则 = 输入色 → 输出色 + 物品过滤 + 红石阈值。 */
    static final class SplitterConfigurator implements IFancyConfigurator {
        private final NanochipModuleMachine machine;

        SplitterConfigurator(NanochipModuleMachine machine) {
            this.machine = machine;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.nanochip.splitter.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable("gtlmodular.nanochip.splitter.title"));
        }

        private static IGuiTexture swatch(NanochipColor color, boolean selected) {
            ColorRectTexture rect = new ColorRectTexture();
            rect.color = 0xFF000000 | color.textColor();
            return selected ? new GuiTextureGroup(rect, new ColorBorderTexture(1, 0xFFFFFFFF)) : rect;
        }

        @Override
        public Widget createConfigurator() {
            int w = 156;
            int rowH = 22;
            WidgetGroup group = new WidgetGroup(0, 0, w, 18 + NanochipSplitterRules.MAX_RULES * rowH + 24);
            group.addWidget(new LabelWidget(4, 2,
                    Component.translatable("gtlmodular.nanochip.splitter.header",
                            NanochipSplitterRules.MAX_RULES).getString()));

            List<NanochipSplitterRules.Rule> rules = machine.splitterRules().rules();
            for (int i = 0; i < rules.size(); i++) {
                NanochipSplitterRules.Rule rule = rules.get(i);
                int y = 16 + i * rowH;
                // 输入色 / 输出色
                group.addWidget(new ButtonWidget(2, y, 18, 18, swatch(rule.in(), true),
                        data -> rule.cycleIn())
                        .setHoverTooltips(Component.translatable("gtlmodular.nanochip.splitter.in",
                                rule.in().nameZh())));
                group.addWidget(new LabelWidget(21, y + 5, "→"));
                group.addWidget(new ButtonWidget(30, y, 18, 18, swatch(rule.out(), true),
                        data -> rule.cycleOut())
                        .setHoverTooltips(Component.translatable("gtlmodular.nanochip.splitter.out",
                                rule.out().nameZh())));
                // 物品过滤：在「不限 + 输入通道里现有的物品」之间循环，避免手打 id
                group.addWidget(new ButtonWidget(52, y, 56, 18, buttonTexture(filterLabel(rule).getString()),
                        data -> cycleFilter(rule))
                        .setHoverTooltips(Component.translatable("gtlmodular.nanochip.splitter.filter.tip")));
                // 红石阈值
                group.addWidget(new IntInputWidget(112, y, 22, 18,
                        rule::redstoneThreshold, rule::setRedstoneThreshold)
                        .setMin(0).setMax(15)
                        .setHoverTooltips(Component.translatable("gtlmodular.nanochip.splitter.redstone.tip")));
                // 删除
                group.addWidget(new ButtonWidget(136, y, 18, 18, new ColorRectTexture(0x40FF5555),
                        data -> {
                            machine.splitterRules().removeRule(rules.indexOf(rule));
                            machine.scheduleRenderUpdate();
                        }).setHoverTooltips(Component.translatable("gtlmodular.nanochip.splitter.remove")));
            }
            group.addWidget(new ButtonWidget(2, 16 + NanochipSplitterRules.MAX_RULES * rowH, 80, 18,
                    buttonTexture(Component.translatable("gtlmodular.nanochip.splitter.add").getString()),
                    data -> {
                        machine.splitterRules().addRule();
                        machine.scheduleRenderUpdate();
                    }));
            group.addWidget(new LabelWidget(88, 20 + NanochipSplitterRules.MAX_RULES * rowH,
                    Component.translatable("gtlmodular.nanochip.splitter.hint").getString()));
            return group;
        }

        private static IGuiTexture buttonTexture(String text) {
            return new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(text));
        }

        private Component filterLabel(NanochipSplitterRules.Rule rule) {
            String filter = rule.itemFilter();
            return Component.literal(filter == null || filter.isBlank() ? "不限" : shortName(filter));
        }

        /** 长 id 截断显示。 */
        private static String shortName(String id) {
            String s = id;
            int colon = s.indexOf(':');
            if (colon >= 0) {
                s = s.substring(colon + 1);
            }
            return s.length() > 18 ? s.substring(0, 17) + "…" : s;
        }

        /** 在「不限 / 该输入色通道里现有的每种物品」之间循环。 */
        private void cycleFilter(NanochipSplitterRules.Rule rule) {
            NanochipAssemblyComplexMachine host = machine.getHost();
            List<String> candidates = new java.util.ArrayList<>();
            candidates.add("");
            if (host != null && host.isFormed()) {
                for (ItemStack proto : host.vacuumNetwork().prototypesOf(rule.in())) {
                    var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(proto.getItem());
                    if (id != null && !candidates.contains(id.toString())) {
                        candidates.add(id.toString());
                    }
                }
            }
            String current = rule.itemFilter() == null ? "" : rule.itemFilter();
            int index = candidates.indexOf(current);
            rule.setFilter(candidates.get((index + 1) % candidates.size()));
        }
    }
}
