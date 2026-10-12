package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.data.NanochipBlocks;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ★【2026-09-21 队长拍板「上 A+」】NAC（主机 + 14 台模块机）的 <b>精确预检</b> ——
 * 「<b>必然不成形</b>」的便宜判据（只读世界，不碰 GTCEu 任何判定）。
 *
 * <p>依据 = 只读可行性报告 {@code _census/多方块检测_可行性研究.md} §0「需要队长授权的最小改动集」#3/#5
 * 与 §5「A+（= A3）」：把"该不该跑昂贵匹配"的判断，从原来那 16 个采样点，扩成
 * <b>全部身份格（唯一方块谓词格）</b>，并补上<b>能力格的方块指纹</b>（只做"变没变"，不做判定）。
 *
 * <h3>它接在哪一步</h3>
 * {@code MultiblockControllerMachine.asyncCheckPattern(long)}（运行时是 gtlcore 的 {@code @Overwrite} 版）
 * 的第 37–41 字节码是 {@code if (!checkPatternWithTryLock()) return;}，<b>昂贵整图案匹配就跑在那次调用里、
 * 且整段持 {@code patternLock}</b>。本类被用在两处：
 * <ol>
 *   <li>{@code NanochipAssemblyComplexMachine}/{@code NanochipModuleMachine}
 *       <b>覆写的 {@code checkPatternWithTryLock()}</b>：本类说"必然不成形"⇒ 直接 {@code return false}
 *       ⇒ <b>昂贵匹配与持锁都不发生</b>（这正是把 40–132 秒级持锁变成 0 的关键）；</li>
 *   <li>（仅主机）未成形侧的决策链里，把本类给出的<b>能力格指纹</b>当作
 *       {@link NanochipStructureCheckGate} 的签名：能力格一变 ⇒ 立刻放行（免等待）。</li>
 * </ol>
 *
 * <h3>★ 零语义变更：为什么"预检失败 ⇒ 真的不成形"</h3>
 * 本类的判据是<b>成形的必要条件</b>，逐类论证（每一类都给出"它是必要条件"的依据）：
 * <table border="1">
 *   <tr><th>类别</th><th>本类怎么做</th><th>为什么"预检失败 ⇒ 一定不成形"</th></tr>
 *   <tr><td><b>身份格</b>：主机 {@code A/B/C/E/J}（8,034 格）</td>
 *       <td>该格方块必须 {@code ==} 图案要求的那<b>一个</b>方块</td>
 *       <td>这 5 个字符在 {@link NanochipComplexPattern#build} 里分别是
 *           {@code firewall()/meshOnly()/reinforcement()/glass()/matrix()}，而它们逐个都是
 *           {@code Predicates.blocks(唯一一个方块)}（无 {@code or} 分支、无全局计数）
 *           ⇒ 任何一次成功匹配都要求那一格就是那个方块。<b>不相等 ⇒ 该格谓词必假 ⇒ 匹配必失败。</b></td></tr>
 *   <tr><td><b>身份格</b>：模块 {@code P/Z} + 塔身角色为 {@code MESH/REINFORCEMENT} 的字符</td>
 *       <td>同上，方块必须相等</td>
 *       <td>这四类在 {@link NanochipComplexPattern#buildModule} 里分别走
 *           {@code meshOnly()}/{@code reinforcement()}，且 {@code withExtraCandidates()} 对
 *           {@code MESH}/{@code REINFORCEMENT} 角色<b>不追加任何候选</b>（见 {@code extraCandidatesFor} 的
 *           {@code default -> NO_EXTRA_CANDIDATES}）⇒ 仍然是"唯一一个方块"的判定。</td></tr>
 *   <tr><td><b>严格空气格</b>：模块的 {@code ' '}（{@code where(AIR, air())}）</td>
 *       <td>该格必须 {@code level.isEmptyBlock(pos)}</td>
 *       <td>{@code air()} = {@code SimplePredicate.AIR}，其 test 字节码就是
 *           {@code world.isEmptyBlock(state.getPos())}（{@code SimplePredicate_javap}：
 *           {@code lambda$static$1}）⇒ 非空 ⇒ 该格谓词必假 ⇒ 匹配必失败。</td></tr>
 *   <tr><td><b>能力格</b>：主机 {@code F/H/I}（625 格）、模块 {@code V}</td>
 *       <td><b>不做判定</b>，只在对身份格成立的那个布局下算一个 {@code BlockState} 指纹</td>
 *       <td>这三类是"多解"谓词（{@code moduleSlots}/{@code hatchOrCasing}/{@code ignoredSlots}，
 *           多候选 + {@code setMinGlobalLimited} 全局计数）⇒ <b>原理上不能退化成方块身份</b>
 *           （报告 §4.1 已证）⇒ 本类<b>绝不</b>因它们判"不成形"；指纹只用于"变没变"。</td></tr>
 *   <tr><td><b>拿不准的一律放行</b></td>
 *       <td>{@link #scan} 的守卫：世界/坐标为空、客户端侧、{@code frontFacing} 不是水平朝向、
 *           {@code getUpwardsFacing() != NORTH}（扳手改过"扩展朝向"）、身份格表为空、方块解析不出</td>
 *       <td>这些都返回 {@link Verdict#UNKNOWN}（{@code mayForm=true}）⇒ 调用方原样交回
 *           {@code super.checkPatternWithTryLock()} ⇒ GTCEu 自己决定。<b>未取证的一律不拦。</b></td></tr>
 * </table>
 *
 * <h3>★ 朝向守卫的依据（为什么"水平朝向 + up=NORTH"时才敢用 8 元素枚举）</h3>
 * GTCEu 的每格世界偏移由 {@code BlockPattern.setActualRelativeOffset(a,b,c,facing,upwardsFacing,isFlipped)}
 * 算出（javap 原文 {@code _census/_jtmp/BlockPattern_c.txt:1811-2243}）：
 * <ul>
 *   <li>{@code (a,b,c) → (dx,dy,dz)} 的映射由 {@code structureDir={LEFT,UP,FRONT}} × {@code facing} 决定；
 *       {@code facing} 水平时只动 dx/dz（dy 恒由 abc 的 UP 分量取正）；</li>
 *   <li>offset 502–727 的两处"枢轴修正"只在 {@code upwardsFacing ∈ {EAST,WEST,SOUTH}} 时生效
 *       ⇒ <b>upwardsFacing == NORTH 时这两处都不进</b>（goto 727），映射退化成纯水平旋转；</li>
 *   <li>offset 727–797：{@code isFlipped} 时
 *       {@code upwardsFacing ∈ {NORTH,SOUTH}} ⇒ 取反 <b>一个水平轴</b>（facing 南北取反 X、东西取反 Z）；
 *       否则取反 <b>Y</b>（竖直镜像）。</li>
 *   <li>⇒ 当 {@code facing} 水平且 {@code upwardsFacing == NORTH} 时，<b>任何</b>被 GTCEu 接受的布局
 *       （含 {@code allowFlip} 的第二趟）都落在"水平 4 旋转 × 取反一条水平轴"= <b>正方形二面体群 D4 的 8 个元素</b>里。
 *       本类枚举的就是 {@link NanochipComplexPattern#elementOffset} 的全部 8 个元素
 *       （与 {@code auditStructure}/{@code _probe_cell.js} 同一套枚举）。</li>
 *   <li>facing 竖直（UP/DOWN）或 upwardsFacing 被改过 ⇒ 布局可能不在 D4 内 ⇒ <b>守卫直接放行</b>。</li>
 * </ul>
 * <p>枚举 D4 只会让判据<b>更宽松</b>（8 个候选 ⊇ 4 个水平旋转）：即使某个元素的命名与真实朝向对不上，
 * 也只是"多算几个元素"，<b>不会</b>把合法结构判死 —— 这是"宁可多跑一次昂贵匹配，也不冒
 * '合法结构被预检永远挡住'的风险"这条纪律在朝向维度的落实。
 *
 * <h3>★ 诚实边界（不假装它更聪明）</h3>
 * <ul>
 *   <li><b>未加载区块</b>：身份格/空气格落在未加载区块 ⇒ 该元素判否。与 GTCEu 自己的行为一致
 *       （{@code MultiblockState.update} 用 {@code Level.isLoaded}，未加载就置 {@code UNLOAD_ERROR} 判失败）。
 *       本类先判 {@code isLoaded} 再读方块，<b>绝不</b>强加载区块。</li>
 *   <li><b>能力格指纹不是判定</b>：它只回答"跟上次真跑匹配时比，F/H/I（或 V）那几格的方块指纹变了没有"。
 *       而 GTCEu 的全局计数（"能源仓 ≥1/≤2"、"真空仓 ≥1"、"模块 ≤15"）与"跨控制器部件归属"都能在
 *       <b>方块完全不变</b>的情况下改变匹配结论（报告 §5「A3 风险」三条例外边界）⇒ 所以
 *       {@link NanochipStructureCheckGate} 的<b>地板照旧保留</b>（"最坏延迟 = 地板值"这条有界不等价没有变）。</li>
 *   <li><b>不列入的格</b>：主机 {@code D}（{@code frames(Naquadah)} —— 材料框架在多档变体下不保证唯一，
 *       本模组自己的 {@code auditStructure} 也把它排除）、控制器的 {@code ~} 那一格（恒真，无信息量）、
 *       {@code ' '}/{@code K}（{@code any()}，零成本跳过）、模块的
 *       {@code ASSEMBLY/FRAME/SHEET/UEV_CASING/MATRIX_CASING/REINFORCED_CASING/GLASS} 角色
 *       （带追加候选 ⇒ 不是唯一方块）。<b>少列 = 只削弱过滤力，永不会造成误判。</b></li>
 *   <li><b>模块的兜底阀门</b>：模块的身份格表是本轮<b>新造</b>的坐标表（登记自 {@code buildModule} 现算的
 *       {@code aisles}），为了不让"表万一与图案脱节"变成"模块永不成形"这种不可接受的故障，
 *       模块实例带一个<b>时间阀门</b>（{@link #MODULE_VALVE_MS}）：同一台机器连续被判"必然不成形"时，
 *       每满一次阀门就放行一次真匹配。<b>它只影响频率（最坏 = 每 {@value #MODULE_VALVE_MS} ms 一次），
 *       不影响任何"成形/不成形"的结论</b>（结论永远由 GTCEu 给）。主机<b>不带</b>阀门 —— 主机的身份格表
 *       直接来自生产在用的 {@code strictCells()}（{@code _census/check_all_patterns.js} /
 *       {@code _census/check_pattern_skip_cells.js} / {@code _census/check_structure_precheck.js}
 *       逐格钉住图案数据），且"预检失败 ⇒ 不成形"是上面论证过的必要条件。</li>
 * </ul>
 *
 * @see NanochipComplexPattern#hostIdentityCells()
 * @see NanochipComplexPattern#moduleIdentityCells(String)
 */
public final class NanochipExactPrecheck {

    /** 候选布局元素个数：水平 4 旋转 × 翻转（= 正方形二面体群 D4 的全部 8 个元素）。 */
    public static final int ELEMENTS = 8;

    /**
     * 模块侧的兜底阀门（毫秒）：连续判"必然不成形"时，每满这么久放行一次真匹配。
     *
     * <p>取 {@code 10_000}（= 10 秒）的理由：模块的单次匹配成本比主机小约 400 倍（报告 §4.2），
     * 所以"最坏每 10 秒一次"对单线程检查池的占用可忽略；而它保证"表万一错了"时模块最迟 10 秒就能成形。
     * <b>主机不用阀门</b>（理由见类注释末段）。
     */
    public static final long MODULE_VALVE_MS = 10_000L;

    /** 空表（模块没有"能力格指纹"这一路：模块的闸签名按既有口径固定用 UNKNOWN_SIGNATURE）。 */
    private static final int[] NO_CELLS = new int[0];

    /** 指纹种子（与 {@link NanochipStructurePrecheck} 的 FNV 种子不同，避免两个签名巧合相等）。 */
    private static final long SEED = 0x9e3779b97f4a7c15L;
    /** 指纹乘子（FNV-1a 64 位）。 */
    private static final long PRIME = 0x100000001b3L;

    /**
     * 预检结论。
     *
     * @param mayForm   {@code false} ⇒ <b>必然不成形</b>（调用方应直接返回"没拿到锁/不成形"，
     *                  绝不进入昂贵匹配）；{@code true} ⇒ 不拦（交回 GTCEu 原路径）
     * @param signature 身份格全对的那个布局下、<b>能力格</b>的方块指纹（{@code UNKNOWN_SIGNATURE} = 给不出）
     * @param unknown   {@code true} ⇒ 本次是"守卫放行"（拿不准），签名不可用作闸的输入
     */
    public record Verdict(boolean mayForm, long signature, boolean unknown) {

        /** 守卫放行：不拦，且签名为"未知"（调用方应沿用上一级签名）。 */
        public static final Verdict UNKNOWN = new Verdict(true, NanochipStructureCheckGate.UNKNOWN_SIGNATURE, true);

        /** 必然不成形。 */
        public static final Verdict REJECT = new Verdict(false, NanochipStructureCheckGate.UNKNOWN_SIGNATURE, false);
    }

    /** 身份格扁平表：每 4 个 int = {@code {图案 x, 图案 row, 图案 z, 角色码}}（角色码见 NanochipComplexPattern.IDENTITY_ROLE_*）。 */
    private final int[] identity;
    /** 能力格扁平表：每 3 个 int = {@code {图案 x, 图案 row, 图案 z}}；空表 = 不做指纹（模块侧）。 */
    private final int[] capability;
    /** 控制器在图案里的列下标（世界偏移 = {@code ctrlX - x}）。 */
    private final int ctrlX;
    /** 控制器在图案里的行下标（世界偏移 = {@code row - ctrlRow}）。 */
    private final int ctrlRow;
    /** 控制器在图案里的剖面下标（世界偏移 = {@code ctrlZ - z}）。 */
    private final int ctrlZ;
    /** 兜底阀门间隔（毫秒；0 = 关闭）。 */
    private final long valveMs;
    /** 诊断标签（"NAC 主机" / "NAC 模块 xxx"）。 */
    private final String label;

    /** 角色码 → 方块；首次使用时才解析（{@code BlockEntry.get()} 必须晚于注册期）。 */
    private volatile Block[] roleBlocks;
    /** 角色码解析失败 ⇒ 永久放行（fail-open）。 */
    private volatile boolean rolesFailed;

    /** 兜底阀门上次放行的时刻（{@code System.nanoTime()}；构造时初始化 ⇒ 第一轮不会被阀门提前放行）。 */
    private long valveLastNanos = System.nanoTime();

    private NanochipExactPrecheck(int[] identity, int[] capability,
                                 int ctrlX, int ctrlRow, int ctrlZ, long valveMs, String label) {
        this.identity = identity == null ? NO_CELLS : identity;
        this.capability = capability == null ? NO_CELLS : capability;
        this.ctrlX = ctrlX;
        this.ctrlRow = ctrlRow;
        this.ctrlZ = ctrlZ;
        this.valveMs = valveMs;
        this.label = label;
    }

    /**
     * 主机实例：身份格 = {@code A/B/C/E/J}（8,034 格）、能力格 = {@code F/H/I}（625 格）、
     * 控制器锚点 = {@code NAC_CTRL_X/ROW/Z}、<b>无阀门</b>。
     */
    public static NanochipExactPrecheck host() {
        return new NanochipExactPrecheck(
                NanochipComplexPattern.hostIdentityCells(),
                NanochipComplexPattern.hostCapabilityCells(),
                NanochipComplexPattern.NAC_CTRL_X,
                NanochipComplexPattern.NAC_CTRL_ROW,
                NanochipComplexPattern.NAC_CTRL_Z,
                0L,
                "NAC 主机");
    }

    /**
     * 某台模块机的实例（身份格 = {@code buildModule} 登记的那张表）。
     *
     * @param moduleId {@code MultiblockMachineDefinition.getId().getPath()}（= {@code NanochipModuleKind.machineId()}）
     * @return 实例；<b>没登记过 ⇒ {@code null}</b>（调用方必须按"放行"处理，不许当成"不成形"）
     */
    public static NanochipExactPrecheck forModule(String moduleId) {
        int[] cells = NanochipComplexPattern.moduleIdentityCells(moduleId);
        if (cells == null) {
            NanochipModContext.logger().debug("[gtlmodular] 精确预检：模块 {} 没有登记身份格表 ⇒ 本台不做精确预检（放行）", moduleId);
            return null;
        }
        return new NanochipExactPrecheck(cells, NO_CELLS,
                NanochipComplexPattern.MOD_CTRL_X,
                NanochipComplexPattern.MOD_CTRL_ROW,
                NanochipComplexPattern.MOD_CTRL_Z,
                MODULE_VALVE_MS,
                "NAC 模块 " + moduleId);
    }

    /** 诊断用：身份格数（= 每次决策要读的世界格数上界，未计短路与元素枚举）。 */
    public int identityCellCount() {
        return this.identity.length / 4;
    }

    /** 诊断用：能力格数（主机 625；模块 0）。 */
    public int capabilityCellCount() {
        return this.capability.length / 3;
    }

    /** 诊断用：标签。 */
    public String label() {
        return this.label;
    }

    /**
     * <b>精确预检</b>：这一轮"有没有可能成形"（只在能给出确定结论时才拦）。
     *
     * @param level          服务端世界（客户端/空 ⇒ 放行）
     * @param controllerPos  控制器世界坐标（空 ⇒ 放行）
     * @param frontFacing    控制器正脸朝向（非水平 ⇒ 放行，见类注释的朝向守卫）
     * @param upwardsFacing  控制器的"扩展朝向"（{@code != NORTH} ⇒ 放行）
     * @return {@link Verdict}；{@code mayForm=false} ⇒ <b>调用方必须直接返回"不成形"，不许再跑昂贵匹配</b>
     */
    public Verdict scan(Level level, BlockPos controllerPos, Direction frontFacing, Direction upwardsFacing) {
        // ★★ 2026-11-12【原初模式】总开关：一律视为「可能成型」⇒ 调用方绝不裸 return ★★
        //   放行后昂贵整图案匹配照常跑，"成形 / 不成形"100% 由 gtceu/gtlcore 自己判。
        //   位置在守卫之前：本类是主机与模块共用的唯一预检入口 ⇒ 这一处总闸同时覆盖两侧
        //   （主机 NanochipAssemblyComplexMachine#checkPatternWithTryLock / #asyncCheckPattern、
        //     模块 NanochipModuleMachine#checkPatternWithTryLock）。
        //   置 false 即逐字回到原来的「8 个元素都不成立 ⇒ REJECT」行为（一行回退）。
        if (NanochipModContext.config().isNanochipOriginalMode()) {
            return Verdict.UNKNOWN;
        }
        // ── 守卫：拿不准的一律放行 ──
        if (level == null || controllerPos == null || level.isClientSide) {
            return Verdict.UNKNOWN;
        }
        if (frontFacing == null || !frontFacing.getAxis().isHorizontal()) {
            return Verdict.UNKNOWN;
        }
        if (upwardsFacing != Direction.NORTH) {
            return Verdict.UNKNOWN;
        }
        int count = this.identity.length / 4;
        if (count == 0) {
            return Verdict.UNKNOWN;                       // 没有身份格 ⇒ 本类无话可说
        }
        Block[] roles = roles();
        if (roles == null) {
            return Verdict.UNKNOWN;                       // 方块解析不出 ⇒ 放行（fail-open）
        }

        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int[] off = new int[3];
        for (int element = 0; element < ELEMENTS; element++) {
            if (!elementMatches(level, controllerPos, probe, off, element, roles)) {
                continue;
            }
            // 该元素下全部身份格成立 ⇒ 不拦；顺便给出能力格指纹（变了 ⇒ 让闸立刻放行 = 免等待）
            long signature = fingerprint(level, controllerPos, probe, off, element);
            return signature == NanochipStructureCheckGate.UNKNOWN_SIGNATURE
                    ? Verdict.UNKNOWN                       // 指纹给不出 ⇒ 当作"拿不准"，交回上一级签名
                    : new Verdict(true, signature, false);
        }

        // ★ 8 个元素都不成立 ⇒ 必然不成形（论证见类注释"零语义变更"表）
        if (this.valveMs > 0) {
            long now = System.nanoTime();
            if ((now - this.valveLastNanos) / 1_000_000L >= this.valveMs) {
                this.valveLastNanos = now;
                NanochipModContext.logger().debug("[gtlmodular] 精确预检（{}）连续判\"必然不成形\"，"
                        + "按兜底阀门放行一次真匹配 @{}（阀门 = {} ms）", this.label, controllerPos, this.valveMs);
                return Verdict.UNKNOWN;
            }
        }
        return Verdict.REJECT;
    }

    /** 某一个布局元素下，是否<b>全部</b>身份格都成立（命中即停 ⇒ 未成形时通常头几格就短路）。 */
    private boolean elementMatches(Level level, BlockPos controllerPos, BlockPos.MutableBlockPos probe,
                                   int[] off, int element, Block[] roles) {
        int count = this.identity.length / 4;
        for (int i = 0; i < count; i++) {
            int base = i * 4;
            int role = this.identity[base + 3];
            NanochipComplexPattern.elementOffset(element,
                    this.ctrlX - this.identity[base],
                    this.identity[base + 1] - this.ctrlRow,
                    this.ctrlZ - this.identity[base + 2], off);
            probe.set(controllerPos.getX() + off[0], controllerPos.getY() + off[1], controllerPos.getZ() + off[2]);
            // 未加载区块：与 GTCEu 自己（MultiblockState.update 的 UNLOAD_ERROR）同判 —— 该元素不符。
            // ★ 必须先 isLoaded 再读：绝不强加载区块。
            if (!level.isLoaded(probe)) {
                return false;
            }
            if (role == NanochipComplexPattern.IDENTITY_ROLE_AIR) {
                if (!level.isEmptyBlock(probe)) {
                    return false;                          // air() 的 test 就是 world.isEmptyBlock(pos)
                }
            } else {
                Block want = roles[role];
                if (want == null || !level.getBlockState(probe).is(want)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 能力格指纹（那个布局下 625 格的 {@code BlockState} 身份哈希）。
     *
     * <p>用 {@link System#identityHashCode} 而不是 {@code Block.getId}：{@code BlockState} 在
     * {@code Block.BLOCK_STATE_REGISTRY} 里是<b>单例</b>、生命周期与会话相同 ⇒ 身份哈希稳定、零分配、
     * 不依赖任何映射名。极端理论碰撞的后果只是"指纹没变" ⇒ 由闸的地板兜底，不会卡死。
     */
    private long fingerprint(Level level, BlockPos controllerPos, BlockPos.MutableBlockPos probe,
                             int[] off, int element) {
        int count = this.capability.length / 3;
        if (count == 0) {
            return NanochipStructureCheckGate.UNKNOWN_SIGNATURE;
        }
        long hash = SEED ^ ((long) element * 0x9e3779b97f4a7c15L);
        for (int i = 0; i < count; i++) {
            int base = i * 3;
            NanochipComplexPattern.elementOffset(element,
                    this.ctrlX - this.capability[base],
                    this.capability[base + 1] - this.ctrlRow,
                    this.ctrlZ - this.capability[base + 2], off);
            probe.set(controllerPos.getX() + off[0], controllerPos.getY() + off[1], controllerPos.getZ() + off[2]);
            if (!level.isLoaded(probe)) {
                return NanochipStructureCheckGate.UNKNOWN_SIGNATURE;   // 给不出可信指纹 ⇒ 交回上一级签名
            }
            BlockState state = level.getBlockState(probe);
            hash = (hash ^ System.identityHashCode(state)) * PRIME;
        }
        // 绝不与"未知"哨兵撞车（撞了会让闸把"变了"误读成"没变"，但真正的影响面只有延迟 ⇒ 仍要避免）
        return hash == NanochipStructureCheckGate.UNKNOWN_SIGNATURE ? 1L : hash;
    }

    /** 角色码 → 方块（惰性；失败一次即永久放行）。 */
    private Block[] roles() {
        if (this.rolesFailed) {
            return null;
        }
        Block[] local = this.roleBlocks;
        if (local != null) {
            return local;
        }
        try {
            Block[] built = new Block[]{
                    NanochipBlocks.MESH_INTERFACE_CASING.get(),          // IDENTITY_ROLE_MESH
                    NanochipBlocks.REINFORCEMENT_CASING.get(),           // IDENTITY_ROLE_REINFORCEMENT
                    NanochipBlocks.FIREWALL_PROJECTION_CASING.get(),     // IDENTITY_ROLE_FIREWALL
                    NanochipBlocks.COMPLEX_GLASS.get(),                  // IDENTITY_ROLE_GLASS
                    NanochipBlocks.COMPUTATIONAL_MATRIX_CASING.get(),    // IDENTITY_ROLE_MATRIX
                    null,                                                // IDENTITY_ROLE_AIR（不查方块）
            };
            this.roleBlocks = built;
            return built;
        } catch (RuntimeException | LinkageError e) {
            // 精确预检绝不能成为新的故障源：解析不出就永久放行（照旧跑 GTCEu 原路径）
            this.rolesFailed = true;
            NanochipModContext.logger().warn("[gtlmodular] 精确预检（{}）解析方块失败 ⇒ 本台永久放行：{}",
                    this.label, e.toString());
            return null;
        }
    }
}
