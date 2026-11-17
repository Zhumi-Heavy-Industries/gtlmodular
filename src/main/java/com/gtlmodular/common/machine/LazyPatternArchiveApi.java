package com.gtlmodular.common.machine;

import com.gtlmodular.GTLModular;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * <b>P2 编排层：把「归档」这套动作暴露给物品与界面</b>
 * （{@code com.gtlmodular.common.item} 与界面页跨包调用它）。
 *
 * <p>为什么要有这一层：
 * <ul>
 *   <li>{@link LazyPatternAssemblyPartMachine} 里的归档入口是<b>包内可见</b>（{@code common.machine}）
 *       —— 不让"机器内部那几张按槽下标对齐的表"漏到别的包去乱改；</li>
 *   <li>物品与界面页需要一套<b>与机器类型无关</b>的门面：{@link #supports(Object)}（按<b>基类</b>判定）
 *       + 复制 / 剪切 / 粘贴 / 清空四个动作，每个动作都返回一个能直接丢给玩家的结果。</li>
 * </ul>
 *
 * <h3>判定口径（★ 本条就是"为什么不沿用 gtlcore 工具"的技术答案）</h3>
 * {@link #supports(Object)} 认 <b>gtlcore 的抽象基类</b> {@code MEPatternBufferPartMachineBase}
 * —— 惰性机（{@link LazyPatternAssemblyPartMachine}）是它的子类 ⇒ 我们自己的机器天然在内。
 * 而 gtlcore 的 {@code MEPatternBufferCopyBehavior/CutBehavior} 认的是<b>具体类</b>
 * {@code MEPatternBufferPartMachine}（字节码 {@code instanceof} + {@code checkcast}，
 * 见 {@code _javap_p2_api.txt}【1】）⇒ 它们对我们失效，这就是"必须自己做工具"的原因。
 *
 * <p>但 {@link #supports(Object)} 只决定"这个动作要不要接"；真正的读写落在
 * {@link #toTarget(Object)} 分发出来的 {@link PatternArchiveTarget} 上
 * （★ <b>片 C</b>：以前只认 {@link LazyPatternAssemblyPartMachine}（{@code toLazy()}）⇒
 * 拿工具右键<b>现役机</b>会拿到 {@code null}、报 {@code archive.result.unsupported}
 * —— 队长实机复现过的那一句"这台机器不是「惰性猪咪样板总成」…"就是这条路。
 * 现在惰性机与现役机各有一个适配器（{@link LazyPatternArchiveTarget} /
 * {@link MoltenPatternArchiveTarget}）⇒ <b>两种机器都认</b>，共用同一套引擎与同一套承诺）。
 *
 * <p><b>两套工具并存、格式不互通</b>（现状，片 C 有意不改）：
 * 现役机仍然能被 gtlcore 自带的 {@code MEPatternBufferCopyBehavior/CutBehavior} 认到
 * （那按<b>具体类</b>判定，我们的现役机正好是它的子类），gtlcore 的档案写在 {@code tag}/{@code cut} 键里、
 * 用固定 128/640 坐标系；我们的档案写在 {@code gtlmodular$archive} 里、带逐槽元数据
 * （配方类型缓存 / 手动指定 / 逐槽催化剂 + 阈值）。我们的<b>读侧只读兼容</b> gtlcore 的两个键
 * （见 {@link LazyPatternArchiveCodec} 的 {@code LEGACY_*}），<b>反向不行</b>；
 * 且 gtlcore 那套<b>完全不搬催化剂</b>。⇒ 迁移要保留催化剂与手动指定就必须用我们的工具。
 */
public final class LazyPatternArchiveApi {

    /** 物品 NBT 上档案容器的键名（tooltip 的调试行会显示它）。 */
    public static final String MOD_ARCHIVE_KEY = LazyPatternArchiveCodec.NBT_STAMP;

    /** 档案格式版本（与编解码器同源）。 */
    public static final int ARCHIVE_VERSION = LazyPatternArchiveCodec.VERSION;

    private LazyPatternArchiveApi() {
    }

    // ==================================================================================
    // 门面：结果类型
    // ==================================================================================

    /** 一次归档动作的结果（语言键 + 参数，可直接 {@link #tell(Player)}）。 */
    public static final class StampResult {
        private final boolean ok;
        private final String messageKey;
        private final Object[] args;

        StampResult(boolean ok, String messageKey, Object[] args) {
            this.ok = ok;
            this.messageKey = messageKey;
            this.args = args == null ? new Object[0] : args;
        }

        public boolean isOk() {
            return ok;
        }

        /** 把结果说给玩家（只让服务端说话，免得单人档里出现两条一样的消息）。 */
        public void tell(Player player) {
            tellKey(player, messageKey, args);
        }
    }

    /** 把一个语言键 + 参数说给玩家（吞掉一切异常：聊天绝不许影响机器）。 */
    public static void tellKey(Player player, String key, Object... args) {
        if (player == null || key == null) {
            return;
        }
        try {
            Level level = player.level();
            if (level != null && level.isClientSide) {
                return;
            }
            player.displayClientMessage(Component.translatable(key, args == null ? new Object[0] : args), false);
        } catch (Throwable ignored) {
            // 提示失败不影响已完成的动作
        }
    }

    /** 该位置上的机器对象（拿不到返回 {@code null}）。 */
    public static Object machineAt(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        try {
            return com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(level, pos);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 这个机器对象是不是"样板总成家族"（★ 按 gtlcore 的<b>抽象基类</b>判定，不按具体类）。
     *
     * <p>★ 片 C：<b>判定口径一个字不改</b>（现役机是具体类 {@code MEPatternBufferPartMachine} 的子类，
     * 本来就在这个基类之内）；片 C 改的是<b>行为落点</b> —— 从"只认惰性机"（旧的 {@code toLazy()}）
     * 变成"两种机器都认"（{@link #toTarget(Object)} 按机种分发到各自的适配器）。
     */
    public static boolean supports(Object machine) {
        return machine instanceof org.gtlcore.gtlcore.common.machine.multiblock.part.ae
                .MEPatternBufferPartMachineBase;
    }

    /** 这个机器对象是不是"我们的惰性猪咪样板总成"（动态容量那一族）。 */
    public static boolean isLazy(Object machine) {
        return machine instanceof LazyPatternAssemblyPartMachine;
    }

    /** ★ 片 C：这个机器对象是不是"我们的现役猪咪样板总成"（gtlcore 具体类那一族，10 档 + 超级版）。 */
    public static boolean isMolten(Object machine) {
        return machine instanceof MoltenPatternAssemblyPartMachine;
    }

    /**
     * ★ <b>片 C 的分发点</b>：把机器对象包成归档引擎认识的 {@link PatternArchiveTarget}。
     *
     * <p>这里就是"工具认得现役机、却主动拒绝"那条路的修复落点：旧实现只做
     * {@code machine instanceof LazyPatternAssemblyPartMachine} ⇒ 现役机拿到 {@code null} ⇒
     * 玩家看到 {@code archive.result.unsupported}（队长实机复现过）。
     * 现在三种机器各有一个<b>适配器</b>（逐方法转调各自已有的实现）⇒ 同一套引擎、同一套承诺。
     *
     * <p><b>三分支（顺序是硬约束）</b>：① 惰性机 {@link LazyPatternAssemblyPartMachine} →
     * {@link LazyPatternArchiveTarget}；② 现役机 {@link MoltenPatternAssemblyPartMachine} →
     * {@link MoltenPatternArchiveTarget}；③ <b>gtlcore 原版样板总成</b>
     * {@code org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine}
     * （含 gtladditions 的超级样板总成，它靠继承命中）→ {@link VanillaPatternArchiveTarget}。
     * ②必须在③之前 —— 现役机也是 gtlcore 那个具体类的子类。
     *
     * @return 认不出来（别的机器、或家族里不存样板的部件）返回 {@code null}（调用方回"不支持"）
     */
    private static PatternArchiveTarget toTarget(Object machine) {
        if (machine instanceof LazyPatternAssemblyPartMachine lazy) {
            return new LazyPatternArchiveTarget(lazy);
        }
        if (machine instanceof MoltenPatternAssemblyPartMachine molten) {
            return new MoltenPatternArchiveTarget(molten);
        }
        // ★ 新增分支：gtlcore 的「原版样板总成」及其任何子类（含可选的 gtladditions 超级样板总成）。
        //   ★★ 顺序是硬约束：MoltenPatternAssemblyPartMachine **也是** MEPatternBufferPartMachine 的子类
        //   ⇒ 这一支必须排在它**后面**，否则自家现役机会被当"原版机"处理（丢设置/名字 + 收尾换人）。
        //   软依赖说明：本分支只认 gtlcore 的具体类（硬前置），gtladditions 的类名一个字都不出现
        //   —— 它靠继承被这个 instanceof 命中 ⇒ gtladditions 缺失也不影响类加载。
        if (machine instanceof org.gtlcore.gtlcore.common.machine.multiblock.part.ae
                .MEPatternBufferPartMachine buffer) {
            return new VanillaPatternArchiveTarget(buffer);
        }
        // 剩下的是"在这个家族里、但不是样板仓本身"的机器（例如代理部件）⇒ 与改动前一样返回 null（报"不支持"）
        return null;
    }

    // ==================================================================================
    // 动作一：复制（源机 → 工具）★ 0.3.14 起按张扣空白样板（堵"无限粘贴"复制漏洞）
    // ==================================================================================

    /**
     * ★★ 0.3.14（队长口径：「剪切完以后应该自动清除。保存的，不然玩家就可以无限粘贴出样板了」）★★
     * <b>空白样板</b> = AE2 的 {@code ae2:blank_pattern}（= {@code appeng.core.definitions.AEItems.BLANK_PATTERN}）。
     *
     * <p><b>取证</b>（不猜）：
     * <ol>
     *   <li>gtlcore 具体类 {@code MEPatternBufferPartMachine.copyFromTag} 的字节码（javap）里，
     *       每成功放下一张样板就走一遍
     *       {@code AEItems.BLANK_PATTERN.stack() → AdvancedBlockPattern.foundItem(player, List.of(...), predicate)}
     *       → {@code ((IItemHandler) triplet.getB()).extractItem(triplet.getC(), 1, false)}；
     *       常量池实证含 {@code Lappeng/core/definitions/AEItems;BLANK_PATTERN} 与 {@code ae2:blank_pattern}；
     *       扣不到就 {@code goto 218}（= 立刻停止，后续样板不再搬）；</li>
     *   <li>AE2 自己的语言文件实证物品 id：{@code "item.ae2.blank_pattern": "空白样板"}
     *       （{@code appliedenergistics2-forge-15.4.10.jar!/assets/ae2/lang/zh_cn.json}），
     *       且 AE2 原版提示键就是 {@code chat.ae2.MissingBlankPatterns} = "用于复制的空白样板不足（缺失 %d）"；</li>
     *   <li>本模组沿用<b>同一个物品</b>（不自己另造耗材）：语义与 gtlcore 的"复制一次 = 一份耗材"完全一致，
     *       玩家不会遇到"两套样板货币"。</li>
     * </ol>
     *
     * <p><b>为什么不是"边取边扣"（gtlcore 那样）而是"先数后扣、整体拒绝"</b>：
     * 队长要求"数量不足 ⇒ 整体拒绝（一张都不取、工具不变、机器不变）"。
     * gtlcore 的"边取边扣"会留下"工具里只有前 3 张"的半成品，而我们这一步是<b>动玩家背包</b>的动作，
     * 半成品既难解释也难救。因此顺序固定为：
     * <ol>
     *   <li>只读地读源机全仓（{@code buildArchivePayload}）；源机空 ⇒ 直接返回，什么都不动；</li>
     *   <li>只读地数玩家身上的空白样板；不够 ⇒ 返回失败（<b>机器没动、工具没动、背包没动</b>）；</li>
     *   <li>写进工具（{@code writeToStack}）；写失败 ⇒ 返回（<b>还没扣料</b>）；</li>
     *   <li>到这里才按张扣空白样板（扣不出预期张数 ⇒ 记日志，但档案已经写好了，不半途回滚工具）。</li>
     * </ol>
     */
    public static StampResult copyFrom(Object machine, ItemStack stack, Player player) {
        PatternArchiveTarget target = toTarget(machine);
        if (target == null) {
            return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.unsupported", null);
        }
        try {
            LazyPatternArchiveCodec.Payload payload = target.buildArchive(
                    target.includeSettingsInArchive(), target.includeNameInArchive());
            if (payload == null || payload.isEmpty()) {
                return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.source_empty", null);
            }
            // ★ ① 先数空白样板：不够就整体拒绝（此刻机器/工具/背包一个字节都没动）
            int need = payload.patternCount;
            int have = countBlankPatterns(player);
            if (have < need) {
                GTLModular.LOGGER.info("[gtlmodular] 样板仓归档：复制被拒绝（空白样板不足）需要={} 身上={} pos={}",
                        need, have, target.pos());
                return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.blank_short",
                        new Object[]{need, have});
            }
            // ★ ② 再写工具（写失败 ⇒ 直接返回，仍然一张料都没扣）
            if (!LazyPatternArchiveCodec.writeToStack(stack, payload, false)) {
                return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.write_failed", null);
            }
            // ★ ③ 最后才真的扣料（复制档案**不带**剪切标记 ⇒ 可反复粘贴）
            int consumed = extractBlankPatterns(player, need);
            if (consumed != need) {
                // 走到这里说明"数得出来但扣不动"（背包被别的线程/容器动过）：如实记日志，不假装成功
                GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：复制扣空白样板数量不符 需要={} 实扣={} pos={}",
                        need, consumed, target.pos());
            }
            GTLModular.LOGGER.info("[gtlmodular] 样板仓归档：复制完成 张数={} 消耗空白样板={} pos={} 玩家={}",
                    payload.patternCount, consumed, target.pos(),
                    player == null ? "-" : player.getGameProfile().getName());
            return new StampResult(true, "gtlmodular.pattern_assembly.archive.result.copied",
                    new Object[]{payload.patternCount, consumed});
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：复制失败（源机未改动）pos={} 原因={}",
                    target.pos(), failure.toString());
            return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.error", null);
        }
    }

    // ==================================================================================
    // 动作二：剪切（源机 → 工具 → 清空源机；★ 先写成功再清源）
    // ==================================================================================

    /**
     * 剪切：先把全仓写进工具，<b>只有写成功之后</b>才清空源机。
     *
     * <p>「先写成功再清源」的落点就是这里的三步顺序：
     * <ol>
     *   <li>① 只读地把源机全仓读出来（{@code buildArchivePayload}）；源机空 ⇒ 直接返回，什么都不动；</li>
     *   <li>② 写进工具（{@code writeToStack}）；<b>写失败 ⇒ 直接返回，源机一张样板都不动</b>；</li>
     *   <li>③ 走到这里才 {@code clearArchiveContent()} 清源，然后<b>只调一次</b>
     *       {@code afterArchiveBulkChange()}（= 既有的 {@code onPatternInventoryChanged()}：
     *       作废样板→槽映射 + 逐槽收尾 + {@code needPatternSync} 让 AE 重取样板表），
     *       <b>不新增第二条刷新路径</b>。</li>
     * </ol>
     */
    public static StampResult cutFrom(Object machine, ItemStack stack, Player player) {
        PatternArchiveTarget target = toTarget(machine);
        if (target == null) {
            return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.unsupported", null);
        }
        try {
            // ① 只读地读全仓（含逐槽元数据）
            LazyPatternArchiveCodec.Payload payload = target.buildArchive(
                    target.includeSettingsInArchive(), target.includeNameInArchive());
            if (payload == null || payload.isEmpty()) {
                // ★ 2026-10-02（用户实测「我已经用剪切工具全部剪切了，但是 AE 里面还是有」）：
                //   源机**对我们的读路径**看起来是空的（例如惰性落盘把高槽数据截断之后的残留状态 ✗），
                //   但 **AE 侧可能仍拿着旧的样板表** ✗ ⇒ 这里也必须补一次"样板表过期"的收尾通知，
                //   让 AE 重新读一次、与机器真实状态收敛；否则就会出现"机器里没有、AE 里一堆"的鬼影 ✗。
                //   语义不变：源为空仍然是"没东西可剪"（result.source_empty）✓ 只是多一次通知 ✓
                try {
                    target.afterBulkChange();
                } catch (Throwable failure) {
                    GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：空剪切的收尾通知失败（已忽略）pos={} 原因={}",
                            target.pos(), failure.toString());
                }
                return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.source_empty", null);
            }
            // ② 先写成功（★ 0.3.14：标记成"剪切档案" ⇒ 粘贴成功一次之后工具会自动清空，只能落一次）
            if (!LazyPatternArchiveCodec.writeToStack(stack, payload, true)) {
                return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.write_failed", null);
            }
            // ③ 写成功了才清源
            target.clearAll();
            try {
                target.afterBulkChange();
            } catch (Throwable failure) {
                GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：剪切后的收尾失败"
                                + "（样板已清空，缓存最迟由下一 tick 的签名轮询兜住）pos={} 原因={}",
                        target.pos(), failure.toString());
            }
            GTLModular.LOGGER.info("[gtlmodular] 样板仓归档：剪切完成 张数={} 元数据条数={} pos={}",
                    payload.patternCount, payload.entries.size(), target.pos());
            return new StampResult(true, "gtlmodular.pattern_assembly.archive.result.cut",
                    new Object[]{payload.patternCount});
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：剪切异常（已中止）pos={} 原因={}",
                    target.pos(), failure.toString());
            return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.error", null);
        }
    }

    // ==================================================================================
    // 动作三：粘贴（工具 → 目标机）
    // ==================================================================================

    /**
     * 把工具里的档案写进目标机（容量装不下 ⇒ 整体拒绝，机器一个字节都不动）。
     *
     * <p><b>落位模式</b>取自目标机自己那个开关
     * （{@link LazyPatternAssemblyPartMachine#isArchiveAlignSlots()}）：
     * <ul>
     *   <li>关（默认）= {@link LazyPatternArchiveCodec.PlaceMode#APPEND} <b>追加</b>：从该机当前
     *       <b>已用最高槽的下一格</b>开始往后延伸 —— <b>任何情况下都不覆盖已有样板</b>；</li>
     *   <li>开 = {@link LazyPatternArchiveCodec.PlaceMode#ALIGN} <b>按原槽对齐</b>：按档案里的槽下标落位，
     *       遇到已占用的槽<b>跳过</b>（不写、不覆盖），跳过条数在结果提示里报告。</li>
     * </ul>
     * 面板按钮与手上工具右键走的是同一条路（同一个引擎、同一个开关）⇒ 两边行为必然一致。
     */
    public static StampResult pasteInto(Object machine, ItemStack stack, Player player) {
        PatternArchiveTarget target = toTarget(machine);
        if (target == null) {
            return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.unsupported", null);
        }
        try {
            LazyPatternArchiveCodec.Payload payload = LazyPatternArchiveCodec.readFromStack(stack);
            if (payload == null || payload.isEmpty()) {
                return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.empty", null);
            }
            // ★ 0.3.14：先判断"这份档案是不是剪切来的"（= 只有一次落地机会）
            boolean cutSourced = payload.cutSourced;
            LazyPatternArchiveCodec.ApplyResult result = LazyPatternArchiveCodec.apply(
                    target, payload, target.includeSettingsInArchive(), target.includeNameInArchive(),
                    target.placeMode());
            if (result == null) {
                return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.error", null);
            }
            if (!result.ok) {
                // ★ 粘贴被整体拒绝/失败 ⇒ **绝不能清工具**（"粘失败还把档案清了"是更严重的 bug）
                GTLModular.LOGGER.info("[gtlmodular] 样板仓归档：粘贴未生效（工具里的档案原样保留）"
                                + "剪切档案={} 结果={} pos={}", cutSourced, result.messageKey, target.pos());
                return new StampResult(false, result.messageKey, result.messageArgs);
            }
            // ★★ 只有"粘贴成功"这一条路上，剪切档案才随本次粘贴消耗掉 ★★
            if (cutSourced) {
                LazyPatternArchiveCodec.clearStack(stack);
                GTLModular.LOGGER.info("[gtlmodular] 样板仓归档：剪切档案已随本次粘贴消耗"
                                + "（工具已清空，不能再次粘贴）张数={} pos={}", payload.patternCount, target.pos());
                // 成功提示（说清写了什么）+ 单独一条"档案已被消耗"的提示，
                // 免得玩家再点一次「粘贴」时只看到一句"工具里没有档案"而不知道为什么。
                tellKey(player, "gtlmodular.pattern_assembly.archive.result.cut_consumed",
                        payload.patternCount);
            }
        // ★ 2026-10-02 与剪切对齐（cutFrom :294 / 空源 :280）：粘贴成功后也要通知
        //   "样板表过期 + AE 重读"，否则玩家粘回去后 AE 侧仍看不到（要重进游戏才刷新）✗
        target.afterBulkChange();
            return new StampResult(true, result.messageKey, result.messageArgs);
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：粘贴异常（已中止，目标机按操作前）pos={} 原因={}",
                    target.pos(), failure.toString());
            return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.error", null);
        }
    }

    // ==================================================================================
    // 0.3.14：空白样板（复制耗材）—— 数 / 扣
    // ==================================================================================

    /**
     * 空白样板物品：AE2 的 {@code ae2:blank_pattern}。
     *
     * <p>解析顺序：① AE2 官方定义 {@code AEItems.BLANK_PATTERN}（gtlcore 用的就是它，javap 实证）；
     * ② 注册表 id 兜底 {@code ae2:blank_pattern}（AE2 语言文件实证）。
     * 两条都拿不到 ⇒ 返回 {@link ItemStack#EMPTY}（调用方按"身上没有空白样板"处理，绝不猜别的物品）。
     */
    private static ItemStack blankPatternStack() {
        try {
            ItemStack stack = appeng.core.definitions.AEItems.BLANK_PATTERN.stack();
            if (stack != null && !stack.isEmpty()) {
                return stack;
            }
        } catch (Throwable ignored) {
            // AE2 版本没有这个字段（理论上不会）⇒ 落到注册表兜底
        }
        try {
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(new ResourceLocation("ae2", "blank_pattern"));
            if (item != null) {
                return new ItemStack(item);
            }
        } catch (Throwable ignored) {
            // 注册表里也没有 ⇒ EMPTY
        }
        return ItemStack.EMPTY;
    }

    /**
     * <b>只读</b>地数玩家身上有几张空白样板（主背包 36 格 + 副手 + 盔甲；不含当前打开的容器）。
     *
     * <p>只认 {@code ItemStack.is(blank)} —— 即同一个物品，NBT 不论（空白样板本来就不带 NBT）。
     */
    public static int countBlankPatterns(Player player) {
        if (player == null) {
            return 0;
        }
        ItemStack blank = blankPatternStack();
        if (blank.isEmpty()) {
            return 0;
        }
        int count = 0;
        try {
            // 主背包 + 副手 + 盔甲：Forge 的 IItemHandler 覆盖顺序是主背包 → 盔甲 → 副手，
            // 而 Player.getInventory().getSlots() 在 1.20.1 是 41（36 + 4 盔甲 + 1 副手）。
            net.minecraftforge.items.IItemHandler handler = new net.minecraftforge.items.wrapper.PlayerInvWrapper(player.getInventory());
            int slots = handler.getSlots();
            for (int i = 0; i < slots; i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty() && stack.is(blank.getItem())) {
                    count += stack.getCount();
                }
            }
        } catch (Throwable ignored) {
            // 数不出来 ⇒ 0（调用方按"不够"处理，宁可拒绝也不无故扣料）
        }
        return count;
    }

    /**
     * 按张扣空白样板（最多 {@code amount} 张）。返回<b>实扣张数</b>。
     *
     * <p>扣料细节（这里最容易写出 dupe/丢料）：
     * <ol>
     *   <li>一律用 {@code extractItem(slot, n, false)} <b>真扣</b>，把返回值写回原槽；
     *       原版 {@code extractItem} 返回的是"被拿走的那一份"，<b>不会</b>自动改槽内容；</li>
     *   <li>只对"确实是空白样板"的槽动手，一格一格扣到够了就停；</li>
     *   <li>任何异常都吞掉（返回已扣到的张数）—— 扣料失败不许把已经写好的档案搞坏。</li>
     * </ol>
     */
    public static int extractBlankPatterns(Player player, int amount) {
        if (player == null || amount <= 0) {
            return 0;
        }
        ItemStack blank = blankPatternStack();
        if (blank.isEmpty()) {
            return 0;
        }
        int remaining = amount;
        int consumed = 0;
        try {
            net.minecraftforge.items.IItemHandler handler = new net.minecraftforge.items.wrapper.PlayerInvWrapper(player.getInventory());
            int slots = handler.getSlots();
            for (int i = 0; i < slots && remaining > 0; i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty() || !stack.is(blank.getItem())) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                ItemStack extracted = handler.extractItem(i, take, false);
                int got = extracted == null ? 0 : extracted.getCount();
                if (got > 0) {
                    remaining -= got;
                    consumed += got;
                } else {
                    break;   // 扣不动 ⇒ 别空转（正常情况下不会发生）
                }
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：扣空白样板时出错（已扣 {} 张，其余放弃）原因={}",
                    consumed, failure.toString());
        }
        return consumed;
    }

    // ==================================================================================
    // 工具侧只读 / 清空（物品 tooltip 与"右键空气"用）
    // ==================================================================================

    /** 工具里有没有档案内容。 */
    public static boolean hasArchive(ItemStack stack) {
        return LazyPatternArchiveCodec.hasStackArchive(stack);
    }

    /** 工具里的样板张数（读 NBT 的计数键，不解码整份档案 ⇒ tooltip 可以放心每帧问）。 */
    public static int archivePatternCount(ItemStack stack) {
        return LazyPatternArchiveCodec.stackPatternCount(stack);
    }

    /** 工具里档案的来源描述（"{@code 维度 (x, y, z)}"）；没有来源信息返回空串。 */
    public static String archiveSource(ItemStack stack) {
        return LazyPatternArchiveCodec.stackSource(stack);
    }

    /** 清空工具里的档案。 */
    public static void clearArchive(ItemStack stack) {
        LazyPatternArchiveCodec.clearStack(stack);
    }
}
