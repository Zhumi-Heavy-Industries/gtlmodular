package com.gtlmodular.common.data.stellar;

import com.gtlmodular.GTLModular;
import net.minecraftforge.event.server.ServerStartedEvent;

/**
 * GTO「恒星炎炀锻炉」配方的注册点 —— <b>本类不再自己构造配方</b>。
 *
 * <h2>为什么只剩一个空壳（2026-11-24）</h2>
 * 这里曾经用 {@code installSimple(...)} 在运行期构建 28 条配方（三味合金形态链 12、
 * 两套陶瓷链 10、六个机壳 6），并<b>双写</b> {@code GTRecipeType.getLookup()} 与
 * {@code RecipeManager.byType}。但同一批配方<b>另有一份逐项相同的 JSON 数据包</b>：
 * {@code data/gtlmodular/recipes/stellar/<id>.json}，配方 id 同为
 * {@code gtlmodular:stellar/<id>}（{@code PREFIX = "stellar/"}）。
 *
 * <p>两份都生效 ⇒ JEI 里每条配方并列出现两页，玩家看到「同一个产物有两个一模一样的
 * 配方页」——输入、耗时、EU/t 全相同，正是这个缺陷。
 *
 * <p>历史症状也对得上：{@code b5b4023} 记录过「31 条只成功 11 条、skipped=0」。
 * 那 20 条不是构建失败，而是 {@code lookup.addRecipe(...)} 因为数据包里已有同配方
 * 而返回 {@code false}——也就是说，<b>玩家看到的一直是数据包那一份</b>，Java 这份是影子。
 *
 * <h2>现在的分工</h2>
 * 配方<b>只由数据包提供</b>：{@code data/gtlmodular/recipes/stellar/} 下的
 * 28 个 JSON（形态链 12 + 陶瓷 10 + 机壳 6）与 3 个合金搅拌配方。
 * 保留数据包一侧的理由：它走标准数据包加载，不依赖 {@code RecipeManager.byType}
 * 的反射取字段（那条路一旦取不到字段就只剩 lookup，JEI 会不显示）。
 *
 * <p>2026-11-24 逐条比对过：这 28 条 Java 配方与对应 JSON 的 type / duration /
 * EU/t / 炉温 / 物品输入 / 不消耗输入 / 流体 / 输出<b>全部一致</b>，所以删掉 Java 侧
 * 不改变任何一条配方的数值。
 *
 * <h2>新增配方怎么加</h2>
 * 往 {@code data/gtlmodular/recipes/stellar/} 放 JSON。<b>不要再回到这里写 Java</b>，
 * 否则又变成两份。
 *
 * <p>本类保留 {@code register()} 只是为了让 {@code GTLModular} 的调用点不动，
 * 并留一条启动日志说明配方来源。
 */
public final class StellarForgeRecipes {

    private StellarForgeRecipes() {
    }

    /** 在 {@code GTLModular} 构造里调用一次（Forge 事件总线）。 */
    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(StellarForgeRecipes::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        GTLModular.LOGGER.info("[gtlmodular] 恒星炎炀锻炉：配方全部由数据包 "
                + "data/gtlmodular/recipes/stellar 提供（28 条形态链/陶瓷/机壳 + 3 条合金搅拌），"
                + "本类不再重复注册");
    }
}
