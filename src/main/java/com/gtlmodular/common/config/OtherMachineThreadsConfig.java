package com.gtlmodular.common.config;

import com.gtlmodular.common.machine.independent.eligibility.ThreadEligibilitySettings;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class OtherMachineThreadsConfig {

    private final ForgeConfigSpec.BooleanValue enabled;
    private final ForgeConfigSpec.ConfigValue<List<? extends String>> whitelist;
    private final ForgeConfigSpec.ConfigValue<List<? extends String>> blacklist;
    private final ForgeConfigSpec.IntValue maxBatchTicks;
    private final ForgeConfigSpec.LongValue maxBatchRuns;
    private final ForgeConfigSpec.BooleanValue allowMeIo;
    private final ForgeConfigSpec.IntValue maxBatchesPerRecipe;
    private final ForgeConfigSpec.IntValue candidateCacheTicks;
    private final ForgeConfigSpec.IntValue candidateScanBudget;
    private final ForgeConfigSpec.BooleanValue statsEnabled;

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
        maxBatchTicks = builder.comment(
                        "单条独立批次的最长时长（tick），默认 10000（约 8 分钟）。",
                        "准入时按机器功率把并行数压到这么长以内；0 表示不限制。",
                        "这是安全阀，不是吞吐上限：长配方会自动缩批，避免一批跑几小时甚至几年。",
                        "Maximum ticks per independent batch. Admission reduces the parallel count to fit this clock; 0 disables the cap.",
                        "This is a safety valve, not a throughput rule: long recipes shrink instead of running for hours.")
                .defineInRange("independentThreadsMaxBatchTicks", 10_000, 0, 2_000_000_000);
        maxBatchRuns = builder.comment(
                        "单条独立批次的最多运行份数（realParallels），默认 0 = 不限制。",
                        "用于给极端库存一个绝对安全阀；0 表示只受输入、输出和单批时长的约束。",
                        "Maximum runs per independent batch. 0 means no fixed run cap; inputs, outputs and the batch clock still apply.")
                .defineInRange("independentThreadsMaxBatchRuns", 0L, 0L, Long.MAX_VALUE);
        allowMeIo = builder.comment(
                        "允许 ME 样板/输出处理器参与独立批次（默认关闭）。",
                        "开启后，装了 ME 长数量仓储的机器走 ME 的 long 计数 IO；未装 ME 的机器仍走普通总线。",
                        "ME tick 物品/流体尚未适配：配方带 tick 物料时仍会拒绝，等后续批次。",
                        "Allow ME pattern/output handlers in independent batches. ME tick item/fluid IO is not implemented yet.")
                .define("independentThreadsAllowMeIo", false);
        maxBatchesPerRecipe = builder.comment(
                        "同一配方最多同时占几条独立批次，默认 0 = 不限制。",
                        "矿物处理这类“配方种类远多于线程”的场景建议设为 1：防止一种矿占满所有槽，让后面的矿能轮转。",
                        "这是候选级公平上限；已经扣料的批次不受影响。",
                        "Maximum concurrent independent batches per recipe. 0 = unlimited; 1 is recommended for many-recipe ore processing.")
                .defineInRange("independentThreadsMaxBatchesPerRecipe", 0, 0, 256);
        candidateCacheTicks = builder.comment(
                        "候选查询缓存的有效 tick 数，默认 5；0 表示每 tick 都重新 lookup。",
                        "上游输入变化没有统一 revision，因此只做短 TTL；真实扣料仍逐条校验，缓存不会绕过输入检查。",
                        "Candidate lookup cache TTL in ticks; 0 disables. Real input checks still run per batch.")
                .defineInRange("independentThreadsCandidateCacheTicks", 5, 0, 200);
        candidateScanBudget = builder.comment(
                        "每 tick 最多试算多少条候选，默认 0 = 空槽数 + 8。",
                        "调低可以压 TPS 峰值，但会减少每 tick 能补的批次数；调高让补位更充分但扫描更贵。",
                        "Maximum candidates probed per tick; 0 = free slots + 8.")
                .defineInRange("independentThreadsCandidateScanBudget", 0, 0, 4096);
        statsEnabled = builder.comment(
                        "在机器面板显示候选扫描/批次 tick 的耗时统计，默认关闭。",
                        "用于调 maxActiveThreads、maxBatchTicks 和候选预算；开启只多一次 nanoTime，不写日志。",
                        "Show per-machine candidate/batch timing stats; off by default.")
                .define("independentThreadsStats", false);
    }

    public int maxBatchTicks() {
        try {
            return maxBatchTicks.get();
        } catch (IllegalStateException unloaded) {
            return maxBatchTicks.getDefault();
        }
    }

    public long maxBatchRuns() {
        try {
            return maxBatchRuns.get();
        } catch (IllegalStateException unloaded) {
            return maxBatchRuns.getDefault();
        }
    }

    public boolean allowMeIo() {
        try {
            return allowMeIo.get();
        } catch (IllegalStateException unloaded) {
            return allowMeIo.getDefault();
        }
    }

    public int maxBatchesPerRecipe() {
        try {
            return maxBatchesPerRecipe.get();
        } catch (IllegalStateException unloaded) {
            return maxBatchesPerRecipe.getDefault();
        }
    }

    public int candidateCacheTicks() {
        try {
            return candidateCacheTicks.get();
        } catch (IllegalStateException unloaded) {
            return candidateCacheTicks.getDefault();
        }
    }

    public int candidateScanBudget() {
        try {
            return candidateScanBudget.get();
        } catch (IllegalStateException unloaded) {
            return candidateScanBudget.getDefault();
        }
    }

    public boolean statsEnabled() {
        try {
            return statsEnabled.get();
        } catch (IllegalStateException unloaded) {
            return statsEnabled.getDefault();
        }
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
