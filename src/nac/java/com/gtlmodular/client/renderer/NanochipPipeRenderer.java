package com.gtlmodular.client.renderer;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.client.renderer.machine.MachineRenderer;
import com.gtlmodular.common.machine.nanochip.NanochipPipeMachine;
import com.lowdragmc.lowdraglib.client.bakedpipeline.FaceQuad;
import com.lowdragmc.lowdraglib.client.model.ModelFactory;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.function.Consumer;

/** 机器走 LDLib 动态渲染入口，直接按连接掩码生成几何，避免增加用于外观的方块状态。 */
public class NanochipPipeRenderer extends MachineRenderer {

    private static final ResourceLocation PIPE_TEXTURE =
            NanochipModContext.assetId("block/overlay/vacuum_pipe");

    private static final ResourceLocation BAR_TEXTURE =
            NanochipModContext.assetId("block/overlay/vacuum_pipe_bar");

    public static final int PIPE_CORE_MIN = 5;
    public static final int PIPE_CORE_MAX = 11;

    public static final double PIPE_THICKNESS = (PIPE_CORE_MAX - PIPE_CORE_MIN) / 16.0;

    /** 芯与臂必须等粗，避免连接处出现台阶。 */
    public static final int PIPE_ARM_MIN = 5;
    public static final int PIPE_ARM_MAX = 11;

    private static final double CORE_MIN = PIPE_CORE_MIN / 16.0;
    private static final double CORE_MAX = PIPE_CORE_MAX / 16.0;
    private static final double ARM_MIN = PIPE_ARM_MIN / 16.0;
    private static final double ARM_MAX = PIPE_ARM_MAX / 16.0;

    public static final double PIPE_ARM_THICKNESS = (PIPE_ARM_MAX - PIPE_ARM_MIN) / 16.0;

    /** 叠加条略向外扩，避免与管身共面闪烁。 */
    private static final float OVERLAY_INFLATE = 0.001F;

    /** 外端内缩量大于叠加条外扩量，避免相邻管道端面和叠加条在方块边界重叠。 */
    private static final float ARM_END_INSET = 0.002F;

    private static final Direction[] DIRECTIONS = Direction.values();

    private static final float[] UV_CENTRE = {5F, 5F, 11F, 11F};

    private static final float BAND_MIN = 5F;
    private static final float BAND_MAX = 11F;

    /** 横向 UV 限在十字带内以避开四角底色，沿轴保持坐标比例以免芯臂接缝拉伸。 */
    private static float[] uvFor(Direction face, AABB box, Direction.Axis along) {
        if (along == null) {
            return UV_CENTRE;
        }
        int uAxis = cubeUvUAxis(face);
        boolean uNeg = cubeUvUNeg(face);
        if (uAxis == along.ordinal()) {
            float a = uNeg ? (float) (16.0 * (1.0 - max(box, uAxis))) : (float) (16.0 * min(box, uAxis));
            float b = uNeg ? (float) (16.0 * (1.0 - min(box, uAxis))) : (float) (16.0 * max(box, uAxis));
            return new float[]{a, BAND_MIN, b, BAND_MAX};
        }
        int vAxis = cubeUvVAxis(face);
        boolean vNeg = cubeUvVNeg(face);
        if (vAxis == along.ordinal()) {
            float a = vNeg ? (float) (16.0 * (1.0 - max(box, vAxis))) : (float) (16.0 * min(box, vAxis));
            float b = vNeg ? (float) (16.0 * (1.0 - min(box, vAxis))) : (float) (16.0 * max(box, vAxis));
            return new float[]{BAND_MIN, a, BAND_MAX, b};
        }
        return UV_CENTRE;
    }

    private static Direction.Axis alongAxisOf(int mask, Direction face) {
        Direction.Axis found = null;
        for (Direction d : DIRECTIONS) {
            if (d.getAxis() == face.getAxis()) continue;
            if ((mask & NanochipPipeMachine.bit(d)) == 0) continue;
            if (found != null && found != d.getAxis()) return null;
            found = d.getAxis();
        }
        return found;
    }

    private static double min(AABB box, int axis) {
        return switch (axis) {
            case 0 -> box.minX;
            case 1 -> box.minY;
            default -> box.minZ;
        };
    }

    private static double max(AABB box, int axis) {
        return switch (axis) {
            case 0 -> box.maxX;
            case 1 -> box.maxY;
            default -> box.maxZ;
        };
    }

    private static int cubeUvUAxis(Direction face) {
        return switch (face) {
            case WEST, EAST -> 2;
            default -> 0;
        };
    }

    private static boolean cubeUvUNeg(Direction face) {
        return face == Direction.NORTH || face == Direction.EAST;
    }

    private static int cubeUvVAxis(Direction face) {
        return face == Direction.UP || face == Direction.DOWN ? 2 : 1;
    }

    private static boolean cubeUvVNeg(Direction face) {
        return face != Direction.UP;
    }

