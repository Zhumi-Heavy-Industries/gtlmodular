package com.gtlmodular.common.data.print;

/**
 * 「一次性工具 3D 打印」要用到的 10 种工具（产物是包里的脆弱工具系列）。
 *
 * <p>数据来源 = GTO（gtocore）{@code GTODisposableToolHandler} 的 {@code DisposableTool} 表：
 * GTO 只有 8 种（file / wrench / crowbar / wire_cutter / hammer / mallet / screwdriver / saw），
 * 每种的 {@code consume} 是「材料耐久 ÷ consume = 一次打印出来的把数」。
 * 队长要求产物直接套用本包的脆弱工具系列（共 10 种，多出 knife / mortar），
 * 因此这两把的 {@code consume} 按同族取值补成 4（锉/锯/螺丝刀同档），行为与其它工具一致。
 *
 * <p>{@code tagPath} 是模具浇铸配方要吃的 {@code forge:tools/<tagPath>} 物品标签：
 * 与 GTO 的 {@code CustomTags.CRAFTING_*} 同语义（拿一把对应工具当模种，浇钢成模具），
 * 但用 Forge 通用标签，这样 GT 工具与我们的脆弱工具都能当模种。
 */
public enum DisposableTools {

    CROWBAR("crowbar", "撬棍", "Crowbar", 3, "crowbars"),
    WRENCH("wrench", "扳手", "Wrench", 8, "wrenches"),
    FILE("file", "锉", "File", 4, "files"),
    SAW("saw", "锯", "Saw", 4, "saws"),
    SCREWDRIVER("screwdriver", "螺丝刀", "Screwdriver", 4, "screwdrivers"),
    WIRE_CUTTER("wire_cutter", "剪线钳", "Wire Cutter", 9, "wire_cutters"),
    HAMMER("hammer", "锻造锤", "Hammer", 6, "hammers"),
    MALLET("mallet", "软锤", "Mallet", 6, "mallets"),
    KNIFE("knife", "刀", "Knife", 4, "knives"),
    MORTAR("mortar", "研钵", "Mortar", 4, "mortars");

    /** 工具短名（= 贴图/物品 id 后缀，与 GTO 的 disposable_<name> 对齐）。 */
    public final String path;
    /** 中文显示名（「一次性<名>模具」用）。 */
    public final String zhName;
    /** 英文显示名。 */
    public final String enName;
    /** 材料耐久 ÷ 这个数 = 单次打印把数（GTO 原值）。 */
    public final int consume;
    /** {@code forge:tools/<tagPath>} 标签路径。 */
    public final String tagPath;

    DisposableTools(String path, String zhName, String enName, int consume, String tagPath) {
        this.path = path;
        this.zhName = zhName;
        this.enName = enName;
        this.consume = consume;
        this.tagPath = tagPath;
    }

    /** 模具物品 id：{@code disposable_<path>_mold}。 */
    public String moldId() {
        return "disposable_" + path + "_mold";
    }

    /** 产物脆弱工具 id：{@code fragile_tool:fragile_<path>}（KubeJS 注册的包内物品）。 */
    public String fragileItemId() {
        return "fragile_tool:fragile_" + path;
    }
}
