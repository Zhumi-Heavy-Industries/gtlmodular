package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.data.nanochip.NanochipModContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** 记录真实调度经过的分支，诊断本身不能重跑修改器、匹配器或扣料。 */
final class NanochipStallDiagnostics {
    private static final int SAMPLE_INTERVAL = 20;
    private static final int CANDIDATE_LIMIT = 256;
    private static final int TEXT_LIMIT = 24000;
    private final NanochipThreadedRecipeLogic logic;
    private final NanochipAssemblyComplexMachine host;
    private final NanochipStallWatch watch = new NanochipStallWatch();
    private final Map<String, String> outcomes = new LinkedHashMap<>();
    private final Map<String, String> runningWaits = new LinkedHashMap<>();
    private TickableSubscription subscription;
    private long sampleTick = -1;
    private long logicTick = -1;
    private long searchTick = -1;
    private long opened;
    private int raw;
    private int usable;
    private int selected;
    private int omitted;
    private String searchFailure;
    private String scheduling = "尚未执行配方 tick";

    NanochipStallDiagnostics(NanochipThreadedRecipeLogic logic, NanochipAssemblyComplexMachine host) {
        this.logic = logic;
        this.host = host;
    }

    void load() {
        unload();
        if (host.getLevel() != null && !host.getLevel().isClientSide) {
            subscription = host.subscribeServerTick(this::tick);
        }
    }

    void unload() {
        if (subscription != null) subscription.unsubscribe();
        subscription = null;
    }

    void logicTick() {
        logicTick = time();
        runningWaits.clear();
        scheduling = "未进入候选搜索（线程已满或机器级 onWorking 否决）";
    }

    void scheduling(String reason) {
        scheduling = reason;
    }

    void beginSearch() {
        searchTick = time();
        outcomes.clear();
        omitted = 0;
        raw = usable = selected = 0;
        searchFailure = null;
        scheduling = "已执行候选搜索";
    }

    void searched(int raw, int usable, int selected, String failure) {
        this.raw = raw;
        this.usable = usable;
        this.selected = selected;
        this.searchFailure = failure;
    }

    void candidate(GTRecipe recipe, String reason) {
        String id = id(recipe);
        if (outcomes.size() >= CANDIDATE_LIMIT && !outcomes.containsKey(id)) {
            omitted++;
            return;
        }
        outcomes.put(id, reason);
    }

    void runningWait(GTRecipe recipe, String reason) {
        if (runningWaits.size() < CANDIDATE_LIMIT) runningWaits.put(id(recipe), reason);
    }

    void opened() {
        opened++;
    }

    private long time() {
        return host.getLevel() == null ? -1 : host.getLevel().getGameTime();
    }

    private void tick() {
        long now = time();
        if (now < 0 || (sampleTick >= 0 && now >= sampleTick && now - sampleTick < SAMPLE_INTERVAL)) return;
        sampleTick = now;
        try {
            var stock = NanochipStallInputs.capture(host);
            if (watch.observe(now, stock.materials(), stock.present(), stock.complete(), opened)) {
                logic.diagnosticsInputChanged();
            }
            if (!watch.reportDue(now)) return;
            NanochipModContext.logger().info("[gtlmodular] NAC 主机停工诊断 @{} {} tick={}：观察到输入后 {} tick 未开出新批次；"
                            + "等待[{}；最近运行等待={}]；调度[最近配方tick={}，{}]；候选[最近真实搜索tick={}，原始={}，资格通过={}，轮转选中={}，"
                            + "搜索异常={}，逐候选结果={}，省略记录次数={}]；槽缓存[{}]；输入数量[{}；完整={}，"
                            + "合计最多256个槽侧/普通库存项、每个ME槽侧64项；分面读数不可跨槽合批，tag值仅区分物料]；主机/模块[{}]",
                    host.getLevel().dimension().location(), host.getPos(), now, watch.waitingTicks(now),
                    read(this::waiting), clip(runningWaits.toString()), logicTick, scheduling, searchTick, raw, usable, selected,
                    searchFailure, clip(outcomes.toString()), omitted, clip(stock.caches()), clip(stock.materials()),
                    stock.complete(), read(this::connections));
        } catch (RuntimeException | LinkageError e) {
            // 诊断异常不能取消机器工作；沿用同一限频器，避免坏输入面刷屏。
            if (watch.reportDue(now)) NanochipModContext.logger().info(
                    "[gtlmodular] NAC 主机停工诊断 @{}：采样失败 {}，配方tick={}，搜索tick={}",
                    host.getPos(), e.toString(), logicTick, searchTick);
        }
    }

    private String waiting() {
        String primary = logic.isSuspend() ? "工作已暂停"
                : !host.isFormed() ? "主机未成形"
                : !host.isRecipeLogicAvailable() ? "配方逻辑不可用/零件未就绪"
                : !logic.recipeTickSubscribed() ? "配方 tick 未订阅"
                : logic.runningThreadCount() >= logic.getThreadLimit() ? "线程已满，等待已有批次"
                : searchTick < 0 ? "尚未执行候选搜索"
                : raw == 0 ? "最近搜索未枚举到候选（检查输入与槽缓存）"
                : usable == 0 ? "最近候选全部被资格闸拒绝"
                : "候选未开批，见逐候选准入/扣料结果";
        return primary + "；状态=" + logic.getStatus() + "；在跑=" + logic.runningThreadCount() + "/" + logic.getThreadLimit()
                + "；上游等待=" + logic.getFancyTooltip() + "；锁配方=" + logic.isLock() + "/" + id(logic.getLockRecipe())
                + "（锁状态仅记录，不推定独立线程因此拒绝）"
                + "；实际余额EU=" + host.getTotalStoredEU() + "；最大输入EU/t=" + host.getMaxInputPower()
                + "；按需电源=" + host.isEnergySourceEffectivelyUnlimited();
    }

    private String connections() {
        StringBuilder text = new StringBuilder("成形=" + host.isFormed() + "；零件=" + host.getParts().size());
        int count = 0;
        for (var module : host.getModules()) {
            if (++count > 64) {
                text.append("；模块明细截断");
                break;
            }
            if (module instanceof NanochipModuleMachine nac) {
                text.append("；").append(nac.getModuleSpec().id()).append('@').append(nac.getPos())
                        .append("{成形=").append(nac.isFormed()).append(",双向连接=").append(nac.hostConnected())
                        .append(",指向本机=").append(nac.getHost() == host).append('}');
            } else text.append("；外部模块=").append(module.getClass().getName());
        }
        return text + "；仅列主机当前连接表，不触发扫描/重连";
    }

    private static String read(Supplier<String> reader) {
        try {
            return reader.get();
        } catch (RuntimeException | LinkageError e) {
            return "读取失败=" + e.getClass().getSimpleName();
        }
    }

    private static String clip(String text) {
        return text.length() <= TEXT_LIMIT ? text : text.substring(0, TEXT_LIMIT) + "…明细截断";
    }

    private static String id(GTRecipe recipe) {
        return recipe == null ? "无" : String.valueOf(recipe.getId());
    }
}