    private TextureAtlasSprite pipeSprite;
    private TextureAtlasSprite barSprite;

    public NanochipPipeRenderer(ResourceLocation modelLocation) {
        super(modelLocation);
    }

    @Override
    public void renderMachine(List<BakedQuad> quads, MachineDefinition definition, MetaMachine machine,
                              Direction frontFacing, Direction side, RandomSource rand,
                              Direction modelFacing, ModelState modelState) {
        if (!(machine instanceof NanochipPipeMachine pipe)) {
            super.renderMachine(quads, definition, machine, frontFacing, side, rand, modelFacing, modelState);
            return;
        }
        int mask = pipe.connectionMask();
        appendLayer(quads, side, mask, pipeSprite(), -1, 0.0F);
        appendLayer(quads, side, mask, barSprite(), NanochipPipeMachine.TINT_BAR, OVERLAY_INFLATE);
    }

    private static void appendLayer(List<BakedQuad> quads, Direction side, int mask,
                                    TextureAtlasSprite sprite, int tint, float inflate) {
        if (side == null) {
            // 芯面兼作管臂内端盖，保留它以避免从管内看穿。
            AABB core = coreBox(inflate);
            for (Direction face : DIRECTIONS) {
                quads.add(bake(face, core, sprite, tint, false,
                        uvFor(face, core, alongAxisOf(mask, face))));
            }
            for (Direction arm : DIRECTIONS) {
                if ((mask & NanochipPipeMachine.bit(arm)) != 0) {
                    AABB box = armBox(arm, inflate);
                    for (Direction face : DIRECTIONS) {
                        if (face != arm && face != arm.getOpposite()) {
                            quads.add(bake(face, box, sprite, tint, false,
                                    uvFor(face, box, arm.getAxis())));
                        }
                    }
                }
            }
            return;
        }
        // 只有管臂末端使用方向剔除桶，避免内部细管面被相邻实心方块误裁。
        if ((mask & NanochipPipeMachine.bit(side)) != 0) {
            quads.add(bake(side, armBox(side, inflate), sprite, tint, true, UV_CENTRE));
        }
    }

    private static AABB coreBox(float inflate) {
        return new AABB(CORE_MIN - inflate, CORE_MIN - inflate, CORE_MIN - inflate,
                CORE_MAX + inflate, CORE_MAX + inflate, CORE_MAX + inflate);
    }

    /** 管臂从芯表面开始，避免多根管臂在芯内共面；叠加层向芯内微缩以封住细缝。 */
    private static AABB armBox(Direction side, float inflate) {
        double min = ARM_MIN - inflate;
        double max = ARM_MAX + inflate;
        double inset = ARM_END_INSET - inflate;
        double startMax = CORE_MAX - inflate;
        double startMin = CORE_MIN + inflate;
        double x0 = min, y0 = min, z0 = min;
        double x1 = max, y1 = max, z1 = max;
        if (side.getStepX() > 0) {
            x0 = startMax;
            x1 = 1.0 - inset;
        } else if (side.getStepX() < 0) {
            x0 = inset;
            x1 = startMin;
        } else if (side.getStepY() > 0) {
            y0 = startMax;
            y1 = 1.0 - inset;
        } else if (side.getStepY() < 0) {
            y0 = inset;
            y1 = startMin;
        } else if (side.getStepZ() > 0) {
            z0 = startMax;
            z1 = 1.0 - inset;
        } else {
            z0 = inset;
            z1 = startMin;
        }
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    /** 必须显式给 UV，cubeUV 会取到贴图四角的底色；仅边界端面允许方向剔除。 */
    private static BakedQuad bake(Direction face, AABB box, TextureAtlasSprite sprite, int tint,
                                  boolean cull, float[] uv) {
        return FaceQuad.builder(face, sprite)
                .cube(box)
                .uv0(uv[0], uv[1])
                .uv1(uv[2], uv[3])
                .tintIndex(tint)
                .cull(cull)
                .bake();
    }

    private TextureAtlasSprite pipeSprite() {
        if (pipeSprite == null) {
            pipeSprite = ModelFactory.getBlockSprite(PIPE_TEXTURE);
        }
        return pipeSprite;
    }

    private TextureAtlasSprite barSprite() {
        if (barSprite == null) {
            barSprite = ModelFactory.getBlockSprite(BAR_TEXTURE);
        }
        return barSprite;
    }

    /** 图集重载后旧 sprite 不再有效，需清缓存并重新声明贴图依赖。 */
    @Override
    public void onPrepareTextureAtlas(ResourceLocation atlas, Consumer<ResourceLocation> register) {
        super.onPrepareTextureAtlas(atlas, register);
        if (TextureAtlas.LOCATION_BLOCKS.equals(atlas)) {
            register.accept(PIPE_TEXTURE);
            register.accept(BAR_TEXTURE);
        }
        this.pipeSprite = null;
        this.barSprite = null;
    }
}
