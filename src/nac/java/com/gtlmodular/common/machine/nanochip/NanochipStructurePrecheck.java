package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.data.NanochipBlocks;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 只采样唯一方块格以提前排除不完整结构；通过仍须完整匹配，不能直接判成形。 */
public final class NanochipStructurePrecheck {

    public static final int MAX_POINTS = 16;

    public static final int FOUR_ROTATIONS = 4;

    private static final char MESH = 'B';
    private static final char REINFORCEMENT = 'C';

    private static final int[][] PATTERN_POINTS = {
            {32, 48, 26},
            {33, 46, 29},
            {36, 38, 25},
            {37, 34, 25},
            {37, 30, 30},
            {36, 26, 24},
            {38, 22, 36},
            {39, 19, 35},
            {25, 16, 36},
            {40, 13, 33},
            {21, 8, 29},
            {11, 4, 7},
            {62, 0, 36},
            {26, 0, 62},
            {1, 0, 22},
            {26, 0, 0},
    };

    private static final char[] POINT_CHARS = {
            MESH, MESH, REINFORCEMENT, REINFORCEMENT, REINFORCEMENT, MESH, REINFORCEMENT, MESH,
            REINFORCEMENT, MESH, MESH, REINFORCEMENT,
            REINFORCEMENT, REINFORCEMENT, REINFORCEMENT, REINFORCEMENT,
    };

    private final int[] rotationOffsets;

    /** 注册前不能取得 BlockEntry 的方块实例，因此延迟到实际检查时解析。 */
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

    public static int queryBudget() {
        return FOUR_ROTATIONS * PATTERN_POINTS.length;
    }

    public record Verdict(boolean mayForm, long signature) {
    }

    private static final Verdict FAIL = new Verdict(false, NanochipStructureCheckGate.UNKNOWN_SIGNATURE);

    private static final Verdict PASS_UNKNOWN =
            new Verdict(true, NanochipStructureCheckGate.UNKNOWN_SIGNATURE);

    private static final long SIGNATURE_SEED = 0xcbf29ce484222325L;
    private static final long SIGNATURE_PRIME = 0x100000001b3L;

    /** BlockState 使用稳定实例，身份哈希可供同一运行期比较而无需遍历注册表。 */
    private static long mixSignature(long signature, BlockState state) {
        return (signature ^ System.identityHashCode(state)) * SIGNATURE_PRIME;
    }

    public Verdict check(Level level, BlockPos controllerPos, boolean flipped) {
        if (NanochipModContext.config().isNanochipOriginalMode()) {
            return PASS_UNKNOWN;
        }
        if (level == null || controllerPos == null) {
            return PASS_UNKNOWN;
        }
        if (flipped) {
            // 镜像布局不在此采样模型中，交完整匹配判断，避免误拒绝合法结构。
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

    public boolean mayBeComplete(Level level, BlockPos controllerPos, boolean flipped) {
        return check(level, controllerPos, flipped).mayForm();
    }
}
