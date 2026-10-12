package com.gtlmodular.common.machine.independent.persistence;

import com.gtlmodular.common.machine.independent.core.IndependentBatchSession;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.Optional;

public final class IndependentBatchNbt {
    private final IndependentBatchPersistence<IndependentRecipePayload, Tag> persistence =
            new IndependentBatchPersistence<>(NbtOps.INSTANCE, Tag::copy, new IndependentRecipePayloadCodec());
    private String lastFailure;

    public IndependentBatchPersistence<IndependentRecipePayload, Tag> persistence() { return persistence; }

    public IndependentBatchSession<IndependentRecipePayload, PreservedBatchData<Tag>> load(CompoundTag traitTag) {
        return persistence.load(Optional.ofNullable(traitTag.get(IndependentBatchPersistence.ROOT_KEY)));
    }

    // 未创建/加载会话时不能删除传入 NBT 中尚未接管的原始记录。
    public void save(CompoundTag traitTag,
                     IndependentBatchSession<IndependentRecipePayload, PreservedBatchData<Tag>> session,
                     boolean forDrop) {
        if (session == null) return;
        // forDrop 同样保留所有权；掉落到物品的转移由机器的 drop-save/放置入口负责。
        try {
            Optional<Tag> root = persistence.save(session,
                    Optional.ofNullable(traitTag.get(IndependentBatchPersistence.ROOT_KEY)), this::reportFailure);
            if (root.isPresent()) traitTag.put(IndependentBatchPersistence.ROOT_KEY, root.get());
            else traitTag.remove(IndependentBatchPersistence.ROOT_KEY);
        } catch (RuntimeException exception) {
            // 全部编码成功后才替换目标键，最后一道兜底不能破坏调用方已有的 NBT 或中断区块保存。
            reportFailure(exception);
        }
    }

    private void reportFailure(RuntimeException exception) {
        String reason = exception.getClass().getName() + ": " + exception.getMessage();
        if (reason.equals(lastFailure)) return;
        lastFailure = reason;
        LogUtils.getLogger().error("[independent save] 保留原始批次记录，需核账后恢复: {}", reason, exception);
    }
}
