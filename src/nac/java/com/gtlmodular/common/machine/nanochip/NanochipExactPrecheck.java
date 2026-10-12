package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.data.NanochipBlocks;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 身份格可提前排除不合法布局；能力格指纹用于发现仓室变化，最终成形仍由完整匹配判定。 */
public final class NanochipExactPrecheck {

    public static final int ELEMENTS = 8;

    /** 周期放行一次模块全量匹配，避免预检表有误时永久阻止合法结构。 */
    public static final long MODULE_VALVE_MS = 10_000L;

    private static final int[] NO_CELLS = new int[0];

    private static final long SEED = 0x9e3779b97f4a7c15L;
    private static final long PRIME = 0x100000001b3L;

    public record Verdict(boolean mayForm, long signature, boolean unknown) {

        public static final Verdict UNKNOWN = new Verdict(true, NanochipStructureCheckGate.UNKNOWN_SIGNATURE, true);

        public static final Verdict REJECT = new Verdict(false, NanochipStructureCheckGate.UNKNOWN_SIGNATURE, false);
    }

    private final int[] identity;
    private final int[] capability;
    private final int ctrlX;
    private final int ctrlRow;
    private final int ctrlZ;
    private final long valveMs;
    private final String label;

    /** 注册前不能读取方块实例，角色表延迟到检查时解析。 */
    private volatile Block[] roleBlocks;
    private volatile boolean rolesFailed;

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

    public int identityCellCount() {
        return this.identity.length / 4;
    }

    public int capabilityCellCount() {
        return this.capability.length / 3;
    }

    public String label() {
        return this.label;
    }

    /** 朝向或角色表无法建模时交上游完整匹配，避免把信息不足当作结构错误。 */
    public Verdict scan(Level level, BlockPos controllerPos, Direction frontFacing, Direction upwardsFacing) {
        if (NanochipModContext.config().isNanochipOriginalMode()) {
            return Verdict.UNKNOWN;
        }
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
            return Verdict.UNKNOWN;
        }
        Block[] roles = roles();
        if (roles == null) {
            return Verdict.UNKNOWN;
        }

        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int[] off = new int[3];
        for (int element = 0; element < ELEMENTS; element++) {
            if (!elementMatches(level, controllerPos, probe, off, element, roles)) {
                continue;
            }
            long signature = fingerprint(level, controllerPos, probe, off, element);
            return signature == NanochipStructureCheckGate.UNKNOWN_SIGNATURE
                    ? Verdict.UNKNOWN
                    : new Verdict(true, signature, false);
        }

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
            // 先检查区块是否加载，避免预检触发强制加载。
            if (!level.isLoaded(probe)) {
                return false;
            }
            if (role == NanochipComplexPattern.IDENTITY_ROLE_AIR) {
                if (!level.isEmptyBlock(probe)) {
                    return false;
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

    /** BlockState 使用稳定实例，身份哈希可比较运行期变化而无需遍历注册表。 */
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
                return NanochipStructureCheckGate.UNKNOWN_SIGNATURE;
            }
            BlockState state = level.getBlockState(probe);
            hash = (hash ^ System.identityHashCode(state)) * PRIME;
        }
        // 避免指纹与未知哨兵相同，否则结构变化会被误当成没有可信证据。
        return hash == NanochipStructureCheckGate.UNKNOWN_SIGNATURE ? 1L : hash;
    }

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
                    NanochipBlocks.MESH_INTERFACE_CASING.get(),
                    NanochipBlocks.REINFORCEMENT_CASING.get(),
                    NanochipBlocks.FIREWALL_PROJECTION_CASING.get(),
                    NanochipBlocks.COMPLEX_GLASS.get(),
                    NanochipBlocks.COMPUTATIONAL_MATRIX_CASING.get(),
                    null,
            };
            this.roleBlocks = built;
            return built;
        } catch (RuntimeException | LinkageError e) {
            // 解析失败后交上游匹配，避免预检本身永久挡住机器。
            this.rolesFailed = true;
            NanochipModContext.logger().warn("[gtlmodular] 精确预检（{}）解析方块失败 ⇒ 本台永久放行：{}",
                    this.label, e.toString());
            return null;
        }
    }
}
