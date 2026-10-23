package com.gtlmodular.common.machine.output;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

/**
 * 把 {@link ProductFlusher} 接到 AE 网络上。
 *
 * <p>只做一件事：把“批量刷写”翻译成 {@link MEStorage#insert}，并把实际接受量原样返回。
 * 部分成功、拒收、离线全部由上层逻辑处理，这里不做任何假设。</p>
 */
public final class AeStorageSink implements ProductFlusher.Sink<AEKey> {
    private final MEStorage storage;
    private final IActionSource source;

    public AeStorageSink(MEStorage storage, IActionSource source) {
        if (storage == null) {
            throw new IllegalArgumentException("storage 不能为 null");
        }
        if (source == null) {
            throw new IllegalArgumentException("source 不能为 null");
        }
        this.storage = storage;
        this.source = source;
    }

    @Override
    public long insert(AEKey key, long amount) {
        if (key == null || amount <= 0) {
            return 0;
        }
        return storage.insert(key, amount, Actionable.MODULATE, source);
    }
}
