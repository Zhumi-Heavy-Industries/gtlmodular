package com.gtlmodular.common.machine.nanochip.structure;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.SinglePredicateError;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipModuleMachine;
import com.gtlmodular.common.machine.nanochip.NanochipStructureLogGate;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class NanochipStructureDiagnostics {

    private final NanochipAssemblyComplexMachine host;
    private final NanochipStructureLogGate structureLogGate;
    private final Supplier<String> moduleSlots;
    private final IntSupplier moduleCount;

    private static final long UNFORMED_AUDIT_INTERVAL_TICKS = 300L;

    /** 时间差参与限流，哨兵不能用会使减法溢出的 Long.MIN_VALUE。 */
    private long lastUnformedAuditTick = -10_000L;

    private static final String UNFORMED_CONCLUSION = "复合体尚未成形";

    public NanochipStructureDiagnostics(NanochipAssemblyComplexMachine host,
            NanochipStructureLogGate structureLogGate, Supplier<String> moduleSlots, IntSupplier moduleCount) {
        this.host = host;
        this.structureLogGate = structureLogGate;
        this.moduleSlots = moduleSlots;
        this.moduleCount = moduleCount;
    }

    public void resetUnformedAudit() {
        this.lastUnformedAuditTick = -10_000L;
    }

    /** 首个失败格不足以解释多处缺件，因此另做限流的完整身份格审计。 */
    public void logUnformedAudit() {
        Level lv = host.getLevel();
        if (lv == null || lv.isClientSide || host.isFormed()) {
            return;
        }
        long now = lv.getGameTime();
        if (now - this.lastUnformedAuditTick < UNFORMED_AUDIT_INTERVAL_TICKS) {
            return;
        }
        this.lastUnformedAuditTick = now;
        List<String> audit = NanochipComplexPattern.auditStructure(lv, host.getPos(), 12);
        // 数量限制没有唯一失败格，必须同时显示 F 位的实际内容。
        String fSlots = host.summarizeFSlotContent();
        // 同一份缺件清单可能长期重复，按内容折叠日志以降低写盘开销。
        String auditDetail = fSlots + "\n" + String.join("\n", audit);
        if (!this.structureLogGate.shouldEmitInfo(UNFORMED_CONCLUSION, auditDetail,
                "复合体@" + host.getPos().toShortString(), now)) {
            if (NanochipModContext.logger().isDebugEnabled()) {
                logUnformedAuditBody(audit, fSlots, true);
            }
            return;
        }
        logUnformedAuditBody(audit, fSlots, false);
    }

    /** 两个日志级别共用同一份诊断内容，避免限流分支遗漏判据。 */
    private void logUnformedAuditBody(List<String> audit, String fSlots, boolean debugOnly) {
        if (audit.isEmpty()) {
            if (debugOnly) {
                NanochipModContext.logger().debug("[gtlmodular] NAC 尚未成形 @{}：唯一方块格（网格机壳/强化机壳/复合体玻璃/"
                                + "计算矩阵机壳/防火墙投影）**全部就位** ⇒ 缺口在「多解格」（H 仓位 / I 可忽略位 / "
                                + "F 模块位 / 空格）或数量类限制上；{}；GTCEu 逐格原因：{}",
                        host.getPos(), fSlots, String.valueOf(describeStructureFailure(host.getMultiblockState())));
            } else {
                NanochipModContext.logger().info("[gtlmodular] NAC 尚未成形 @{}：唯一方块格（网格机壳/强化机壳/复合体玻璃/"
                                + "计算矩阵机壳/防火墙投影）**全部就位** ⇒ 缺口在「多解格」（H 仓位 / I 可忽略位 / "
                                + "F 模块位 / 空格）或数量类限制上；{}；GTCEu 逐格原因：{}",
                        host.getPos(), fSlots, String.valueOf(describeStructureFailure(host.getMultiblockState())));
            }
        } else {
            if (debugOnly) {
                NanochipModContext.logger().debug("[gtlmodular] NAC 缺件清单 @{}（{} 条；{}）：", host.getPos(), audit.size(), fSlots);
                for (String line : audit) {
                    NanochipModContext.logger().debug("[gtlmodular]   · {}", line);
                }
            } else {
                NanochipModContext.logger().info("[gtlmodular] NAC 缺件清单 @{}（{} 条；{}）：", host.getPos(), audit.size(), fSlots);
                for (String line : audit) {
                    NanochipModContext.logger().info("[gtlmodular]   · {}", line);
                }
            }
        }
        logFailedPredicate(debugOnly);
    }

    private void logFailedPredicate() {
        logFailedPredicate(false);
    }

    /** 失败判据必须与缺件清单一起降级，否则仍会重复写入 INFO 日志。 */
    private void logFailedPredicate(boolean debugOnly) {
        try {
            MultiblockState state = host.getMultiblockState();
            if (state == null) {
                return;
            }
            PatternError error = state.error;
            if (error == null || error == MultiblockState.UNLOAD_ERROR || error == MultiblockState.UNINIT_ERROR) {
                return;
            }
            Map<SimplePredicate, Integer> counts = state.getGlobalCount();
            if (error instanceof SinglePredicateError single) {
                SimplePredicate p = single.predicate;
                if (debugOnly) {
                    NanochipModContext.logger().debug("[gtlmodular] NAC 失败判据 @{}：type={}（0..3 = 数量类）；"
                                    + "该判据计数={} / 上限 max={} min={} maxLayer={} minLayer={}；候选方块={}",
                            host.getPos(), single.type, countOf(counts, p),
                            p.maxCount, p.minCount, p.maxLayerCount, p.minLayerCount, candidateNames(p));
                } else {
                    NanochipModContext.logger().info("[gtlmodular] NAC 失败判据 @{}：type={}（0..3 = 数量类）；"
                                    + "该判据计数={} / 上限 max={} min={} maxLayer={} minLayer={}；候选方块={}",
                            host.getPos(), single.type, countOf(counts, p),
                            p.maxCount, p.minCount, p.maxLayerCount, p.minLayerCount, candidateNames(p));
                }
            } else if (debugOnly) {
                NanochipModContext.logger().debug("[gtlmodular] NAC 失败判据 @{}：非单判据错误（{}）", host.getPos(), error.getClass().getSimpleName());
            } else {
                NanochipModContext.logger().info("[gtlmodular] NAC 失败判据 @{}：非单判据错误（{}）", host.getPos(), error.getClass().getSimpleName());
            }
            if (counts != null && !counts.isEmpty()) {
                if (debugOnly) {
                    NanochipModContext.logger().debug("[gtlmodular] NAC 全局计数表 @{}（{} 条）：", host.getPos(), counts.size());
                } else {
                    NanochipModContext.logger().info("[gtlmodular] NAC 全局计数表 @{}（{} 条）：", host.getPos(), counts.size());
                }
                for (Map.Entry<SimplePredicate, Integer> e : counts.entrySet()) {
                    SimplePredicate p = e.getKey();
                    if (debugOnly) {
                        NanochipModContext.logger().debug("[gtlmodular]   · 候选={} 计数={} max={} min={}",
                                candidateNames(p), e.getValue(), p.maxCount, p.minCount);
                    } else {
                        NanochipModContext.logger().info("[gtlmodular]   · 候选={} 计数={} max={} min={}",
                                candidateNames(p), e.getValue(), p.maxCount, p.minCount);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // 诊断失败不能打断结构检查。
        }
    }

    private static int countOf(Map<SimplePredicate, Integer> counts, SimplePredicate p) {
        if (counts == null) {
            return -1;
        }
        Integer v = counts.get(p);
        return v == null ? -1 : v;
    }

    private static String candidateNames(SimplePredicate p) {
        try {
            BlockInfo[] infos = p.candidates.get();
            if (infos == null || infos.length == 0) {
                return "候选表为空";
            }
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < Math.min(6, infos.length); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(BuiltInRegistries.BLOCK.getKey(infos[i].getBlockState().getBlock()));
            }
            if (infos.length > 6) {
                sb.append(", …共 ").append(infos.length).append(" 个");
            }
            return sb.append(']').toString();
        } catch (RuntimeException e) {
            return "候选表读不出来：" + e.getClass().getSimpleName();
        }
    }

    public void logStructureFailureDetail() {
        String detail = describeStructureFailure(host.getMultiblockState());
        if (detail != null) {
            NanochipModContext.logger().info("[gtlmodular] NAC 结构失效定位 @{}：{}", host.getPos(), detail);
            return;
        }
        NanochipModContext.logger().info("[gtlmodular] NAC 结构失效摘要 @{}：GTCEu 本次没有留下逐格原因"
                        + "（MultiblockState.error == null）—— 原因：onStructureInvalid() 不带参数，逐格原因只能从"
                        + " MultiblockState.error 读，而 GTCEu 只在「数量类 / 被卸载」失败路径上写它，"
                        + "单格不符常常直接 return false；⇒ 退化摘要：当前已连接模块 {} 个；{}",
                host.getPos(), moduleCount.getAsInt(), moduleSlots.get());
    }

    public static String describeStructureFailure(MultiblockState state) {
        if (state == null) {
            return null;
        }
        PatternError error = state.error;
        if (error == null) {
            return null;
        }
        if (error == MultiblockState.UNLOAD_ERROR) {
            return "GTCEu 记录的原因是「方块/零件被卸载」MultiblockState.UNLOAD_ERROR"
                    + "—— 不是真的结构错，等区块/零件重新加载后会自动复查";
        }
        if (error == MultiblockState.UNINIT_ERROR) {
            return "GTCEu 记录的原因是「结构状态尚未初始化」MultiblockState.UNINIT_ERROR —— 不是真的结构错";
        }
        try {
            BlockPos bad = error.getPos();
            Component expected = error.getErrorInfo();
            int type = -1;
            String what;
            if (error instanceof SinglePredicateError single) {
                type = single.type;
                what = switch (single.type) {
                    case 0 -> "该方块/仓位的【全局数量超过上限】；例：能源仓最多 2 个";
                    case 1 -> "该方块/仓位的【全局数量不足】；例：至少 1 个能源仓";
                    case 2 -> "该方块/仓位的【每层数量超过上限】";
                    case 3 -> "该方块/仓位的【每层数量不足】";
                    default -> "该格判定不符：单格没通过";
                };
            } else {
                what = "该格判定不符：通用结构错误";
            }
            boolean countType = type >= 0 && type <= 3;
            return String.format(Locale.ROOT,
                    "%s；GTCEu 记录的位置 %s%s；该格实际=%s；GTCEu 期望文案=「%s」；允许放的方块/物品=%s%s",
                    what,
                    bad == null ? "未给出" : bad.toShortString(),
                    countType ? "数量类错误：这只是「最后被检查的那一格」，不是「某一格放错了」" : "",
                    bad == null ? "未知" : describeSlotContent(state.getWorld(), bad),
                    expected == null ? "无" : expected.getString(),
                    describeCandidates(error),
                    type < 0 ? "" : "；GTCEu 错误类型=" + error.getClass().getSimpleName() + "(type=" + type + ")");
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String describeSlotContent(Level level, BlockPos pos) {
        MetaMachine machine = level == null ? null : MetaMachine.getMachine(level, pos);
        if (machine instanceof NanochipModuleMachine module) {
            return module.getModuleSpec().nameZh() + (module.isFormed() ? " · 已成形" : " · 未成形");
        }
        ResourceLocation id = level == null ? null : BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        String base = id == null ? "取不到方块 id" : id.toString();
        return machine == null ? base : base + " · " + machine.getClass().getSimpleName();
    }

    private static String describeCandidates(PatternError error) {
        List<List<ItemStack>> candidates = error.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return "GTCEu 未给出候选表";
        }
        StringBuilder sb = new StringBuilder();
        int groups = 0;
        for (List<ItemStack> group : candidates) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            if (groups >= 6) {
                sb.append(" / …");
                break;
            }
            if (groups > 0) {
                sb.append(" / ");
            }
            ItemStack first = group.get(0);
            ResourceLocation id = first.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(first.getItem());
            sb.append(id == null ? "空" : id.toString());
            if (group.size() > 1) {
                sb.append("(+" ).append(group.size() - 1).append(')');
            }
            groups++;
        }
        return sb.length() == 0 ? "GTCEu 未给出候选表" : sb.toString();
    }
}
