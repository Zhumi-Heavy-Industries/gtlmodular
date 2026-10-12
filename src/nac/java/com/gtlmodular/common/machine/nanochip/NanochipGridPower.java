package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class NanochipGridPower {

    private final NanochipAssemblyComplexMachine machine;
    private final Supplier<UUID> readUser;
    private final Consumer<UUID> writeUser;
    private final Runnable refreshEnergySnapshot;

    private long lastGridUserLogTick = Long.MIN_VALUE;

    private transient boolean gridUserChecked;

    @Nullable
    private static final java.lang.reflect.Field ENERGY_CONTAINER_LIST_FIELD = resolveEnergyListField();

    private static final long CACHE_TICKS = 20L;

    private long cachedEu;

    private long cachedAtTick = Long.MIN_VALUE;

    NanochipGridPower(NanochipAssemblyComplexMachine machine, Supplier<UUID> readUser,
                      Consumer<UUID> writeUser, Runnable refreshEnergySnapshot) {
        this.machine = machine;
        this.readUser = readUser;
        this.writeUser = writeUser;
        this.refreshEnergySnapshot = refreshEnergySnapshot;
    }

    boolean hasGridPowerIdentity() {
        return readUser.get() != null && NanochipModContext.config().isNanochipGridPowerEnabled();
    }

    void onMachinePlaced(@Nullable net.minecraft.world.entity.LivingEntity player, ItemStack stack) {
        if (player instanceof net.minecraft.world.entity.player.Player p) {
            writeUser.accept(p.getUUID());
            this.gridUserChecked = true;
            NanochipModContext.logger().info("[gtlmodular] NAC @{} 电网取电身份已登记：{}（玩家 {}）",
                    machine.getPos(), readUser.get(), p.getGameProfile().getName());
            refreshEnergySnapshot.run();   // 结构检查可能先于放置回调完成，那时还没有账户。
        }
    }

    /** 不生成随机账户：无人能为它充值。无有效身份时等待玩家主动绑定自己的账户。 */
    @Nullable
    private UUID resolveGridUser() {
        if (readUser.get() != null && !gridUserChecked && machine.getLevel() instanceof ServerLevel serverLevel) {
            gridUserChecked = true;
            if (isOrphanGridAccount(serverLevel.getServer(), readUser.get())) {
                NanochipModContext.logger().warn("[gtlmodular] NAC @{} 电网账户 {} 不属于任何玩家且余额为 0"
                                + "（旧版本自动生成的随机账户）⇒ 已解除绑定；空手潜行右键控制器即可绑定到自己",
                        machine.getPos(), readUser.get());
                writeUser.accept(null);
                machine.markDirty();
                refreshEnergySnapshot.run();
            }
        }
        return readUser.get();
    }

    /** 仅清理从未被服务器识别且余额为零的账户；信息不足时保留绑定，避免误删玩家身份。 */
    private static boolean isOrphanGridAccount(MinecraftServer server, UUID uuid) {
        if (server.getPlayerList().getPlayer(uuid) != null) {
            return false;
        }
        GameProfileCache profiles = server.getProfileCache();
        if (profiles == null || profiles.get(uuid).isPresent()) {
            return false;
        }
        try {
            BigInteger balance = WirelessEnergyManager.getUserEU(uuid);
            return balance == null || balance.signum() <= 0;
        } catch (Throwable t) {
            return false;
        }
    }

    InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player,
                            InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                || !player.getItemInHand(hand).isEmpty()
                || !NanochipModContext.config().isNanochipGridPowerEnabled()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            UUID uuid = player.getUUID();
            if (!uuid.equals(readUser.get())) {
                writeUser.accept(uuid);
                gridUserChecked = true;
                machine.markDirty();
                refreshEnergySnapshot.run();
                NanochipModContext.logger().info("[gtlmodular] NAC @{} 电网账户绑定到 {}（{}）",
                        machine.getPos(), player.getGameProfile().getName(), uuid);
            }
            player.displayClientMessage(Component.translatable(NanochipAssemblyComplexMachine.LANG_GRID_BOUND,
                    FormattingUtil.formatNumbers(gridBalanceEU(uuid))), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static BigInteger gridBalanceEU(UUID uuid) {
        try {
            BigInteger balance = WirelessEnergyManager.getUserEU(uuid);
            return balance == null ? BigInteger.ZERO : balance;
        } catch (Throwable t) {
            return BigInteger.ZERO;
        }
    }

    void addGridAccountLine(List<Component> textList) {
        if (!NanochipModContext.config().isNanochipGridPowerEnabled()) {
            return;
        }
        UUID user = resolveGridUser();
        if (user == null) {
            textList.add(Component.translatable(NanochipAssemblyComplexMachine.LANG_GRID_UNBOUND).withStyle(ChatFormatting.YELLOW));
            return;
        }
        String name = user.toString().substring(0, 8);
        if (machine.getLevel() instanceof ServerLevel serverLevel) {
            GameProfileCache profiles = serverLevel.getServer().getProfileCache();
            if (profiles != null) {
                name = profiles.get(user).map(com.mojang.authlib.GameProfile::getName).orElse(name);
            }
        }
        textList.add(Component.translatable(NanochipAssemblyComplexMachine.LANG_GRID_ACCOUNT,
                Component.literal(name).withStyle(ChatFormatting.WHITE),
                Component.literal(FormattingUtil.formatNumbers(gridBalanceEU(user)) + " EU")
                        .withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
    }

    /** 本入口每 tick 会被多次调用，缓存反射句柄以免重复查找私有字段。 */
    static List<com.gregtechceu.gtceu.api.capability.IEnergyContainer> innerContainers(
            EnergyContainerList list) {
        try {
            java.lang.reflect.Field f = ENERGY_CONTAINER_LIST_FIELD;
            if (f == null) {
                return List.of();
            }
            Object raw = f.get(list);
            if (raw instanceof List<?> l) {
                List<com.gregtechceu.gtceu.api.capability.IEnergyContainer> out = new ArrayList<>(l.size());
                for (Object o : l) {
                    if (o instanceof com.gregtechceu.gtceu.api.capability.IEnergyContainer c) {
                        out.add(c);
                    }
                }
                return out;
            }
        } catch (Throwable ignored) {
        }
        return List.of();
    }

    @Nullable
    private static java.lang.reflect.Field resolveEnergyListField() {
        try {
            java.lang.reflect.Field f = EnergyContainerList.class.getDeclaredField("energyContainerList");
            f.setAccessible(true);
            return f;
        } catch (Throwable ignored) {
            return null;
        }
    }

    void warnGridDrawFailure(String site) {
        Level lv = machine.getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        long now = lv.getGameTime();
        if (lastGridUserLogTick != Long.MIN_VALUE
                && now - lastGridUserLogTick < NanochipAssemblyComplexMachine.GRID_USER_LOG_INTERVAL) {
            return;
        }
        lastGridUserLogTick = now;
        NanochipModContext.logger().warn("[gtlmodular] NAC @{} 电网取电不可用（{}）：本机只能用能源仓供电，"
                        + "没有能源仓的机器会原地等电（不退进度、不掉料）",
                machine.getPos(), site);
    }

    /** 配方 EU 从能力代理中的处理器结算；只在 getEnergyContainer 中包装容器无法接入实际扣电。 */
    void registerGridPowerHandler(Supplier<? extends IRecipeHandler<Long>> gridContainer) {
        if (!NanochipModContext.config().isNanochipGridPowerEnabled()) {
            return;
        }
        IRecipeHandler<Long> grid = gridContainer.get();
        try {
            var proxy = machine.getCapabilitiesProxy();
            if (proxy == null) {
                return;
            }
            var cap = com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP;
            var io = com.gregtechceu.gtceu.api.capability.recipe.IO.IN;
            List<com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler<?>> existing = proxy.get(io, cap);
            List<com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler<?>> writable;
            if (existing == null) {
                writable = new ArrayList<>();
            } else if (existing instanceof ArrayList) {
                writable = existing;
            } else {
                writable = new ArrayList<>(existing);
            }
            if (writable.contains(grid)) {
                return;
            }
            writable.add(grid);
            proxy.put(io, cap, writable);
            NanochipModContext.logger().info("[gtlmodular] NAC @{} 电网取电已接入能力代理：EURecipeCapability 输入处理器 "
                    + "{} 个（末位=电网容器，身份 {}）——这一步让配方 EU 结算真正吃得到电网",
                    machine.getPos(), writable.size(), readUser.get());
        } catch (Throwable t) {
            machine.warnGridDrawFailure("电网处理器注册失败：" + t.getClass().getSimpleName());
        }
    }

    void unregisterGridPowerHandler(Supplier<? extends IRecipeHandler<Long>> gridContainer) {
        try {
            var proxy = machine.getCapabilitiesProxy();
            if (proxy == null) {
                return;
            }
            var cap = com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP;
            var io = com.gregtechceu.gtceu.api.capability.recipe.IO.IN;
            List<com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler<?>> existing = proxy.get(io, cap);
            IRecipeHandler<Long> grid = gridContainer.get();
            if (existing == null || !existing.contains(grid)) {
                return;
            }
            List<com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler<?>> writable =
                    existing instanceof ArrayList ? existing : new ArrayList<>(existing);
            writable.remove(grid);
            if (writable.isEmpty()) {
                proxy.remove(io, cap);
            } else {
                proxy.put(io, cap, writable);
            }
        } catch (Throwable ignored) {
        }
    }

    EnergyContainerList getEnergyContainer(EnergyContainerList own,
                                          Supplier<? extends IEnergyContainer> gridContainer) {
        if (!NanochipModContext.config().isNanochipGridPowerEnabled()) {
            return own;
        }
        List<com.gregtechceu.gtceu.api.capability.IEnergyContainer> list = new ArrayList<>();
        // EnergyContainerList#changeEnergy 按列表顺序扣电，电网必须排在能源仓之前。
        list.add(gridContainer.get());
        if (own != null) {
            // 复用真实容器以保持同一份存量；反射失败时只能退化为电网单路供电。
            for (com.gregtechceu.gtceu.api.capability.IEnergyContainer c : innerContainers(own)) {
                // 基类列表也可能从能力代理带回电网容器，必须去重，避免同一来源被计算两次。
                if (c != null && c != gridContainer.get()) {
                    list.add(c);
                }
            }
        }
        return new EnergyContainerList(list);
    }

    @Nullable
    private UUID user() {
        return NanochipModContext.config().isNanochipGridPowerEnabled() ? resolveGridUser() : null;
    }

    /** 电网余额可能超过 long，窄化前必须截断，避免回绕成负数。 */
    private long wireEu() {
        Level lv = machine.getLevel();
        long now = lv == null ? Long.MIN_VALUE : lv.getGameTime();
        if (now == Long.MIN_VALUE || cachedAtTick == Long.MIN_VALUE || now - cachedAtTick >= CACHE_TICKS) {
            cachedAtTick = now;
            UUID u = user();
            if (u == null) {
                cachedEu = 0L;
            } else {
                BigInteger bal = null;
                try {
                    bal = WirelessEnergyManager.getUserEU(u);
                } catch (Throwable t) {
                    cachedEu = 0L;
                    bal = null;
                }
                cachedEu = (bal == null || bal.signum() <= 0) ? 0L
                        : (bal.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                                ? Long.MAX_VALUE
                                : bal.longValue());
            }
        }
        return cachedEu;
    }

    long getEnergyStored() {
        return user() == null ? 0L : wireEu();
    }

    long getEnergyCapacity() {
        return user() != null ? NanochipAssemblyComplexMachine.ON_DEMAND_POWER_FLOOR_EU : 0L;
    }

    long getInputVoltage() {
        return user() != null ? Long.MAX_VALUE : 0L;
    }

    long changeEnergy(long amount) {
        if (amount >= 0L) {
            return 0L;
        }
        if (!NanochipModContext.config().isNanochipGridPowerEnabled()) {
            return 0L;
        }
        UUID u = user();
        if (u == null) {
            return 0L;
        }
        long need = amount == Long.MIN_VALUE ? Long.MAX_VALUE : -amount;
        if (need <= 0L) {
            return 0L;
        }
        long paid = Math.min(need, wireEu());
        if (paid <= 0L) {
            return 0L;
        }
        if (!WirelessEnergyManager.addEUToGlobalEnergyMap(u, -paid, machine)) {
            return 0L;
        }
        cachedAtTick = Long.MIN_VALUE;   // 付完重读真实余额：余额夹到 Long.MAX_VALUE 后再减会误报为零，挡住其他模块。
        return -paid;
    }

    List<Object> getContents() {
        return java.util.Collections.singletonList((Object) (user() == null ? 0L : wireEu()));
    }

    double getTotalContentAmount() {
        return user() == null ? 0.0 : (double) wireEu();
    }

    List<Long> handleRecipeInner(com.gregtechceu.gtceu.api.capability.recipe.IO io,
            GTRecipe recipe, List<Long> left, @Nullable String slot, boolean simulate) {
        if (io != com.gregtechceu.gtceu.api.capability.recipe.IO.IN || left == null || left.isEmpty()) {
            return left;
        }
        long need = 0L;
        for (Long v : left) {
            if (v != null && v > 0L) {
                need = need > Long.MAX_VALUE - v ? Long.MAX_VALUE : need + v;
            }
        }
        if (need <= 0L) {
            return null;
        }
        long paid;
        if (simulate) {
            paid = user() == null ? 0L : Math.min(need, wireEu());
        } else {
            long paidSigned = changeEnergy(-need);
            paid = paidSigned >= 0L ? 0L : -paidSigned;
        }
        long remain = need - paid;
        return remain <= 0L ? null : List.of(remain);
    }

    void logLoadedAccount() {
        if (NanochipModContext.config().isNanochipGridPowerEnabled()) {
            UUID gridUser = resolveGridUser();
            if (gridUser != null) {
                NanochipModContext.logger().info("[gtlmodular] NAC @{} 电网账户 {}", machine.getPos(), gridUser);
            } else {
                NanochipModContext.logger().info("[gtlmodular] NAC @{} 电网账户未绑定 ⇒ 只用能源仓供电；"
                        + "空手潜行右键控制器即可绑定到自己的无线电网", machine.getPos());
            }
        }
    }
}
