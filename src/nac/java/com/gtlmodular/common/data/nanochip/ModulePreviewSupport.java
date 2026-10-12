package com.gtlmodular.common.data.nanochip;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class ModulePreviewSupport {

    // 两种宿主共用限额，防止分离预览实现后放大日志量。
    private static final int MAX_LOGGED_KEYS = 64;
    private static final Set<String> LOGGED_KEYS = new LinkedHashSet<>(16);
    private static final AtomicInteger DROPPED = new AtomicInteger();

    private ModulePreviewSupport() {
    }

    public static MultiblockMachineDefinition lookupDefinition(String namespace, String path) {
        try {
            MachineDefinition def = GTRegistries.MACHINES.get(new ResourceLocation(namespace, path));
            return def instanceof MultiblockMachineDefinition multi ? multi : null;
        } catch (Throwable t) {
            
            return null;
        }
    }

    public static void logOnce(String key, String hostName, BlockPos hostPos, List<ModuleRenderInfo> list) {
        boolean emit;
        synchronized (LOGGED_KEYS) {
            if (LOGGED_KEYS.contains(key)) {
                emit = false;
            } else if (LOGGED_KEYS.size() < MAX_LOGGED_KEYS) {
                LOGGED_KEYS.add(key);
                emit = true;
            } else {
                
                emit = (DROPPED.incrementAndGet() & 0x3F) == 0;
            }
        }
        if (emit) {
            NanochipModContext.logger().info("[gtlmodular] 模块预览: getModulesForRendering 被调用 主机={}@{} 返回={}",
                    hostName, hostPos.toShortString(), list.size());
        }
    }
}
