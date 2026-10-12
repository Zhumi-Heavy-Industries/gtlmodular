package com.gtlmodular.common.config;

import com.gtlmodular.common.machine.independent.eligibility.ThreadEligibilitySettings;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class OtherMachineThreadsConfig {

    private final ForgeConfigSpec.BooleanValue enabled;
    private final ForgeConfigSpec.ConfigValue<List<? extends String>> whitelist;
    private final ForgeConfigSpec.ConfigValue<List<? extends String>> blacklist;

    public OtherMachineThreadsConfig(ForgeConfigSpec.Builder builder) {
        enabled = builder.comment(
                        "其他机器独立线程总开关，默认关闭。只接管已支持的逻辑族及物料/供电 handler。",
                        "新批准入资格 = 总开关开启 && 不在黑名单 &&（标准逻辑精确类匹配 || 在白名单）。",
                        "标准逻辑指 GTLAdditions 的 GTLAddMultipleRecipesLogic、GTLAddMultipleTypeRecipesLogic、",
                        "GTLAddMultipleWirelessRecipesLogic、GTLAddMultipleTypeWirelessRecipesLogic 和 MutableRecipesLogic 本体。",
                        "匹配配方逻辑的实际类，不自动包含子类；Mutable 还须处于上游多配方模式。",
                        "开关和名单只影响新批次资格；已有工作仍须保留至结清。")
                .define("independentThreadsForOtherMachines", false);
        whitelist = builder.comment(
                        "扩展例外白名单，默认空。空白名单仍允许标准逻辑精确类匹配。",
                        "仅接受完整、精确的命名空间:路径 ID；不补命名空间、不支持通配符、不转换大小写。",
                        "尚未注册的合法 ID 会保留；重复 ID 在准入判断中视为同一项。",
                        "白名单不能越过总开关或黑名单，也不保证专有逻辑的合并覆写能够适配。")
                .defineListAllowEmpty("independentThreadsMachineWhitelist", List.<String>of(),
                        ThreadEligibilitySettings::isValidMachineId);
        blacklist = builder.comment(
                        "机器黑名单，默认空，优先于标准类和白名单。ID 格式与白名单相同。",
                        "加入黑名单仅停止新批准入，不能丢弃已扣料的工作。")
                .defineListAllowEmpty("independentThreadsMachineBlacklist", List.<String>of(),
                        ThreadEligibilitySettings::isValidMachineId);
    }

    public ThreadEligibilitySettings snapshot() {
        try {
            return ThreadEligibilitySettings.from(enabled.get(), whitelist.get(), blacklist.get());
        } catch (IllegalStateException unloaded) {
            // 配置加载前必须保持关闭，不能由例外白名单提前接管机器。
            return ThreadEligibilitySettings.from(enabled.getDefault(), whitelist.getDefault(), blacklist.getDefault());
        }
    }
}
