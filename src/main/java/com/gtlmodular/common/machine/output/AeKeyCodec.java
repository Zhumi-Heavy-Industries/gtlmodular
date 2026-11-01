package com.gtlmodular.common.machine.output;

import appeng.api.stacks.AEKey;
import net.minecraft.nbt.CompoundTag;

/** AE2 的 {@link AEKey} 编解码（1.20.1 / AE2 15.x 的 {@code toTagGeneric} / {@code fromTagGeneric}）。 */
public final class AeKeyCodec implements ProductTablePersistence.KeyCodec<AEKey> {
    public static final AeKeyCodec INSTANCE = new AeKeyCodec();

    private AeKeyCodec() {}

    @Override
    public CompoundTag write(AEKey key) {
        return key.toTagGeneric();
    }

    @Override
    public AEKey read(CompoundTag tag) {
        try {
            return AEKey.fromTagGeneric(tag);
        } catch (RuntimeException exception) {
            // 认不出的 key（模组被移除、数据损坏）只跳过这一条，不让整张表失效。
            return null;
        }
    }
}
