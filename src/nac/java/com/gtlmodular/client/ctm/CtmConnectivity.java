package com.gtlmodular.client.ctm;

/** 邻居方向按贴图坐标解释，面朝向由 CtmFaceFrame 提供，避免写死六面朝向导致接缝错边。 */
public final class CtmConnectivity {

    private CtmConnectivity() {
    }

    public static final int P_TOP = 1;
    public static final int P_TOP_RIGHT = 1 << 1;
    public static final int P_RIGHT = 1 << 2;
    public static final int P_BOTTOM_RIGHT = 1 << 3;
    public static final int P_BOTTOM = 1 << 4;
    public static final int P_BOTTOM_LEFT = 1 << 5;
    public static final int P_LEFT = 1 << 6;
    public static final int P_TOP_LEFT = 1 << 7;

    public static final int ORTHO = P_TOP | P_RIGHT | P_BOTTOM | P_LEFT;

    public static final int[] STEP_RIGHT = { 0, +1, +1, +1, 0, -1, -1, -1 };
    public static final int[] STEP_UP = { +1, +1, 0, -1, -1, -1, 0, +1 };

    public static final int CTM_TILES = 47;
    public static final int COMPACT_TILES = 5;

    private static final int[] R_REQUIRED = {
            0x00, 0x04, 0x44, 0x40, 0x14, 0x50, 0x15, 0x54, 0x57, 0x5D, 0xF5, 0xD7,
            0x10, 0x1C, 0x7C, 0x70, 0x05, 0x41, 0x45, 0x51, 0xD5, 0x75, 0x7D, 0x5F,
            0x11, 0x1F, 0xFF, 0xF1, 0x1D, 0x74, 0x17, 0x5C, 0xF7, 0xDF, 0x77, 0xDD,
            0x01, 0x07, 0xC7, 0xC1, 0x47, 0xD1, 0xC5, 0x71, 0xFD, 0x7F, 0x55,
    };

    private static final int[] R_FORBIDDEN = {
            0x55, 0x51, 0x11, 0x15, 0x49, 0x25, 0x4A, 0x29, 0xA8, 0xA2, 0x0A, 0x28,
            0x45, 0x41, 0x01, 0x05, 0x52, 0x94, 0x92, 0xA4, 0x2A, 0x8A, 0x82, 0xA0,
            0x44, 0x40, 0x00, 0x04, 0x42, 0x09, 0x48, 0x21, 0x08, 0x20, 0x88, 0x22,
            0x54, 0x50, 0x10, 0x14, 0x90, 0x24, 0x12, 0x84, 0x02, 0x80, 0xAA,
    };

    public static int ctmIndex(int mask) {
        int m = mask & 0xFF;
        for (int i = 0; i < CTM_TILES; i++) {
            if ((m & R_REQUIRED[i]) == R_REQUIRED[i] && (m & R_FORBIDDEN[i]) == 0) {
                return i;
            }
        }
        return 0;
    }

    /** 子象限模板不可用时采用整面兜底，避免缺面；选图需让连通边上的边框消失。 */
    public static int compactIndex(int mask) {
        int o = mask & ORTHO;
        if (o == ORTHO) {
            return 1;
        }
        if (o == (P_TOP | P_BOTTOM)) {
            return 2; // 上下连通需保留左右边框，对应第 2 张。
        }
        if (o == (P_LEFT | P_RIGHT)) {
            return 3; // 左右连通需保留上下边框，对应第 3 张。
        }
        if (o == 0) {
            return 0;
        }
        return 4;
    }

    public static int compactWholeSprite(int mask) {
        return (mask & 0xFF) == 0xFF ? 1 : 0;
    }

    /** 按贴图半区选择相邻边与夹角，避免把其他坐标约定的位掩码直接套入。 */
    public static int compactQuadrantSprite(int mask, int hu, int hv) {
        int uBit = (hu == 0) ? P_LEFT : P_RIGHT;
        int vBit = (hv == 0) ? P_TOP : P_BOTTOM;
        int dBit = (hu == 0) ? (hv == 0 ? P_TOP_LEFT : P_BOTTOM_LEFT)
                : (hv == 0 ? P_TOP_RIGHT : P_BOTTOM_RIGHT);
        boolean nu = (mask & uBit) != 0;
        boolean nv = (mask & vBit) != 0;
        if (nu && nv) {
            return (mask & dBit) != 0 ? 1 : 4;
        }
        if (nu) {
            return 3;
        }
        if (nv) {
            return 2;
        }
        return 0;
    }

    /** 仅整面 47 格可用正交结果剪枝；compact 仍需查对角，否则会丢失内角。 */
    public static int singletonIndex(int orthoMask) {
        int o = orthoMask & ORTHO;
        if (o == 0) {
            return 0;
        }
        if (o == (P_LEFT | P_RIGHT)) {
            return 2;
        }
        if (o == (P_TOP | P_BOTTOM)) {
            return 24;
        }
        if (o == P_RIGHT) {
            return 1;
        }
        if (o == P_LEFT) {
            return 3;
        }
        if (o == P_BOTTOM) {
            return 12;
        }
        if (o == P_TOP) {
            return 36;
        }
        return -1;
    }
}
