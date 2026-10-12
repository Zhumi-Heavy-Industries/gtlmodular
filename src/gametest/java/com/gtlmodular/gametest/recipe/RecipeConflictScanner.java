package com.gtlmodular.gametest.recipe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

public final class RecipeConflictScanner {
    private final ServerLevel level;
    private final String product;
    private final Method identity;
    private final Constructor<?> decodedPattern;
    private final RecipeConflictProbe probe;

    public RecipeConflictScanner(ServerLevel level, String product, RecipeConflictProbe probe) {
        this.level = level;
        this.product = product;
        this.probe = probe;
        try {
            // 测试源码集不依赖 main；独立版既不加载也不模拟完整版的身份闸。
            if (product.equals("gtlmodular")) {
                Class<?> matching = Class.forName("com.gtlmodular.common.machine.pattern.PatternRecipeMatching");
                Class<?> decoded = Class.forName(matching.getName() + "$DecodedPattern");
                decodedPattern = decoded.getConstructor(IPatternDetails.class, int.class);
                identity = matching.getMethod("patternIdentityMatches", GTRecipe.class, decoded);
            } else {
                identity = null;
                decodedPattern = null;
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法调用实际样板身份判据", e);
        }
    }

    public Report scan() {
        Map<GTRecipeType, Map<String, GTRecipe>> tables = new LinkedHashMap<>();
        Map<String, Integer> excluded = new TreeMap<>();
        for (var recipe : level.getRecipeManager().getRecipes()) {
            if (recipe instanceof GTRecipe gt) {
                tables.computeIfAbsent(gt.recipeType, key -> new TreeMap<>()).put(gt.id.toString(), gt);
            } else {
                excluded.merge(recipe.getType().toString(), 1, Integer::sum);
            }
        }
        // 代理表参与实际查找，不能仅扫描 RecipeManager 后漏掉转换出的 GT 配方。
        for (GTRecipeType type : GTRegistries.RECIPE_TYPES) {
            type.getProxyRecipes().values().forEach(recipes -> recipes.forEach(recipe ->
                    tables.computeIfAbsent(type, key -> new TreeMap<>()).putIfAbsent(recipe.id.toString(), recipe)));
        }
        Map<String, Table> summary = new TreeMap<>();
        List<Finding> findings = new ArrayList<>();
        List<Map<String, String>> unresolved = new ArrayList<>();
        List<Map<String, String>> unscannable = new ArrayList<>();
        long comparisons = 0;
        for (var table : tables.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString())).toList()) {
            GTRecipeType type = table.getKey();
            List<String> machines = GTRegistries.MACHINES.values().stream()
                    .filter(machine -> machine.getRecipeTypes() != null
                            && Arrays.asList(machine.getRecipeTypes()).contains(type))
                    .map(machine -> machine.getId().toString()).sorted().toList();
            List<Entry> entries = table.getValue().values().stream().map(Entry::new).toList();
            entries.stream().filter(entry -> entry.inputs.alternatives().isEmpty()).forEach(entry ->
                    unscannable.add(Map.of("type", type.toString(), "recipe", entry.recipe.id.toString(),
                            "reason", "没有可枚举的物品/流体输入（空标签或仅电路等），无法构造正常 ME 推料")));
            summary.put(type.toString(), new Table(entries.size(), machines));
            Map<AEKey, Set<Entry>> index = new HashMap<>();
            entries.forEach(entry -> entry.inputs.keys().forEach(key ->
                    index.computeIfAbsent(key, ignored -> new HashSet<>()).add(entry)));
            for (Entry candidate : entries) {
                // 每一路都必须有候选交集；用最稀少的一路缩小集合，不跳过任意满足条件的配对。
                List<AEKey> rarest = candidate.inputs.alternatives().stream().min(Comparator.comparingLong(keys ->
                        keys.stream().mapToLong(key -> index.getOrDefault(key, Set.of()).size()).sum())).orElse(List.of());
                Set<Entry> possible = new HashSet<>();
                rarest.forEach(key -> possible.addAll(index.getOrDefault(key, Set.of())));
                for (Entry target : possible.stream().sorted(Comparator.comparing(e -> e.recipe.id.toString())).toList()) {
                    if (candidate == target) continue;
                    comparisons++;
                    var search = candidate.inputs.suppliedBy(target.inputs);
                    if (search.exhausted()) unresolved.add(Map.of("type", type.toString(),
                            "candidate", candidate.recipe.id.toString(), "target", target.recipe.id.toString(),
                            "reason", "原料候选搜索超过 4096 个节点，未证明安全"));
                    if (search.witness() == null) continue;
                    var witness = search.witness();
                    String guard = identityGuard(candidate.recipe, target.recipe, witness);
                    List<String> conditions = new ArrayList<>();
                    conditions.addAll(candidate.caveats);
                    conditions.addAll(target.caveats);
                    String status = conditions.isEmpty() ? "CONFLICT" : "CONDITIONAL";
                    if (owned(candidate.recipe) || owned(target.recipe)
                            || candidate.recipe.id.toString().startsWith("gtl_gametest:conflict_scan/")) {
                        probe.verify(candidate.recipe, target.recipe, witness);
                    }
                    findings.add(new Finding(type.toString(), candidate.recipe.id.toString(), target.recipe.id.toString(),
                            owned(candidate.recipe) || owned(target.recipe), status, guard,
                            witness.targetBatches(), witness.circuit(), witness.supply().describe(), conditions,
                            RecipeHelper.getInputEUt(candidate.recipe), candidate.recipe.duration,
                            RecipeHelper.getInputEUt(target.recipe), target.recipe.duration,
                            "候选配方通过目标样板供料的真实 ME 候选/数量匹配及电路检查；"
                                    + "targetBatches 表示同槽积累的目标批数，机器清单见 tables。"));
                }
            }
        }
        Map<String, Long> known = new TreeMap<>();
        findings.forEach(finding -> {
            String issue = KnownRecipeConflicts.issue(finding);
            if (!issue.isEmpty()) known.merge(issue, 1L, Long::sum);
        });
        return new Report(product, level.getRecipeManager().getRecipes().size(), comparisons, summary,
                excluded, findings, unresolved, unscannable, known);
    }

    private boolean owned(GTRecipe recipe) {
        return recipe.id.getNamespace().equals("gtlmodular") || recipe.id.getNamespace().equals("gtl_nac");
    }

    private String identityGuard(GTRecipe candidate, GTRecipe target, RecipeConflictInputs.Witness witness) {
        if (identity == null) return "NOT_PRESENT";
        List<GenericStack> outputs = new ArrayList<>();
        for (var content : target.getOutputContents(ItemRecipeCapability.CAP)) {
            var stacks = ((Ingredient) content.content).getItems();
            if (stacks.length > 0 && !stacks[0].isEmpty()) {
                outputs.add(new GenericStack(AEItemKey.of(stacks[0]), 1));
                break;
            }
        }
        for (var content : target.getOutputContents(FluidRecipeCapability.CAP)) {
            var stacks = ((FluidIngredient) content.content).getStacks();
            if (stacks.length > 0 && !stacks[0].isEmpty()) {
                outputs.add(new GenericStack(AEFluidKey.of(stacks[0].getFluid()), 1));
                break;
            }
        }
        if (outputs.isEmpty()) return "NO_PRIMARY_OUTPUT";
        var inputs = witness.supply().patternInputs(witness.circuit());
        if (inputs.size() > 81) return "PATTERN_TOO_LARGE";
        var encoded = PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new),
                outputs.toArray(GenericStack[]::new));
        var pattern = PatternDetailsHelper.decodePattern(encoded, level);
        if (pattern == null) throw new IllegalStateException("扫描样板解码失败：" + target.id);
        try {
            Object decoded = decodedPattern.newInstance(pattern, witness.circuit());
            return (boolean) identity.invoke(null, candidate, decoded) ? "ALLOWS" : "BLOCKS";
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("扫描身份闸失败：" + target.id, e);
        }
    }

    private static final class Entry {
        private final GTRecipe recipe;
        private final RecipeConflictInputs inputs;
        private final List<String> caveats = new ArrayList<>();

        private Entry(GTRecipe recipe) {
            // 某些 Ingredient 会缓存随机数量；扫描副本，避免影响随后执行的加工用例。
            this.recipe = recipe.copy();
            inputs = new RecipeConflictInputs(this.recipe);
            recipe.conditions.forEach(condition -> caveats.add(recipe.id + " condition="
                    + GTRegistries.RECIPE_CONDITIONS.getKey(condition.getType()) + " " + condition.serialize()));
            if (recipe.inputs.keySet().stream().anyMatch(cap -> cap != ItemRecipeCapability.CAP
                    && cap != FluidRecipeCapability.CAP)) caveats.add(recipe.id + " 存在其他输入能力");
            if (recipe.tickInputs.keySet().stream().anyMatch(cap -> cap != EURecipeCapability.CAP)) {
                caveats.add(recipe.id + " 存在非 EU 的每 tick 输入");
            }
            if (recipe.isFuel) caveats.add(recipe.id + " 燃料配方，需核实发电机查找路径");
        }
    }

    public record Table(int recipes, List<String> machines) {}
    public record Finding(String type, String candidate, String target, boolean owned, String status,
                          String patternIdentity, long targetBatches, int circuit, Map<String, Long> supply,
                          List<String> conditions, long candidateEUt, int candidateTicks,
                          long targetEUt, int targetTicks, String reason) {}
    public record Report(String product, int loadedRecipes, long directedComparisons, Map<String, Table> tables,
                         Map<String, Integer> nonGtRecipes, List<Finding> findings,
                         List<Map<String, String>> unresolved, List<Map<String, String>> unscannable,
                         Map<String, Long> knownFailureCounts) {
        public List<Finding> ownConflicts() {
            return findings.stream().filter(f -> f.owned && f.status.equals("CONFLICT")).toList();
        }
    }
}
