package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.data.NanochipBlocks;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ★【2026-09-18 第三轮】NAC 主机的 <b>便宜结构预检</b>（O(≤16) 世界查询 ⇒ 决定"这一轮值不值得跑昂贵匹配"）。
 *
 * <p>★【2026-09-18 第四轮】{@link #check} 在同一次扫描里顺便产出**签名**（那 16 格的 {@code BlockState} 指纹），
 * 交给 {@link NanochipStructureCheckGate} 做"无变化就不重跑"的判据。注意签名在预检通过时**必然恒定**
 * —— 所以真正能察觉变化的是闸里的"失败格证据"，详见该闸类注释（那里有完整推导）。
 *
 * <h3>为什么需要它（上一轮遗留的体验问题）</h3>
 * 上一轮为了止血「NAC 卡死」，把主机 {@code asyncCheckPattern} 的两个分支都节流成
 * {@code UNFORMED_SKIP = 6}（见 {@link NanochipStructureCheckThrottle}）。效果是稳态开销降下来了，
 * 但代价是：<b>玩家补上最后一块方块之后，最坏要等约 6 秒才成形</b>（GTCEu 原生最多 1 秒）。
 *
 * <p>队长批准的修法：<b>未成形时先跑一张"便宜的关键方块预检"，只有预检通过才做昂贵的整图案匹配</b>
 * ⇒ 「放最后一块 → 成形」几乎立刻（下一轮检查就成），而昂贵的整图案匹配仍然只在"预检说可能成形"时跑。
 *
 * <h3>预检查哪些格、为什么这些格是「任何合法结构都必然满足」</h3>
 * 取 {@link NanochipComplexPattern} 图案里字符为 {@code B}（{@code meshOnly()} =
 * {@code gtceu:...} 网格接口机壳）或 {@code C}（{@code reinforcement()} = 强化机壳）的格。
 * 这两条谓词在图案里都是 <b>唯一方块</b>的判定（{@code blocks(block(NanochipBlocks.…))}），
 * 不存在 {@code or} 分支 ⇒ <b>只要结构匹配成功，这些格上就必然是那一个方块</b>。
 *
 * <p><b>为什么不取 {@code F}/{@code H}/{@code I}/空格</b>：
 * {@code F}（模块位）允许「模块控制器 / 网格接口机壳 / 仓室」，{@code H} 允许「仓室或机壳」，
 * {@code I} 是"吃任何 GTCEu 仓位"的宽放位，空格是 {@code air()}（塔身让位那 5880 格已被
 * {@code openTowerSpace()} 改成 {@code any()}）—— 这四类都不是唯一方块，拿来做预检会<b>误判</b>。
 * 也正因如此，本类只认 {@code B}/{@code C}，{@link #blockFor} 对其它字符直接抛异常（防呆）。
 *
 * <p><b>点集怎么挑的（可复算）</b>：在全部 8087 个严格格（{@code B} 1099 + {@code C} 3956 +
 * {@code A} 32 + {@code D} 53 + {@code E} 2226 + {@code J} 721，已排除让位格）里，
 * 按「世界方位扇区（45° 一档）× 高度带」各取离控制器最远的一格，得到 <b>16 点</b>：
 * 底层 4 个方位各 1 格（{@code wy = −8}）+ 中枢自 {@code wy = −4} 到 {@code +40} 的 12 个高度各 1 格。
 * ⇒ 这 16 点沿<b>建造进度梯子</b>分布：玩家一层层往上搭时，未搭到的那一层的点必然先失败
 * ⇒ 昂贵匹配只在"差最后几格"时才被放行。复算脚本：{@code _census/check_structure_precheck.js}（现读 {@code NAC_SLICES} 逐点核对）。
 *
 * <h3>查询点数上限（硬性）</h3>
 * {@link #MAX_POINTS} = <b>16</b>，且是<b>常量偏移</b>：预检只做 {@code ≤ 4 × 16 = 64} 次
 * 「控制器 + 固定 (dx,dy,dz)」的 {@code getBlockState}，<b>绝不遍历体积</b>
 * （图案包围盒 63×49×63 = 194481 格 ⇒ 预检最多是它的 0.03%，且每个朝向一旦有一格不符就立刻短路）。
 * FOUR_ROTATIONS 个朝向的枚举见下。
 *
 * <h3>★ 朝向处理：枚举 4 个水平旋转，任一通过即通过（不自己复刻 GTCEu 的 facing→轴 映射）</h3>
 * GTCEu 铺图案时按控制器的 {@code frontFacing} 把图案整体绕竖直轴转（见 {@code NanochipComplexPattern}
 * 类注释 §50；{@code facing=south} 是"原样"、{@code facing=north} 是 180°）。本类<b>不</b>把
 * {@code facing} 翻译成旋转角，而是<b>直接枚举 4 个候选旋转</b>，只要有一个朝向能让 16 点全部对上就放行：
 * <pre>
 *   R0   (wx, wy, wz)
 *   R90  (wz, wy, −wx)
 *   R180 (−wx, wy, −wz)
 *   R270 (−wz, wy, wx)
 * </pre>
 * 其中 {@code (wx, wy, wz) = (NAC_CTRL_X − 图案x, 图案row − NAC_CTRL_ROW, NAC_CTRL_Z − 图案z)}，
 * 也就是"控制器 facing=north 时图案格落到世界的偏移"（该式来自 {@code NanochipComplexPattern}
 * 类注释第 58 行那条<b>已被玩家存档逐格验证</b>的世界映射）。
 *
 * <p><b>为什么这么写而不是查 {@code frontFacing} 后自己算角度</b>：4 个水平朝向对应的 4 种世界布局，
 * <b>恰好就是这 4 个旋转</b>（集合本身与"哪个朝向对应哪个旋转"的命名无关）。
 * 自己写 facing→轴 的映射一旦写错，后果是<b>合法结构永远不过预检 ⇒ 永远不成形</b>（不可接受）；
 * 枚举则天然免疫这个错误方向 —— 最坏只会多查 3 组、或对"镜像布局"漏判（见下面的诚实边界）。
 *
 * <h3>诚实边界（不假装它更聪明）</h3>
 * <ul>
 *   <li><b>未加载区块</b>：任一查询点所在区块未加载 ⇒ 该朝向判为不符。这与 GTCEu 自己的行为一致
 *       （{@code MultiblockState.update} 用 {@code Level.isLoaded(pos)}，未加载就置
 *       {@code UNLOAD_ERROR} 直接判失败），且 NAC 跨 5×5 区块、缺一块也永远成形不了。</li>
 *   <li><b>{@code isFlipped}</b>（扳手翻转）：本类不建模镜像布局 ⇒ 一旦机器处于翻转态，
 *       {@link #mayBeComplete} 直接返回 {@code true}（不拦，交回 GTCEu 原逻辑）。
 *       宁可多跑一次昂贵匹配，也不冒"合法结构被预检永远挡住"的风险。</li>
 *   <li><b>预检只是必要条件，不是充分条件</b>：通过 ≠ 会成形（还有 194481−16 格要 GTCEu 自己判）；
 *       不通过 ⇒ 一定不成形（那 16 格是"任何合法匹配都必须满足"的格）。这正是
 *       {@code 预检失败 ⇒ 跳过昂贵匹配} 的正确性依据。</li>
 *   <li>本类<b>只服务「未成形」这一侧</b>；成形后的失效判定走 GTCEu 的事件路径
 *       （见 {@code NanochipStructureCheckThrottle} 的 javap 证据），<b>不</b>经过本类。</li>
 * </ul>
 */
public final class NanochipStructurePrecheck {

    /** 预检查询点数上限（硬性；常量偏移，<b>不</b>随结构体积变化）。 */
    public static final int MAX_POINTS = 16;

    /** 水平朝向的候选旋转个数（0°/90°/180°/270°，绕控制器竖直轴）。 */
    public static final int FOUR_ROTATIONS = 4;

    /** 网格接口机壳的图案字符（{@code meshOnly()}）。 */
    private static final char MESH = 'B';
    /** 强化机壳的图案字符（{@code reinforcement()}）。 */
    private static final char REINFORCEMENT = 'C';

    /**
     * 预检点：<b>图案坐标</b>（{@code NAC_SLICES} 的 {@code (x, row, z)}），与 {@link #POINT_CHARS} 逐点对齐。
     *
     * <p>挑选口径见类注释（世界方位扇区 × 高度带的"最远严格格"）。这 16 个下标
     * <b>必须</b>与 {@code NAC_SLICES} 里对应格的字符一致 —— 由
     * {@code _census/check_structure_precheck.js} 现读图案数据逐点断言（改了图案没改这里 ⇒ 报红）。
     */
    private static final int[][] PATTERN_POINTS = {
            // {图案 x, 图案 row, 图案 z}
            {32, 48, 26}, // 世界 (−1, 40, 6)  —— 顶部
            {33, 46, 29}, // 世界 (−2, 38, 3)
            {36, 38, 25}, // 世界 (−5, 30, 7)
            {37, 34, 25}, // 世界 (−6, 26, 7)
            {37, 30, 30}, // 世界 (−6, 22, 2)
            {36, 26, 24}, // 世界 (−5, 18, 8)
            {38, 22, 36}, // 世界 (−7, 14, −4)
            {39, 19, 35}, // 世界 (−8, 11, −3)
            {25, 16, 36}, // 世界 (6, 8, −4)
            {40, 13, 33}, // 世界 (−9, 5, −1)
            {21, 8, 29},  // 世界 (10, 0, 3)  —— 控制器同层
            {11, 4, 7},   // 世界 (20, −4, 25)  —— 低位外环
            {62, 0, 36},  // 世界 (−31, −8, −4) —— 底层 4 个方位
            {26, 0, 62},  // 世界 (5, −8, −30)
            {1, 0, 22},   // 世界 (30, −8, 10)
            {26, 0, 0},   // 世界 (5, −8, 32)
    };

    /** 与 {@link #PATTERN_POINTS} 逐点对齐的图案字符（{@code 'B'} 网格接口机壳 / {@code 'C'} 强化机壳）。 */
    private static final char[] POINT_CHARS = {
            MESH, MESH, REINFORCEMENT, REINFORCEMENT, REINFORCEMENT, MESH, REINFORCEMENT, MESH,
            REINFORCEMENT, MESH, MESH, REINFORCEMENT,
            REINFORCEMENT, REINFORCEMENT, REINFORCEMENT, REINFORCEMENT,
    };

    /**
     * 4 个候选旋转展开后的世界偏移，下标 {@code ((旋转 * n) + 点序号) * 3 + 分量}。
     * 构造期一次算好（只用图案常量，不碰注册表）。
     */
    private final int[] rotationOffsets;

    /** 每点期望的方块；首次使用时才解析（{@code BlockEntry.get()} 必须晚于注册期）。 */
    private volatile Block[] resolved;

    public NanochipStructurePrecheck() {
        int n = PATTERN_POINTS.length;
        if (n == 0 || n > MAX_POINTS) {
            throw new IllegalStateException("预检点数 " + n + " 超出上限 " + MAX_POINTS);
        }
        if (POINT_CHARS.length != n) {
            throw new IllegalStateException("PATTERN_POINTS(" + n + ") 与 POINT_CHARS(" + POINT_CHARS.length + ") 长度不一致");
        }
        this.rotationOffsets = new int[FOUR_ROTATIONS * n * 3];
        for (int i = 0; i < n; i++) {
            int[] p = PATTERN_POINTS[i];
            // 控制器 facing=north 那一套：世界偏移 = 控制器 + (31−x, row−8, 32−z)
            int wx = NanochipComplexPattern.NAC_CTRL_X - p[0];
            int wy = p[1] - NanochipComplexPattern.NAC_CTRL_ROW;
            int wz = NanochipComplexPattern.NAC_CTRL_Z - p[2];
            int[][] rot = {
                    {wx, wy, wz},
                    {wz, wy, -wx},
                    {-wx, wy, -wz},
                    {-wz, wy, wx},
            };
            for (int r = 0; r < FOUR_ROTATIONS; r++) {
                int base = (r * n + i) * 3;
                this.rotationOffsets[base] = rot[r][0];
                this.rotationOffsets[base + 1] = rot[r][1];
                this.rotationOffsets[base + 2] = rot[r][2];
            }
        }
    }

    /** 图案字符 → 期望方块（只认唯一方块判定的两个字符；其它字符一律抛异常，防呆）。 */
    private static Block blockFor(char ch) {
        if (ch == MESH) {
            return NanochipBlocks.MESH_INTERFACE_CASING.get();
        }
        if (ch == REINFORCEMENT) {
            return NanochipBlocks.REINFORCEMENT_CASING.get();
        }
        throw new IllegalStateException("预检点字符 '" + ch + "' 没有唯一方块映射（只允许 B/C）");
    }

    private Block[] blocks() {
        Block[] local = this.resolved;
        if (local == null) {
            local = new Block[POINT_CHARS.length];
            for (int i = 0; i < local.length; i++) {
                local[i] = blockFor(POINT_CHARS[i]);
            }
            this.resolved = local;
        }
        return local;
    }

    /** 本次预检实际做了多少次世界查询（诊断用；= 4 朝向 × 16 点的上界，未计短路）。 */
    public static int queryBudget() {
        return FOUR_ROTATIONS * PATTERN_POINTS.length;
    }

    /**
     * 预检结论（★ 第四轮新增签名）：{@code mayForm=false} ⇒ 一定不成形；
     * {@code mayForm=true} ⇒ 可能成形，且 {@code signature} 是"这次命中的那个朝向下 16 格的方块指纹"。
     *
     * <p>签名交给 {@link NanochipStructureCheckGate} 比较：与上次不同 ⇒ 立刻允许重跑昂贵匹配（免等待）。
     * <b>注意</b>签名在"预检通过"时必然恒定（通过条件就是这 16 格恰好是那 16 个方块）
     * ⇒ 它只是队长口径里的那一项，真正能察觉变化的是闸里的"失败格证据"（见该闸类注释）。
     */
    public record Verdict(boolean mayForm, long signature) {
    }

    /** 预检失败（4 个朝向都不符）——一定不成形。 */
    private static final Verdict FAIL = new Verdict(false, NanochipStructureCheckGate.UNKNOWN_SIGNATURE);

    /** 无法判定（未加载 / 翻转态）：放行，签名用哨兵（= 常量，闸会按"没变"处理）。 */
    private static final Verdict PASS_UNKNOWN =
            new Verdict(true, NanochipStructureCheckGate.UNKNOWN_SIGNATURE);

    /** 指纹种子（FNV-1a 64 位）。 */
    private static final long SIGNATURE_SEED = 0xcbf29ce484222325L;
    /** 指纹乘子（FNV-1a 64 位）。 */
    private static final long SIGNATURE_PRIME = 0x100000001b3L;

    /**
     * 把一格方块并入签名。
     *
     * <p>用 {@link System#identityHashCode} 而不是 {@code Block.getId}：{@code BlockState} 在
     * {@code Block.BLOCK_STATE_REGISTRY} 里是**单例**、生命周期与会话相同 ⇒ 身份哈希稳定、零分配、
     * 且不依赖任何映射名（不会因混淆/版本变化而编译不过）。
     * 极端理论碰撞的后果只是"签名没变" ⇒ 由 {@link NanochipStructureCheckGate} 的地板兜底，不会卡死。
     */
    private static long mixSignature(long signature, BlockState state) {
        return (signature ^ System.identityHashCode(state)) * SIGNATURE_PRIME;
    }

    /**
     * <b>便宜预检（带签名）</b>：这一轮"有没有可能成形"，以及"这 16 格现在长什么样"。
     *
     * @param level         服务器世界
     * @param controllerPos 控制器世界坐标
     * @param flipped       机器是否处于扳手翻转态（翻转时本类不建模 ⇒ 直接放行）
     * @return {@link Verdict}；{@code mayForm=false} ⇒ 调用方应跳过昂贵的整图案匹配
     */
    public Verdict check(Level level, BlockPos controllerPos, boolean flipped) {
        // ★★ 2026-11-12【原初模式】总开关：一律视为「可能成型」★★
        //   返回 PASS_UNKNOWN（mayForm=true、签名用哨兵）⇒ 调用方不再裸 return，
        //   昂贵整图案匹配照常跑；签名是常量 ⇒ 变化闸按"没变"处理（原初模式下闸本来也放行）。
        //   置 false 即逐字回到原来的 16 点预检（一行回退）。
        if (NanochipModContext.config().isNanochipOriginalMode()) {
            return PASS_UNKNOWN;
        }
        if (level == null || controllerPos == null) {
            return PASS_UNKNOWN;
        }
        if (flipped) {
            // 不建模镜像布局：宁可多跑一次昂贵匹配，也不冒"合法结构被预检永远挡住"的风险
            return PASS_UNKNOWN;
        }
        Block[] expected = blocks();
        int n = expected.length;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int r = 0; r < FOUR_ROTATIONS; r++) {
            boolean rotationOk = true;
            long signature = SIGNATURE_SEED;
            for (int i = 0; i < n; i++) {
                int base = (r * n + i) * 3;
                probe.set(controllerPos.getX() + this.rotationOffsets[base],
                        controllerPos.getY() + this.rotationOffsets[base + 1],
                        controllerPos.getZ() + this.rotationOffsets[base + 2]);
                // 未加载区块：与 GTCEu 自己（MultiblockState.update 的 UNLOAD_ERROR）同判 —— 该朝向不符
                if (!level.isLoaded(probe)) {
                    rotationOk = false;
                    break;
                }
                BlockState actual = level.getBlockState(probe);
                if (!actual.is(expected[i])) {
                    rotationOk = false;
                    break;
                }
                signature = mixSignature(signature, actual);
            }
            if (rotationOk) {
                return new Verdict(true, signature);
            }
        }
        return FAIL;
    }

    /**
     * 兼容入口（只要布尔结论）：等价于 {@code check(level, controllerPos, flipped).mayForm()}。
     *
     * @param level         服务器世界
     * @param controllerPos 控制器世界坐标
     * @param flipped       机器是否处于扳手翻转态
     * @return {@code false} ⇒ 一定不成形；{@code true} ⇒ 可能成形
     */
    public boolean mayBeComplete(Level level, BlockPos controllerPos, boolean flipped) {
        return check(level, controllerPos, flipped).mayForm();
    }
}
