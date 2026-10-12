package com.gtlmodular.client.ctm;

import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** 从实际顶点与 UV 反推面内方向，使 CTM 邻居方向跟随模型旋转和 UV 约定。 */
public final class CtmFaceFrame {

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

    public boolean has(Direction face) {
        return this.right[face.ordinal()] != NONE;
    }

    public Direction rightOf(Direction face) {
        int o = this.right[face.ordinal()];
        return o == NONE ? null : Direction.values()[o];
    }

    public Direction upOf(Direction face) {
        int o = this.up[face.ordinal()];
        return o == NONE ? null : Direction.values()[o];
    }

    public boolean complete() {
        for (int i = 0; i < FACE_COUNT; i++) {
            if (this.right[i] == NONE || this.up[i] == NONE) {
                return false;
            }
        }
        return true;
    }

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
        Direction upDir = vDir.getOpposite(); // v 增大指向贴图下方；切向轴不能重合或落在法线轴上。
        if (uDir.getAxis() == upDir.getAxis()
                || uDir.getAxis() == face.getAxis()
                || upDir.getAxis() == face.getAxis()) {
            return null;
        }
        return new int[] { uDir.ordinal(), upDir.ordinal() };
    }

    /** UV 是归一化图集坐标，阈值必须远小于大图集中单张贴图的跨度，避免误判退化。 */
    private static final float UV_EPSILON = 1.0E-6F;

    private static boolean degenerate(float min, float max) {
        return max - min <= UV_EPSILON;
    }

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
