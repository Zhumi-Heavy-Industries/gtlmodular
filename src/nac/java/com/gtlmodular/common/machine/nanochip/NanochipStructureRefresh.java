package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * NAC 多方块的「<b>立即更新结构</b>」——左侧配置页图标列里的**一个小方按钮，点一下就更新**
 * （2026-09-21 队长定稿：「就改成这种小按钮，点一下就更新了」）。
 *
 * <h2>位置演进（四次，每次都是队长一句话推着走）</h2>
 * <ol>
 *   <li><b>0.1.16</b>：覆写 {@code createUI(Player)} 自绘 176×230 朴素页放按钮
 *       ⇒ 把基类 fancy 机器界面整套顶掉（施工记录 O.69）。</li>
 *   <li><b>0.1.24-①</b>：改覆写 {@code createUIWidget()}，按钮进主页面底部（显示区压到 99 高）。</li>
 *   <li><b>0.1.24-②</b>：做成 {@code IFancyConfigurator}（带展开面板 + 全宽按钮），落进左侧那列。</li>
 *   <li><b>0.1.24-③（本文件）</b>：队长「就改成这种小按钮，点一下就更新了」
 *       ⇒ 改成 {@link IFancyConfiguratorButton}：<b>图标本身就是按钮</b>，
 *       点一下直接触发，<b>没有展开面板、不用点第二下</b>。</li>
 * </ol>
 *
 * <h2>为什么"点图标即动作"能生效（javap 取证，不是猜）</h2>
 * <ol>
 *   <li>{@code ConfiguratorPanel$Tab.onClick(ClickData)} 的字节码：
 *       若 configurator 是 {@code IFancyConfiguratorButton} ⇒ 直接调它的 {@code onClick(data)}，
 *       <b>不走"展开/收起面板"那两支</b>；否则才是展开/收起。</li>
 *   <li>Tab 里的那个按钮是 LDLib {@code ButtonWidget}，其 {@code mouseClicked} 字节码是
 *       <b>无条件</b> {@code writeClientAction(1, data::writeToBuf)} 之后再调 {@code onPressCallback}
 *       （客户端那次 {@code isRemote=true}）；而 {@code handleClientAction(id=1, buf)} 在<b>服务端</b>
 *       读出 {@code ClickData} 后再跑同一个回调（{@code isRemote=false}）。</li>
 *   <li>⇒ 同一个 {@link #onClick(ClickData)} 会在两侧各跑一次，<b>靠 {@code data.isRemote} 分侧</b>。
 *       GTCEu 自己的「工作开关」配置页就是这么改机器状态的（它的 {@code onClick} 直接
 *       {@code setWorkingEnabled(...)}，没有任何额外通道）。</li>
 * </ol>
 *
 * <h2>⚠ 代价（如实登记）：没有聊天回执</h2>
 * 这个形态下 {@code onClick} 只拿到 {@code ClickData}，<b>拿不到玩家</b>（上一版的那个按钮能把
 * {@code getGui().entityPlayer} 取出来，是因为它自己是个 Widget）。所以请求人传 {@code null}：
 * 三道门照旧归零、结构照旧立刻重查，<b>但没有那几条"已受理 / 已成形 / 缺件清单"的聊天回执</b>。
 * 命中与否以服务器日志为准（{@code [gtlmodular] NAC … 已成形 / 仍未成形}）。
 * 若队长要回执，正解是换回"带面板的配置页 + 面板里的按钮"（两次点击），二者不可兼得 —— 这条写在这里，
 * 免得下次有人以为"回执丢了是 bug"。
 */
public final class NanochipStructureRefresh {

    private NanochipStructureRefresh() {}

    /** 按钮文字（同时也是这个配置页标签的标题与悬停说明）。 */
    public static final String LANG_BUTTON = "gtlmodular.nanochip.structure.refresh";

    /** 按钮悬停说明。 */
    public static final String LANG_TIP = "gtlmodular.nanochip.structure.refresh.tip";

    /** 已受理（等服务端出结论）。 */
    public static final String LANG_REQUESTED = "gtlmodular.nanochip.structure.refresh.requested";

    /** 已成形。 */
    public static final String LANG_OK = "gtlmodular.nanochip.structure.refresh.ok";

    /** 仍未成形。 */
    public static final String LANG_FAIL = "gtlmodular.nanochip.structure.refresh.fail";

    /** 仍未成形：唯一方块格全对 ⇒ 缺口在多解格或数量类限制上（主机专用的那条兜底说明）。 */
    public static final String LANG_FAIL_OTHER = "gtlmodular.nanochip.structure.refresh.other";

    /** 图标 = GTCEu「扫描器」的正面（"重新扫一遍结构"最贴切；已核该贴图在 gtceu 包里存在）。 */
    public static final String ICON = "gtceu:textures/block/machines/scanner/overlay_front.png";

    /**
     * ★★【B · 停用"我们主动刷新结构"】<b>手动「立即更新结构」这个入口是否还生效</b> ★★
     *
     * <p><b>默认 {@code true} = 保留</b>（当前默认值刻意<b>不是</b> false，理由见下）。
     *
     * <h3>为什么它与"自动刷新"分开、且默认保留</h3>
     * 用户口径是「不要刷新结构什么的」—— 指的是<b>我们自己在后台触发</b>的结构重判/催促那一类
     * （那才是"一进游戏就要重新加载结构、而且要好久"的来源）。本按钮<b>不是</b>自动的：
     * 它只在玩家点图标时走一次 {@code data.isRemote == false} 的服务端回调（见 {@link RefreshConfigurator}），
     * 自己不跑任何昂贵匹配，只把三道门归零交给上游下一轮。
     * 而在 B 把 {@code NanochipAssemblyComplexMachine.LOAD_PARTS_RECOVERY_ENABLED} 置 {@code false}
     * 之后，「读档后零件表为空 ⇒ 等级 ULV / 能源 0」这条上游缺口<b>已经没有任何自动补救</b> ⇒
     * 若连这个按钮也停掉，玩家在游戏里就只剩"拆掉控制器再放回"这一条路（只能手拆）。
     * ⇒ 默认保留它，是"停掉自动刷新"与"别把玩家最后一条自助路也堵死"之间的最小必要取舍。
     *
     * <h3>要"最干净"档就改这一行</h3>
     * 置 {@code false} ⇒ {@link RefreshConfigurator#onClick} 直接返回：图标照旧显示、点它不再解开三道门、
     * 上游也不会因此被催着跑那次全量匹配。此时全工程<b>再无任何结构刷新入口</b>
     * （自动的已由 B 主闸停用，手动的由本开关停用），读档 ULV 只能靠拆放控制器恢复。
     * 编译期常量、不是 {@code @Persisted}、不进存档、不改存档格式、不新增 mixin。
     */
    public static final boolean MANUAL_REFRESH_ENABLED = true;

    /**
     * 能被「立即更新结构」按钮刷新的机器（本包只有 NAC 主机与 14 台模块塔实现它）。
     *
     * <p>实现方必须做三件事（缺一不可，见 {@code NanochipAssemblyComplexMachine#requestStructureRefresh}）：
     * 归零变化闸 / 归零节流 / 记住请求人；<b>不</b>要求它自己做任何昂贵的结构匹配。
     */
    public interface Refreshable {

        /**
         * @param requester 点按钮的那个人（服务端侧；客户端或拿不到时为 {@code null} ——
         *                  本形态下**恒为 null**，见类注释的"代价"一节）。实现方必须对 null 有守卫。
         */
        void requestStructureRefresh(ServerPlayer requester);
    }

    /**
     * 左列那个<b>小方按钮</b>：点一下 ⇒ 立刻解开三道门 + 喂一轮结构检查。
     *
     * <p>挂法（主机与 14 台模块塔都一样）：
     * <pre>{@code
     * @Override
     * public void attachConfigurators(ConfiguratorPanel panel) {
     *     super.attachConfigurators(panel);
     *     panel.attachConfigurators(new NanochipStructureRefresh.RefreshConfigurator(this));
     * }
     * }</pre>
     */
    public static final class RefreshConfigurator implements IFancyConfiguratorButton {

        private final MetaMachine machine;

        public RefreshConfigurator(MetaMachine machine) {
            this.machine = machine;
        }

        /**
         * 这个配置页是"按钮型"，没有展开面板 —— 但 {@code getTitle()} 仍要覆写：
         * {@code IFancyConfiguratorButton} 的默认实现是<b>抛 {@code NotImplementedException}</b>
         * （javap 已核），而 Tab 构造时可能取标题当悬停文本。
         */
        @Override
        public Component getTitle() {
            return Component.translatable(LANG_BUTTON);
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture(ICON);
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable(LANG_TIP));
        }

        /**
         * 点一下。两侧都会进来（见类注释的第 2 条），**只在服务端那一侧做事**。
         *
         * <p>为什么必须判 {@code data.isRemote}：客户端那次也调这个回调，
         * 若两边都执行，等于"点一下触发两次"（客户端那次还会去动客户端机器实例上的闸，
         * 纯属污染）。判据 {@code check_structure_refresh_button.js} 的 A2 钉着这一条。
         */
        @Override
        public void onClick(ClickData data) {
            if (data == null || data.isRemote) {
                return;   // 客户端那次不做任何事（真正的动作在服务端那次）
            }
            if (!MANUAL_REFRESH_ENABLED) {
                return;   // ★【B】手动刷新入口停用（见 MANUAL_REFRESH_ENABLED 的 javadoc）
            }
            if (this.machine instanceof Refreshable refreshable) {
                // requester = null：本形态拿不到玩家（见类注释"代价"），实现方必须容忍。
                refreshable.requestStructureRefresh(null);
            }
        }
    }
}
