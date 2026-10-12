package com.gtlmodular.common.machine.independent.control;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtlmodular.common.machine.independent.persistence.IndependentBatchPersistence;
import com.gtlmodular.common.machine.independent.persistence.IndependentOwnershipData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.Objects;

public final class IndependentBatchOwnership {
    public static final String TRANSFER = "gtlmodular_independent_transfer";
    public enum Result { READY, WAIT, DUPLICATE }

    private final MetaMachine machine;
    private UUID token;
    private boolean pending;
    private ItemStack placedItem;
    private CompoundTag placedTag;
    private Tag savedRoot;

    public IndependentBatchOwnership(MetaMachine machine) { this.machine = machine; }

    public UUID token() { return token; }

    public void load(CompoundTag tag) {
        token = tag.hasUUID(TRANSFER) ? tag.getUUID(TRANSFER) : null;
        pending = true;
        placedItem = null;
        placedTag = null;
        savedRoot = tag.get(IndependentBatchPersistence.ROOT_KEY).copy();
    }

    public void placed(ItemStack item) {
        placedItem = item;
        placedTag = item.getTag() == null ? null : item.getTag().copy();
    }

    public Result resolve() {
        if (!(machine.getLevel() instanceof ServerLevel level)
                || level.captureBlockSnapshots || level.restoringBlockSnapshots) return Result.WAIT;
        var data = IndependentOwnershipData.get(level);
        if (pending && token != null) {
            var entry = data.entry(token);
            if (entry != null && !entry.spent() && sourceLevel(level, entry.owner()) == null) return Result.WAIT;
            UUID previous = token;
            token = data.claim(token, owner(level), source -> sourceVacant(level, source, previous));
            if (token == null) {
                pending = false;
                placedItem = null;
                placedTag = null;
                savedRoot = null;
                return Result.DUPLICATE;
            }
        } else if (token == null) {
            token = data.issue(owner(level));
        }
        if (pending) {
            pending = false;
            // Forge 此时已结束放置事件和快照回滚，才能清理创造模式留下的手持副本。
            if (placedItem != null && placedTag != null && placedItem.getTag() != null
                    && Objects.equals(placedTag.get(TRANSFER), placedItem.getTag().get(TRANSFER))
                    && Objects.equals(placedTag.get(IndependentBatchPersistence.ROOT_KEY),
                            placedItem.getTag().get(IndependentBatchPersistence.ROOT_KEY))) {
                IndependentDropTransfer.strip(placedItem);
            }
            placedItem = null;
            placedTag = null;
            machine.markDirty();
        }
        return Result.READY;
    }

    private BatchOwnershipLedger.Owner owner(ServerLevel level) {
        return new BatchOwnershipLedger.Owner(level.dimension().location().toString(), machine.getPos().asLong());
    }

    private boolean sourceVacant(ServerLevel level, BatchOwnershipLedger.Owner source, UUID previous) {
        var sourceLevel = sourceLevel(level, source);
        if (sourceLevel == null) return false;
        BlockPos pos = BlockPos.of(source.position());
        // 搬运后源区块可能已卸载；不能把未加载当作源机器不存在。
        sourceLevel.getChunkAt(pos);
        var controller = IndependentLogicAccess.controller(MetaMachine.getMachine(sourceLevel, pos));
        return controller == null || !controller.ownsWork() || !previous.equals(controller.ownershipToken());
    }

    private ServerLevel sourceLevel(ServerLevel level, BatchOwnershipLedger.Owner source) {
        return level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(source.dimension())));
    }

    public void save(CompoundTag tag) {
        Tag root = tag.get(IndependentBatchPersistence.ROOT_KEY);
        if (machine.getLevel() instanceof ServerLevel level) {
            var data = IndependentOwnershipData.get(level);
            // 原批次推进或换批后，旧快照不能在拆机时抢领已经交付过的产物。
            if (token != null && !pending && savedRoot != null && !savedRoot.equals(root)) {
                data.retire(token);
                token = null;
            }
            // 旧档也要在首次被 /clone 或 BG 序列化时取得身份，不能等第一次 tick。
            if (token == null) token = data.issue(owner(level));
        }
        if (token != null) tag.putUUID(TRANSFER, token);
        savedRoot = root == null ? null : root.copy();
    }

    public void complete() {
        if (token != null && !pending && machine.getLevel() instanceof ServerLevel level) {
            IndependentOwnershipData.get(level).retire(token);
        }
        token = null;
        pending = false;
        placedItem = null;
        placedTag = null;
        savedRoot = null;
    }
}
