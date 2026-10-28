package com.gtlmodular.common.event;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.machine.nanochip.NanochipStructurePosIndex;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ★★【方块移除定点失效】的 level 钩子 —— 只服务 NAC（及其后续模块/地心系列）的结构位置缓存。★★
 *
 * <p>思路出处：另一位作者（秋叶）的「然后 <b>level 注入</b>一下 / 有个<b>方块移除的位置</b>」
 * ⇒ 落地形态就是本类：<b>Forge 事件，零混入</b>（不新增/不启用 mixin、不动 {@code gtlmodular.mixins.json}）。
 *
 * <h2>为什么选 {@code BlockEvent.BreakEvent}（逐条对比，依据见实施记录 §③）</h2>
 * <table border="1">
 *   <tr><th>候选</th><th>能不能拿到「方块移除」</th><th>结论</th></tr>
 *   <tr><td>{@code BlockEvent.BreakEvent}（本类）</td>
 *       <td><b>能</b>：玩家挖掉方块时触发（原版 {@code ServerPlayerGameMode#destroyBlock} 在
 *           {@code removeBlock} <b>之前</b>问 Forge），事件带 {@code (Level, BlockPos)}</td>
 *       <td>✅ <b>选它</b>：这是"最早、最省"的那个点（玩家一挖就到手，零世界扫描、零区块遍历），
 *           而且它覆盖的正是用户实测的那条路径（玩家手动敲掉一个方块）</td></tr>
 *   <tr><td>{@code PlayerEvent.BreakSpeed}</td>
 *       <td>不能：它只调"挖得多快"</td><td>✗ 与移除无关</td></tr>
 *   <tr><td>{@code BlockEvent.EntityPlaceEvent}</td>
 *       <td>不能：它是"放下方块"；而且 NAC 主机的 CHECK 格全部已被真实方块占着
 *           （空格/K/I 三类 {@code any()} 格放什么都合法）⇒ <b>放手放不出失效</b></td>
 *       <td>✗ 不需要（论证见实施记录 §③）</td></tr>
 *   <tr><td>上游回调（{@code MultiblockState.onBlockStateChanged}）</td>
 *       <td>能，但<b>门是死的</b>：gtlcore 的 {@code LevelMixin} 要求
 *           {@code chunkPosMapping} 与 {@code MultiblockState.cache} 同时命中，
 *           而这两份都是纯运行期数据（{@code MultiblockWorldSavedData.save()} 逐字节码原样返回、
 *           {@code multiblockState} 没有 {@code @Persisted}）⇒ 读档后两条都空</td>
 *       <td>⭐ 它是<b>判定入口</b>（本功能命中后正是调它），但<b>不能当触发器</b></td></tr>
 *   <tr><td>我们自己的 {@code LevelChunk} 监听</td>
 *       <td>不存在，且要新增混入才能有</td><td>✗ 违反"零混入"</td></tr>
 * </table>
 *
 * <h2>为什么"只记账、不当场判定"</h2>
 * 本事件在方块<b>真的被移除之前</b>触发 ⇒ 当场判定读到的是旧世界（那一格还是原方块），
 * 只会白跑一次匹配、还会把"真结果"污染给本类主机 {@code checkPatternWithLock()} 的同 tick 折叠。
 * 所以这里只把坐标交给 {@link NanochipStructurePosIndex}，真正让上游重判的动作落在
 * 主机的<b>下一 tick</b> —— 与队长钉死的口径「拆一格外壳 ⇒ 下一 tick 失效」逐字一致。
 *
 * <h2>失败姿态</h2>
 * 客户端 / 事件被别的模组取消（保护插件、领地插件…）/ 表建不出来 / 解析异常 ⇒ 一律什么都不做，
 * 逐字回到上游原有行为。<b>本类不产生任何判定</b>（它连 {@code isFormed} 都不读）。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NanochipBlockRemovalHandler {

    private NanochipBlockRemovalHandler() {
    }

    /**
     * 玩家挖掉一个方块。
     *
     * <p>用 {@link EventPriority#LOWEST} 的理由：要看到"别的模组最终的取消结论"
     * （保护/领地等通常挂在默认优先级上）⇒ 被取消的那些<b>不该</b>进入我们的待办。
     * 同款写法先例：{@code gtl_enhancedcore} 的 {@code IvTaskProtection#breaking}
     * （{@code _census/enhancedcore-src/.../IvTaskProtection.java:20}）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // ★★ 2026-11-12【原初模式】总开关：不接管、不记账 ⇒ 本钩子直接返回 ★★
        //   「方块移除 ⇒ 定点失效」整条链在原初模式下闲置（另一入口
        //   NanochipStructurePosIndex.noteBlockRemoved 同样早退，这里再早退一次是为了
        //   连事件体里的判断与爆炸那几百格的循环都省掉）。结构失效完全回到上游自己的
        //   LevelMixin → MultiblockState.onBlockStateChanged 路径。
        if (GTLModularConfig.isNanochipOriginalMode()) {
            return;
        }
        if (event == null || event.isCanceled()) {
            return;                                        // 这次挖掘被取消了 ⇒ 方块不会被移除
        }
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;                                        // 只认服务端世界（客户端那次不算数）
        }
        NanochipStructurePosIndex.noteBlockRemoved(level, event.getPos());
    }

    /**
     * 爆炸会炸掉的那批方块（TNT / 苦力怕 / 别的模组的爆炸）。
     *
     * <p>为什么顺手接上：{@code BlockEvent.BreakEvent} <b>只覆盖玩家挖掘</b> ——
     * 这条不是推测，本仓就有同款取证：{@code gtl_repo/.../DShanhaiMaintenanceHatchMachine.java:523-528}
     * 逐字写着「原先只靠 {@code DiskMachineDropHandler} 监听 {@code BlockEvent.BreakEvent}，
     * 而爆炸、{@code /fill}、WorldEdit、机械钻头等移除路径都<b>不触发</b>该事件」
     * ⇒ 在"上游那两条通知路径哑掉"的状态下，炸出来的洞同样不会被发现。本事件给的是
     * <b>即将被炸掉的方块清单</b>（同样在移除之前）⇒ 与挖掘完全同一条"记账 ⇒ 下一 tick 判定"的路。
     *
     * <p>成本：每个坐标一次哈希表二分 + 每台主机一次相对偏移查表；被炸的方块通常是几十~几百格，
     * 且只在有已登记主机时才真查 ⇒ 可忽略。任何异常都在
     * {@link NanochipStructurePosIndex#noteBlockRemoved} 内部被吞掉。
     */
    @SubscribeEvent
    public static void onExplosionDetonate(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        // ★★ 2026-11-12【原初模式】总开关：同上 —— 直接返回，连 affectedBlocks 都不遍历 ★★
        if (GTLModularConfig.isNanochipOriginalMode()) {
            return;
        }
        if (event == null || event.getLevel() == null || event.getLevel().isClientSide) {
            return;
        }
        var affected = event.getAffectedBlocks();
        if (affected == null || affected.isEmpty()) {
            return;
        }
        for (net.minecraft.core.BlockPos pos : affected) {
            NanochipStructurePosIndex.noteBlockRemoved(event.getLevel(), pos);
        }
    }

    /**
     * <b>维度卸载</b>（世界卸载 / 服务器停止 / 单人档退出）⇒ 把该维度的登记表一次性丢掉。
     *
     * <p>为什么加这一条（本轮指针化配套）：表里现在存的是<b>指向机器的指针</b>
     * ⇒ "摘除时机"必须逐条落实（见 {@code _census/位置表指针化_实施记录.md} §③）。
     * 每台机器自己的 {@code onUnload()} 已经覆盖了"区块卸载"这一大类
     * （原版 {@code LevelChunk.clearAllBlockEntities()} → {@code BlockEntity.setRemoved()} →
     * GTCEu {@code MetaMachineBlockEntity.setRemoved()} → {@code MetaMachine.onUnload()}，
     * 本仓既有取证见 {@code _census/enhancedcore-src/docs/客户端深度优化可行性报告-20260929.md:106-108}
     * 与 {@code NanochipPipeMachine.java:304-309}）；本事件是<b>整维度</b>的最后一道主动闸，
     * 不依赖"每台都走到了自己的回调"。
     *
     * <p>只认服务端维度：单人档里客户端与服务端世界的 {@code dimension()} <b>是同一个键</b>
     * ⇒ 若连客户端卸载也去清，会把服务端那张表误清（这正是本轮在索引侧修掉的缺口之一）。
     *
     * <p>失败姿态：拿不到/不是 {@code ServerLevel} ⇒ 直接返回；索引侧还有 {@link java.lang.ref.WeakReference}
     * 兜底 ⇒ 即使这一条一次都没触发，也<b>不可能</b>钉住任何机器 / 方块实体 / 世界。
     */
    @SubscribeEvent
    public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event == null || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        NanochipStructurePosIndex.forgetDimension(serverLevel.dimension());
    }
}
