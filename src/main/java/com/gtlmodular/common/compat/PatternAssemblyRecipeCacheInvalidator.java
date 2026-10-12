package com.gtlmodular.common.compat;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.nanochip.NanochipRecipeCacheHooks;
import com.gtlmodular.common.machine.MoltenPatternAssemblyPartMachine;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

/** 识别失败缓存会随存档恢复，配方环境就绪后需允许这些槽重新识别。 */
public final class PatternAssemblyRecipeCacheInvalidator implements NanochipRecipeCacheHooks.Invalidator {

    private static final int TYPE_NOT_TRIED = -1;
    private static final int TYPE_UNKNOWN = -2;

    private Field slotTypeCacheField;
    private boolean slotTypeCacheFieldFailed;
    private boolean cacheClearLogged;

    @Override
    public void invalidateStaleCaches(LevelChunk chunk, BooleanSupplier claimFailureLog) {
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            // 部件机器不是 BlockEntity，必须从持有它的方块实体取得实例。
            if (blockEntity instanceof IMachineBlockEntity holder
                    && holder.getMetaMachine() instanceof MoltenPatternAssemblyPartMachine machine) {
                invalidateStaleTypeCache(machine, claimFailureLog);
            }
        }
    }

    private void invalidateStaleTypeCache(MoltenPatternAssemblyPartMachine machine, BooleanSupplier claimFailureLog) {
        try {
            Field field = resolveSlotTypeCacheField();
            if (field == null) {
                return;
            }
            int[] cache = (int[]) field.get(machine);
            if (cache == null) {
                return;
            }
            int cleared = 0;
            for (int i = 0; i < cache.length; i++) {
                if (cache[i] == TYPE_UNKNOWN) {
                    cache[i] = TYPE_NOT_TRIED;
                    cleared++;
                }
            }
            if (cleared > 0 && !cacheClearLogged) {
                cacheClearLogged = true;
                GTLModular.LOGGER.info("[gtlmodular] 主机表 byType 别名：已作废样板总成 {} 个「识别不出」的旧缓存（存档里存的 -2）"
                        + "⇒ 那些样板槽会按新环境重扫一次 @{}", cleared, machine.getPos());
            }
        } catch (Throwable t) {
            if (claimFailureLog.getAsBoolean()) {
                GTLModular.LOGGER.warn("[gtlmodular] 主机表 byType 别名：清样板识别缓存时异常（已忽略）：{}", t.toString());
            }
        }
    }

    // 自有字段不参与混淆；解析失败后停止重试，避免每次加载区块都重复报错。
    private Field resolveSlotTypeCacheField() {
        if (slotTypeCacheField != null) {
            return slotTypeCacheField;
        }
        if (slotTypeCacheFieldFailed) {
            return null;
        }
        try {
            Field candidate = MoltenPatternAssemblyPartMachine.class.getDeclaredField("slotRecipeType");
            if (candidate.getType() != int[].class) {
                slotTypeCacheFieldFailed = true;
                return null;
            }
            candidate.setAccessible(true);
            slotTypeCacheField = candidate;
            return candidate;
        } catch (Throwable t) {
            slotTypeCacheFieldFailed = true;
            GTLModular.LOGGER.warn("[gtlmodular] 主机表 byType 别名：拿不到样板总成的识别缓存字段（slotRecipeType）：{}"
                    + " ⇒ 旧存档里被判过「识别不出」的样板槽不会自动重扫；"
                    + "验证时请把那个样板槽重新放一次（或 Shift+右键点一下槽）再试", t.toString());
            return null;
        }
    }
}
