package com.gtlmodular.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class GTLModularConfig {

    private GTLModularConfig() {
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue DUAL_FLUID_TAX_ENABLED =
            BUILDER.comment("M2：熔岩高炉单元双流体税总开关")
                    .define("dualFluidTax.enabled", true);

    private static final ForgeConfigSpec.ConfigValue<String> LAVA_FLUID_ID =
            BUILDER.comment("熔岩税流体的注册 id（玩家语境熔岩=原版岩浆）。",
                            "原版岩浆 minecraft:lava；若走 GT 材料熔岩可改 gtceu:lava")
                    .define("dualFluidTax.lavaFluidId", "minecraft:lava");

    private static final ForgeConfigSpec.ConfigValue<String> BLAZE_FLUID_ID =
            BUILDER.comment("液态烈焰税流体的注册 id（烈焰高炉同款燃料，材料 blaze 的流体）")
                    .define("dualFluidTax.blazeFluidId", "gtceu:blaze");

    private static final ForgeConfigSpec.IntValue IV_BASELINE_MB_PER_SECOND =
            BUILDER.comment("IV 档每 1 秒、每种流体的扣量（mb）。",
                            "默认 83 ≈ 10×√(8192/120)（按 IV 档额定功率 8192 EU/t 代入原公式），可自行调整。",
                            "每高于 IV 一档再乘 perTierMultiplier")
                    .defineInRange("dualFluidTax.ivBaselineMbPerSecond", 83, 0, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.DoubleValue PER_TIER_MULTIPLIER =
            BUILDER.comment("高于 IV 后，每提升一档电压的流体扣量倍率")
                    .defineInRange("dualFluidTax.perTierMultiplier", 4.0, 0.0, 1024.0);

    private static final ForgeConfigSpec.IntValue BASELINE_TIER =
            BUILDER.comment("基准电压档（默认 5 = GTValues.IV）；该档倍率为 1")
                    .defineInRange("dualFluidTax.voltageBaselineTier", 5, 0, 30);

    private static final ForgeConfigSpec.BooleanValue ALLOW_BELOW_BASELINE =
            BUILDER.comment("是否允许低于基准电压运行（默认 false：低于 IV 不允许运行）")
                    .define("dualFluidTax.allowBelowBaselineTier", false);

    private static final ForgeConfigSpec.IntValue DRAIN_INTERVAL_TICKS =
            BUILDER.comment("扣税周期（tick）。20 = 每游戏秒扣一次")
                    .defineInRange("dualFluidTax.drainIntervalTicks", 20, 1, 1200);

    private static final ForgeConfigSpec.BooleanValue COIL_DISCOUNT_ENABLED =
            BUILDER.comment("M3：按线圈温度降耗总开关（并行由并行控制仓提供，线圈不再提供并行）")
                    .define("coilDiscount.enabled", true);

    private static final ForgeConfigSpec.IntValue COIL_DISCOUNT_BASE_TEMPERATURE =
            BUILDER.comment("降耗基准温度（K）：等于该温度记第 1 档，倍率 ×1。默认 1800 = 铜镍线圈")
                    .defineInRange("coilDiscount.baseTemperature", 1800, 1, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue COIL_DISCOUNT_TEMPERATURE_PER_LEVEL =
            BUILDER.comment("温度档跨度（K）：每升高该值记 +1 档")
                    .defineInRange("coilDiscount.temperaturePerLevel", 1800, 1, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.DoubleValue COIL_DISCOUNT_FACTOR_PER_LEVEL =
            BUILDER.comment("每高 1 档的 EU/t 系数（倍率 = 该值^(档−1)；0.95 = 每档 −5%）")
                    .defineInRange("coilDiscount.factorPerLevel", 0.95, 0.01, 1.0);

    private static final ForgeConfigSpec.DoubleValue COIL_DISCOUNT_MIN_FACTOR =
            BUILDER.comment("降耗封顶：EU/t 倍率不低于该值（0.5 = 最多省一半电）")
                    .defineInRange("coilDiscount.minFactor", 0.5, 0.01, 1.0);

    private static final ForgeConfigSpec.DoubleValue COIL_DISCOUNT_DURATION_MULTIPLIER =
            BUILDER.comment("模块A 全局耗时倍率（配方耗时 × 该值；0.1 = 耗时缩短到 1/10）")
                    .defineInRange("coilDiscount.durationMultiplier", 0.1, 0.01, 1.0);

    private static final ForgeConfigSpec.DoubleValue STRIPPER_ENERGY_MULTIPLIER =
            BUILDER.comment("猪咪黑奴挖矿机的耗能倍数（总耗能 × 该值；Jade 的『总耗能倍率』读的也是它）",
                            "默认 6 = 600%")
                    .defineInRange("stripper.energyMultiplier", 6.0, 0.0, 1024.0);

    private static final ForgeConfigSpec.DoubleValue STRIPPER_DURATION_MULTIPLIER =
            BUILDER.comment("猪咪黑奴挖矿机的耗时倍数（默认 1 = 100%，不额外改耗时）")
                    .defineInRange("stripper.durationMultiplier", 1.0, 0.0, 1024.0);

    private static final ForgeConfigSpec.BooleanValue PATTERN_IDENTITY_GATE =
            BUILDER.comment("猪咪样板总成的「样板身份闸」开关（默认开）",
                            "gtlcore 的样板仓是「库存驱动」：只要仓里某个槽位的物料能凑出某配方的输入，",
                            "该配方就可能绑到这个槽上执行 —— 同一配方类型下存在重复输入时就会串",
                            "（例：A 橡木→白桦木、B 橡木+白桦木→钻石，单独一单 A 也可能被拿去跑 B）。",
                            "开：扣料时校验「候选配方是否属于该槽自己的样板」（类型一致 + 输入覆盖 + 主产物命中，跳过编程电路），",
                            "不匹配的槽直接拒绝参与该配方，把匹配从「库存驱动」纠正成「样板槽身份驱动」。",
                            "关：退回原生行为（只剩配方模式锁挡跨模式串）。",
                            "闸门一律 fail-open：样板解析不出来 / 出任何异常都放行，绝不会让机器卡死。")
                    .define("patternAssembly.identityGate", true);

    private static final ForgeConfigSpec.BooleanValue AUTO_RECIPE_MODE_SWITCH =
            BUILDER.comment("猪咪样板总成的「自动切换配方模式」开关（默认开）",
                            "开：当前模式下机器已经跑不动（IDLE/WAITING，即这一模式的料吃完或卡住）时，",
                            "自动扫描仓里其它「有料在槽位等着」的样板槽，把控制器切到那个模式并重置配方逻辑，",
                            "所以一台机器可以同时挂多种模式的样板、轮流自动跑，省一台机器。",
                            "只在机器「没在跑配方」时切（跑动中打断会让已扣的输入蒸发）；",
                            "切过去 40 tick 还没开跑的类型会被临时拉黑 200 tick，防止两个模式之间来回横跳。",
                            "关：只保留「AE 推样板时按槽位锁模式」的行为（不做主动轮询）。")
                    .define("patternAssembly.autoRecipeModeSwitch", true);

    private static final ForgeConfigSpec.BooleanValue PATTERN_DEBUG_LOGS =
            BUILDER.comment("猪咪样板总成的「调试日志」开关（默认关）",
                            "开：每次 AE 推送样板都打印明细（推了什么、各多少 mB/个）、",
                            "推送后内部槽条目数、以及「需要量 vs 网络可用量」的对比 —— 排查",
                            "「材料明明够却卡住 / 存量不足 / 死锁」时很有用，但会持续刷日志。",
                            "关：这三类啰嗦日志全部不打印（默认）。",
                            "注意：下面两条「只在出问题时才触发」的日志不受这个开关影响，始终保留 ——",
                            "  ①『XX 被合成 CPU 占用』死锁提示；",
                            "  ②『身份闸拒绝配方』误挡提示。")
                    .define("patternAssembly.debugPatternLogs", false);

    public static final NanochipConfig NANOCHIP = new NanochipConfig(BUILDER, "gtlmodular");

    // 保留直接访问电网配置的旧入口，并与共享组件使用同一个 ConfigValue。
    public static final ForgeConfigSpec.BooleanValue NANOCHIP_GRID_POWER = NANOCHIP.gridPowerValue();
    static final List<String> BUILT_IN_CALIBRATION_WHITELIST = NANOCHIP.defaultCalibrationRecipeWhitelist();

    /** 必须在 SPEC 构建前定义，否则该配置项不会纳入配置规格。 */
    private static final ForgeConfigSpec.BooleanValue SPACE_ORE_RECIPE_MIRROR =
            BUILDER.comment("天基矿石处理厂（gtceu:space_ore_processor）配方镜像开关（默认 true = 开）",
                            "Mirror the gtceu:steam_space_ore_processor recipes into gtceu:space_ore_processor (default true)",
                            "true （默认）：开启镜像 —— 服务器启动完成后，把源表全部配方复制一份到天基矿石处理厂配方表",
                            "true  (default): mirror every steam_space_ore_processor recipe into space_ore_processor once the recipe manager is ready",
                            "        源：gtceu:steam_space_ore_processor（实机 999 条）；它一条都没有时回落到 gtceu:integrated_ore_processor",
                            "        目标已有配方 ⇒ 直接跳过（幂等）；每条失败只跳过那一条",
                            "        依据：gtladditions 3.2.8Custom_SubSpace-fix2 分支自带的复制逻辑（onRecipeBuild 回调）实机一次都没触发，该表 0 条，机器转不动",
                            "false：完全不执行（退回现状：该配方表 0 条），仅用于对照实验",
                            "false: do not run the mirror at all (back to the broken state; for A/B testing only)",
                            "改这一项需要重启游戏：只在服务器启动完成（进世界/开服）时读一次",
                            "Requires a game restart: it is read once when the server has started.")
                    .define("spaceOre.recipeMirror", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /** 配置尚不可读时沿用默认开启，避免配方镜像被静默禁用。 */
    public static boolean isSpaceOreRecipeMirrorEnabled() {
        try {
            return SPACE_ORE_RECIPE_MIRROR.get();
        } catch (Throwable ignored) {
            return SPACE_ORE_RECIPE_MIRROR.getDefault();
        }
    }

    public static boolean isVacuumRequiresPipes() {
        return NANOCHIP.isVacuumRequiresPipes();
    }

    public static boolean isNanochipComplexEnabled() {
        return NANOCHIP.isNanochipComplexEnabled();
    }

    public static boolean isNanochipOriginalMode() {
        return NANOCHIP.isNanochipOriginalMode();
    }

    public static int getNanochipDirectParallelCap() {
        return NANOCHIP.getNanochipDirectParallelCap();
    }

    public static int getNanochipDirectCrossRecipeThreads() {
        return NANOCHIP.getNanochipDirectCrossRecipeThreads();
    }

    public static boolean isNanochipCalibrationUseFullWindow() {
        return NANOCHIP.isNanochipCalibrationUseFullWindow();
    }

    public static void setNanochipCalibrationUseFullWindow(boolean value) {
        NANOCHIP.setNanochipCalibrationUseFullWindow(value);
    }

    public static List<String> getNanochipCalibrationRecipeWhitelist() {
        return NANOCHIP.getNanochipCalibrationRecipeWhitelist();
    }

    public static int getNanochipCalibrationRecipeWhitelistCount() {
        return NANOCHIP.getNanochipCalibrationRecipeWhitelistCount();
    }

    public static double getNanochipImpurityPerRecipe() {
        return NANOCHIP.getNanochipImpurityPerRecipe();
    }

    public static boolean isNanochipDebugLogsEnabled() {
        return NANOCHIP.isNanochipDebugLogsEnabled();
    }

    public static boolean isNanochipVerboseStructureLogs() {
        return NANOCHIP.isNanochipVerboseStructureLogs();
    }

    public static void setNanochipVerboseStructureLogs(boolean value) {
        NANOCHIP.setNanochipVerboseStructureLogs(value);
    }

    public static boolean isNanochipSimpleLogistics() {
        return NANOCHIP.isNanochipSimpleLogistics();
    }

    public static boolean isNanochipGridPowerEnabled() {
        return NANOCHIP.isNanochipGridPowerEnabled();
    }

    public static void setNanochipSimpleLogistics(boolean value) {
        NANOCHIP.setNanochipSimpleLogistics(value);
    }

    public static boolean isHideOrphanItemsEnabled() {
        return NANOCHIP.isHideOrphanItemsEnabled();
    }

    public static void setHideOrphanItemsEnabled(boolean value) {
        NANOCHIP.setHideOrphanItemsEnabled(value);
    }

    public static boolean isPatternDebugLogsEnabled() {
        try {
            return PATTERN_DEBUG_LOGS.get();
        } catch (Throwable ignored) {
            return PATTERN_DEBUG_LOGS.getDefault();
        }
    }

    public static boolean isPatternIdentityGateEnabled() {
        try {
            return PATTERN_IDENTITY_GATE.get();
        } catch (Throwable ignored) {
            return PATTERN_IDENTITY_GATE.getDefault();
        }
    }

    public static boolean isAutoRecipeModeSwitchEnabled() {
        try {
            return AUTO_RECIPE_MODE_SWITCH.get();
        } catch (Throwable ignored) {
            return AUTO_RECIPE_MODE_SWITCH.getDefault();
        }
    }

    public static void setPatternIdentityGateEnabled(boolean value) {
        if (PATTERN_IDENTITY_GATE.get() != value) {
            PATTERN_IDENTITY_GATE.set(value);
        }
    }

    public static boolean isDualFluidTaxEnabled() {
        return DUAL_FLUID_TAX_ENABLED.get();
    }

    public static String getLavaFluidId() {
        return LAVA_FLUID_ID.get();
    }

    public static String getBlazeFluidId() {
        return BLAZE_FLUID_ID.get();
    }

    public static int getIvBaselineMbPerSecond() {
        return IV_BASELINE_MB_PER_SECOND.get();
    }

    public static double getPerTierMultiplier() {
        return PER_TIER_MULTIPLIER.get();
    }

    public static int getBaselineTier() {
        return BASELINE_TIER.get();
    }

    public static boolean isAllowBelowBaseline() {
        return ALLOW_BELOW_BASELINE.get();
    }

    public static int getDrainIntervalTicks() {
        return DRAIN_INTERVAL_TICKS.get();
    }

    public static void setEnabled(boolean value) {
        if (DUAL_FLUID_TAX_ENABLED.get() != value) {
            DUAL_FLUID_TAX_ENABLED.set(value);
        }
    }

    public static void setLavaFluidId(String value) {
        if (!LAVA_FLUID_ID.get().equals(value)) {
            LAVA_FLUID_ID.set(value);
        }
    }

    public static void setBlazeFluidId(String value) {
        if (!BLAZE_FLUID_ID.get().equals(value)) {
            BLAZE_FLUID_ID.set(value);
        }
    }

    public static void setIvBaselineMbPerSecond(int value) {
        if (IV_BASELINE_MB_PER_SECOND.get() != value) {
            IV_BASELINE_MB_PER_SECOND.set(value);
        }
    }

    public static void setPerTierMultiplier(double value) {
        if (PER_TIER_MULTIPLIER.get() != value) {
            PER_TIER_MULTIPLIER.set(value);
        }
    }

    public static void setBaselineTier(int value) {
        if (BASELINE_TIER.get() != value) {
            BASELINE_TIER.set(value);
        }
    }

    public static void setAllowBelowBaseline(boolean value) {
        if (ALLOW_BELOW_BASELINE.get() != value) {
            ALLOW_BELOW_BASELINE.set(value);
        }
    }

    public static void setDrainIntervalTicks(int value) {
        if (DRAIN_INTERVAL_TICKS.get() != value) {
            DRAIN_INTERVAL_TICKS.set(value);
        }
    }

    public static boolean isCoilDiscountEnabled() {
        return COIL_DISCOUNT_ENABLED.get();
    }

    public static int getCoilDiscountBaseTemperature() {
        return COIL_DISCOUNT_BASE_TEMPERATURE.get();
    }

    public static int getCoilDiscountTemperaturePerLevel() {
        return COIL_DISCOUNT_TEMPERATURE_PER_LEVEL.get();
    }

    public static double getCoilDiscountFactorPerLevel() {
        return COIL_DISCOUNT_FACTOR_PER_LEVEL.get();
    }

    public static double getCoilDiscountMinFactor() {
        return COIL_DISCOUNT_MIN_FACTOR.get();
    }

    public static double getCoilDiscountDurationMultiplier() {
        return COIL_DISCOUNT_DURATION_MULTIPLIER.get();
    }

    public static double getStripperEnergyMultiplier() {
        return STRIPPER_ENERGY_MULTIPLIER.get();
    }

    public static double getStripperDurationMultiplier() {
        return STRIPPER_DURATION_MULTIPLIER.get();
    }

    public static void setAutoRecipeModeSwitchEnabled(boolean value) {
        if (AUTO_RECIPE_MODE_SWITCH.get() != value) {
            AUTO_RECIPE_MODE_SWITCH.set(value);
        }
    }

    public static void setCoilDiscountEnabled(boolean value) {
        if (COIL_DISCOUNT_ENABLED.get() != value) {
            COIL_DISCOUNT_ENABLED.set(value);
        }
    }

    public static void setCoilDiscountBaseTemperature(int value) {
        if (COIL_DISCOUNT_BASE_TEMPERATURE.get() != value) {
            COIL_DISCOUNT_BASE_TEMPERATURE.set(value);
        }
    }

    public static void setCoilDiscountTemperaturePerLevel(int value) {
        if (COIL_DISCOUNT_TEMPERATURE_PER_LEVEL.get() != value) {
            COIL_DISCOUNT_TEMPERATURE_PER_LEVEL.set(value);
        }
    }

    public static void setCoilDiscountFactorPerLevel(double value) {
        if (COIL_DISCOUNT_FACTOR_PER_LEVEL.get() != value) {
            COIL_DISCOUNT_FACTOR_PER_LEVEL.set(value);
        }
    }

    public static void setCoilDiscountMinFactor(double value) {
        if (COIL_DISCOUNT_MIN_FACTOR.get() != value) {
            COIL_DISCOUNT_MIN_FACTOR.set(value);
        }
    }

    public static void setCoilDiscountDurationMultiplier(double value) {
        if (COIL_DISCOUNT_DURATION_MULTIPLIER.get() != value) {
            COIL_DISCOUNT_DURATION_MULTIPLIER.set(value);
        }
    }

    public static void setStripperEnergyMultiplier(double value) {
        if (STRIPPER_ENERGY_MULTIPLIER.get() != value) {
            STRIPPER_ENERGY_MULTIPLIER.set(value);
        }
    }

    public static void setStripperDurationMultiplier(double value) {
        if (STRIPPER_DURATION_MULTIPLIER.get() != value) {
            STRIPPER_DURATION_MULTIPLIER.set(value);
        }
    }
}
