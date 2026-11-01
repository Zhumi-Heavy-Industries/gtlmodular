package com.gtlmodular.client.ctm;

import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <b>面内坐标帧</b>：把「贴图 u 增大方向（右）/ v 减小方向（上）」解析成<b>世界方向</b>，逐面一份。
 *
 * <h2>为什么从 quad 几何反推，而不是写死 6 面表</h2>
 * CTM 的 8 个 position（TOP / TOP_RIGHT / …）是定义在<b>贴图自身坐标系</b>里的：
 * 索引 1 的 tile「只在右边连通」指的是<b>贴图图像的右边</b>。它在世界里的方向对 6 个面各不相同，
 * 且取决于 vanilla 贴图到面的朝向约定（这个约定由 {@code FaceBakery} 决定，抄错就会把接缝画到错的边上）。
 *
 * <p>与其硬编码一张 6×2 的表去猜约定，这里直接从<b>已烘焙 quad 的 4 个顶点的位置 + UV</b> 反推：
 * UV 小的一对顶点 → UV 大的一对顶点，其重心之差的方向就是「+u（右）」在世界里的方向；
 * v 同理取反即「上」。这样得到的帧必然与游戏实际使用的朝向一致（无论模型有没有 uvlock / 旋转），
 * 也与贴图作者画 CTM tile 时参照的 vanilla 面朝向一致。
 *
 * <p>只对「平面、四边形、UV 两组各 2 顶点、且 +u/+v 落在两个不同的切向轴上」的 quad 成立；
 * 任何一条不满足就判为未知（返回 {@code null}），调用方据此回退到第 0 格（= 现状外观）。
 */
public final class CtmFaceFrame {

    /** 未知帧的哨兵值（与 {@link Direction} 的 ordinal 不冲突）。 */
    public static final int NONE = -1;

    private static final int FACE_COUNT = 6;

    private final int[] right = new int[FACE_COUNT];
    private final int[] up = new int[FACE_COUNT];

    private CtmFaceFrame() {
        for (int i = 0; i < FACE_COUNT; i++) {
            this.right[i] = NONE;
            this.up[i] = NONE;
        }
    }

    /**
     * 从模型的实际 quad 反推 6 个面的帧。
     *
     * @param model 已烘焙模型（用它的 3 参数 {@code getQuads}，不涉及 ModelData）
     * @param state 要取 quad 用的状态（传本方块 {@code defaultBlockState()}）
     * @return 帧对象；单面推不出时该面保持未知（不影响别的面）
     */
    public static CtmFaceFrame derive(BakedModel model, BlockState state) {
        CtmFaceFrame f = new CtmFaceFrame();
        for (Direction face : Direction.values()) {
            List<BakedQuad> quads = model.getQuads(state, face, RandomSource.create(42L));
            if (quads.isEmpty()) {
                continue;
            }
            int[] frame = deriveFace(quads.get(0), face);
            if (frame != null) {
                f.right[face.ordinal()] = frame[0];
                f.up[face.ordinal()] = frame[1];
            }
        }
        return f;
    }

    /** 该面是否已解析出帧。 */
    public boolean has(Direction face) {
        return this.right[face.ordinal()] != NONE;
    }

    /** 该面的「贴图右边」对应的世界方向；未知时返回 {@code null}。 */
    public Direction rightOf(Direction face) {
        int o = this.right[face.ordinal()];
        return o == NONE ? null : Direction.values()[o];
    }

    /** 该面的「贴图上边」对应的世界方向；未知时返回 {@code null}。 */
    public Direction upOf(Direction face) {
        int o = this.up[face.ordinal()];
        return o == NONE ? null : Direction.values()[o];
    }

    /** 6 个面是否全部解析成功（全成功才允许开启连接纹理）。 */
    public boolean complete() {
        for (int i = 0; i < FACE_COUNT; i++) {
            if (this.right[i] == NONE || this.up[i] == NONE) {
                return false;
            }
        }
        return true;
    }

    /**
     * ★ 2026-09-17 起<b>包内可见</b>（原来 private）：机器方块那条
     * （{@code MachineCtmBakedModel}）也要用它 —— 机器的外壳 quad 只能在<b>渲染期</b>拿到
     * （LDLib 的 {@code RendererBakedModel} 三参 {@code getQuads} 恒空表，见那边的类注释），
     * 所以它按"当次真实吐出的外壳 quad"逐状态反推帧，复用同一套数学（避免两处判据漂移）。
     *
     * @return {@code {rightOrdinal, upOrdinal}}，或 {@code null}（几何不满足判据）
     */
    static int[] deriveFace(BakedQuad quad, Direction face) {
        int[] data = quad.getVertices();
        if (data.length < 32 || data.length % 4 != 0) {
            return null;
        }
        int stride = data.length / 4;
        float[] px = new float[4];
        float[] py = new float[4];
        float[] pz = new float[4];
        float[] uu = new float[4];
        float[] vv = new float[4];
        for (int i = 0; i < 4; i++) {
            int b = i * stride;
            px[i] = Float.intBitsToFloat(data[b]);
            py[i] = Float.intBitsToFloat(data[b + 1]);
            pz[i] = Float.intBitsToFloat(data[b + 2]);
            uu[i] = Float.intBitsToFloat(data[b + 4]);
            vv[i] = Float.intBitsToFloat(data[b + 5]);
        }
        Direction uDir = edgeDirection(px, py, pz, uu);
        Direction vDir = edgeDirection(px, py, pz, vv);
        if (uDir == null || vDir == null) {
            return null;
        }
        Direction upDir = vDir.getOpposite(); // v 增大 = 贴图往下
        // +u / +v 必须落在两个不同的切向轴上，且都不是面法线轴
        if (uDir.getAxis() == upDir.getAxis()
                || uDir.getAxis() == face.getAxis()
                || upDir.getAxis() == face.getAxis()) {
            return null;
        }
        return new int[] { uDir.ordinal(), upDir.ordinal() };
    }

    /**
     * UV 退化判据的极小 epsilon。
     *
     * <p>★ 2026-09-16 实机修正（真 bug，日志根因）：<b>烘焙后 quad 的 UV 是"图集归一化坐标"，不是 0..16 的贴图像素</b>
     * —— 16×16 的贴图落进一个 16384×16384 的方块图集时，它的 u 跨度只有 `16 / 16384 = 9.77e-4`。
     * 第一版这里写的是 `1.0E-3F`，比它还大 ⇒ **每一面都被判成"UV 退化"** ⇒ 面内坐标帧全部未知
     * ⇒ `getModelData` 不提供连接信息 ⇒ 连接纹理永远退回第 0 格（实机日志原文：
     * `CTM: 已包装 4 个模型条目（… （面内坐标帧未知 ⇒ 回退第 0 格））`）。
     *
     * <p>现在改成 `1.0E-6F`：既远小于最小可能的贴图跨度（1/16384 ≈ 9.8e-4），又远大于 float 在该量级
     * （约 0.25）上的量化误差（≈3e-8）。分组不再依赖跨度大小（只按 min/max 的中点分两组）⇒ 与图集尺寸无关 ✓。
     * 自检 `check_ctm_model.js` 里用"16384 图集 + 16px 贴图"的真实数值做了回归断言。
     */
    private static final float UV_EPSILON = 1.0E-6F;

    /**
     * 当 UV 分量的 4 个值里出现退化成"同一个值"时视为未知（返回 {@code null}）。
     * 判据用 {@link #UV_EPSILON}，见它的注释（这是本轮实机修掉的那个真 bug）。
     */
    private static boolean degenerate(float min, float max) {
        return max - min <= UV_EPSILON;
    }

    /**
     * 按某个 UV 分量把 4 个顶点分成两组（小/大各 2 个），两组重心之差落在哪个轴上、朝哪边。
     *
     * @param c 该 UV 分量的 4 个值
     * @return 对应的世界方向；分组不是 2+2、差值为 0、或不是单一轴时返回 {@code null}
     */
    private static Direction edgeDirection(float[] px, float[] py, float[] pz, float[] c) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float value : c) {
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
        }
        if (degenerate(min, max)) {
            return null;
        }
        float mid = (min + max) * 0.5F;
        float lx = 0.0F;
        float ly = 0.0F;
        float lz = 0.0F;
        float hx = 0.0F;
        float hy = 0.0F;
        float hz = 0.0F;
        int ln = 0;
        int hn = 0;
        for (int i = 0; i < 4; i++) {
            if (c[i] < mid) {
                lx += px[i];
                ly += py[i];
                lz += pz[i];
                ln++;
            } else {
                hx += px[i];
                hy += py[i];
                hz += pz[i];
                hn++;
            }
        }
        if (ln != 2 || hn != 2) {
            return null;
        }
        float dx = hx / hn - lx / ln;
        float dy = hy / hn - ly / ln;
        float dz = hz / hn - lz / ln;
        int sx = step(dx);
        int sy = step(dy);
        int sz = step(dz);
        if (sx != 0 && sy == 0 && sz == 0) {
            return axisDirection(Direction.Axis.X, sx);
        }
        if (sy != 0 && sx == 0 && sz == 0) {
            return axisDirection(Direction.Axis.Y, sy);
        }
        if (sz != 0 && sx == 0 && sy == 0) {
            return axisDirection(Direction.Axis.Z, sz);
        }
        return null;
    }

    /** 把模型空间位移量化成 -1 / 0 / +1（1 模型单位以下视为 0）。 */
    private static int step(float d) {
        if (Math.abs(d) < 0.5F) {
            return 0;
        }
        return d > 0.0F ? 1 : -1;
    }

    private static Direction axisDirection(Direction.Axis axis, int sign) {
        return Direction.fromAxisAndDirection(axis,
                sign > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }
}
