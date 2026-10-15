package com.gtlmodular.gametest;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.api.block.BlockProperties;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.gametest.fixture.NacStructureLayout;
import com.gtlmodular.gametest.fixture.PatternAssemblyFixture;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeStatus;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PatternAssemblyTests {
    private static final List<PatternAssemblyFixture> fixtures = new ArrayList<>();
    private static final List<String> variants = List.of("lazy", "mv", "super");
    private static final List<String> recipes = List.of("x", "y", "c1", "c2");

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        if (!NanochipModContext.modId().equals("gtlmodular")) return List.of();
        List<TestFunction> tests = new ArrayList<>();
        for (String variant : variants) {
            for (String scenario : List.of("identity", "circuit", "type", "wake")) {
                String name = "gtlmodular.pattern_" + variant + "_" + scenario;
                tests.add(new TestFunction("pattern_assembly", name, NacStructureLayout.templateId().toString(),
                        6000, 0, true, helper -> exercise(helper, variant, scenario)));
            }
        }
        return tests;
    }

    @AfterBatch(batch = "pattern_assembly")
    public static void cleanup(ServerLevel level) {
        fixtures.forEach(PatternAssemblyFixture::releaseChunks);
        fixtures.clear();
    }

    private static void exercise(GameTestHelper helper, String variant, String scenario) {
        Run run = new Run(helper, variant, scenario.equals("type"), scenario.equals("wake"));
        var sequence = helper.startSequence().thenWaitUntil(run.fixture::assertReady).thenExecute(run::prepare);
        // 来回切换让上一次的真实 lastRecipe 留在控制器里，覆盖热缓存而非只测空机起跑。
        for (String target : switch (scenario) {
            case "identity" -> List.of("x", "y", "x", "y");
            case "circuit" -> List.of("c1", "c2", "c1", "c2");
            default -> List.of("x");
        }) {
            if (run.wake) {
                // 故障态不靠“摆”：先退订，再让服务器自己的 serverTick 把 server_tick 落到 false。
                sequence.thenExecute(run::breakHostTickSubscription)
                        .thenIdle(2)
                        .thenExecute(run::assertHostAsleep);
            }
            sequence.thenExecute(() -> run.push(target))
                    .thenWaitUntil(run::assertFinished)
                    .thenExecuteFor(40, run::assertFinished)
                    .thenExecute(run::log);
        }
        sequence.thenExecute(run.fixture::releaseChunks).thenSucceed();
    }

    private static final class Run {
        private final GameTestHelper helper;
        private final PatternAssemblyFixture fixture;
        private final boolean checkType;
        private final boolean wake;
        private final Map<String, GTRecipe> loaded = new LinkedHashMap<>();
        private final Map<String, IPatternDetails> patterns = new LinkedHashMap<>();
        private final Map<Item, Long> expectedOutputs = new LinkedHashMap<>();
        private String target;
        private long initialEnergy;
        private long expectedEu;
        private GTRecipe observed;
        private boolean observing;
        private long circuitsPushed;

        private Run(GameTestHelper helper, String variant, boolean checkType, boolean wake) {
            this.helper = helper;
            this.checkType = checkType;
            this.wake = wake;
            fixture = new PatternAssemblyFixture(helper, variant + "_me_pattern_assembly");
            fixtures.add(fixture);
            fixture.build();
        }

        private void prepare() {
            fixture.repairMaintenance();
            for (String name : recipes) {
                var recipe = GTRecipeTypes.ASSEMBLER_RECIPES.getRecipe(helper.getLevel().getRecipeManager(), id(name));
                helper.assertTrue(recipe != null, "真实 RecipeManager 缺少配方 " + id(name));
                loaded.put(name, recipe);
                List<GenericStack> inputs = new ArrayList<>();
                inputs.add(new GenericStack(AEItemKey.of(item("common")), 1));
                if (!name.equals("x")) inputs.add(new GenericStack(AEItemKey.of(item("extra")), 1));
                inputs.add(new GenericStack(AEFluidKey.of(Fluids.WATER), 144));
                if (name.startsWith("c")) {
                    inputs.add(new GenericStack(AEItemKey.of(IntCircuitBehaviour.stack(name.equals("c1") ? 1 : 2)), 1));
                }
                ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new),
                        new GenericStack[]{new GenericStack(AEItemKey.of(output(name)), amount(name))});
                fixture.assembly().getTerminalPatternInventory().setItemDirect(recipes.indexOf(name), encoded);
            }
            var available = fixture.assembly().getAvailablePatterns();
            helper.assertTrue(available.size() == 4, "四张真实 AE 加工样板未被发布：" + available.size());
            for (String name : recipes) {
                IPatternDetails pattern = available.stream().filter(p ->
                                p.getPrimaryOutput().what().equals(AEItemKey.of(output(name)))
                                        && p.getPrimaryOutput().amount() == amount(name))
                        .findFirst().orElseThrow();
                patterns.put(name, pattern);
            }
            GTRecipe foreign = GTRecipeTypes.MIXER_RECIPES.getRecipe(helper.getLevel().getRecipeManager(), id("foreign"));
            helper.assertTrue(foreign != null, "缺少跨类型对照配方");
            loaded.put("foreign", foreign);
            helper.assertTrue(fixture.stored() == 0 && fixture.assembly().getBuffer().isEmpty(), "初始电量/产物非零");
        }

        private void push(String name) {
            fixture.assertReady();
            // wake 场景模拟真实故障：宿主不暂停，只是 tick 订阅被丢掉了。
            fixture.controller().getRecipeLogic().setWorkingEnabled(wake);
            assertEmptyInputs();
            fixture.fillEnergy();
            target = name;
            observed = null;
            expectedEu = 0;
            initialEnergy = fixture.stored();
            IPatternDetails pattern = patterns.get(name);
            KeyCounter[] counters = new KeyCounter[pattern.getInputs().length];
            for (int i = 0; i < counters.length; i++) {
                var input = pattern.getInputs()[i];
                var stack = input.getPossibleInputs()[0];
                if (stack.what() instanceof AEItemKey key && IntCircuitBehaviour.isIntegratedCircuit(key.toStack())) {
                    circuitsPushed += stack.amount() * input.getMultiplier();
                }
                counters[i] = new KeyCounter();
                counters[i].add(stack.what(), stack.amount() * input.getMultiplier());
            }
            helper.assertTrue(fixture.assembly().pushPattern(pattern, counters), "真实 AE 推料入口拒收 " + name);
            if (wake) {
                helper.assertTrue(hostTickable(), "推料后宿主仍在休眠（server_tick=false）：唤醒链失效");
            }
            var inventory = fixture.assembly().getMergedInternalSlot();
            helper.assertTrue(inventory.left().getLong(item("common")) == 1
                            && inventory.left().getLong(item("extra")) == (name.equals("x") ? 0 : 1)
                            && inventory.left().getLong(GTItems.INTEGRATED_CIRCUIT.get()) == circuitsPushed
                            && inventory.left().values().longStream().sum() == (name.equals("x") ? 1 : 2) + circuitsPushed
                            && inventory.right().getLong(Fluids.WATER) == 144
                            && inventory.right().values().longStream().sum() == 144, "推料数量不符：" + inventory);
            int slot = recipes.indexOf(name);
            boolean acceptsItems = simulate(loaded.get(name), slot, true);
            boolean acceptsFluids = simulate(loaded.get(name), slot, false);
            helper.assertTrue(acceptsItems && acceptsFluids, "目标配方未通过真实 handler：" + name);
            if (name.equals("y")) {
                helper.assertTrue(!simulate(loaded.get("x"), slot, true) && !simulate(loaded.get("x"), slot, false),
                        "X 子集配方穿过 Y 样板的物品/流体身份闸");
            }
            if (checkType) {
                helper.assertTrue(loaded.get("foreign").recipeType == GTRecipeTypes.MIXER_RECIPES
                                && GTRecipeTypes.ASSEMBLER_RECIPES.getProxyRecipes().isEmpty(), "跨类型对照不再独立");
                boolean itemsAllowed = simulate(loaded.get("foreign"), slot, true);
                boolean fluidsAllowed = simulate(loaded.get("foreign"), slot, false);
                helper.assertTrue(fixture.stored() == initialEnergy && fixture.assembly().getBuffer().isEmpty()
                                && inventory.equals(fixture.assembly().getMergedInternalSlot()), "类型模拟改变了库存/电量");
                helper.assertTrue(!itemsAllowed && !fluidsAllowed, "Assembler 代理表为空，X 样板仍允许 Mixer 同输入同产物候选："
                        + "物品=" + itemsAllowed + " 流体=" + fluidsAllowed);
            }
            if (name.startsWith("c")) {
                helper.assertTrue(IntCircuitBehaviour.getCircuitConfiguration(fixture.assembly().getCircuitForRecipe(slot))
                        == (name.equals("c1") ? 1 : 2), "槽电路读口与样板电路不符：" + name);
                String other = name.equals("c1") ? "c2" : "c1";
                helper.assertTrue(!simulate(loaded.get(other), slot, true), "错误电路通过物品 handler：" + other);
            }
            simulate(loaded.get(name), slot, true);
            simulate(loaded.get(name), slot, false);
            helper.assertTrue(fixture.stored() == initialEnergy
                            && inventory.equals(fixture.assembly().getMergedInternalSlot()), "模拟匹配消耗了材料/能源");
            expectedOutputs.merge(output(name), amount(name), Long::sum);
            observing = true;
            observe();
            if (!wake) {
                // wake 场景不做这一步：宿主只允许靠推料自己的唤醒链恢复。
                fixture.controller().getRecipeLogic().setWorkingEnabled(true);
            }
        }

        private boolean simulate(GTRecipe recipe, int slot, boolean items) {
            var handlers = fixture.assembly().getMERecipeHandlerTraits();
            if (items) {
                var handler = handlers.left();
                handler.initMEHandleContents(recipe,
                        recipe.getInputContents(ItemRecipeCapability.CAP).stream().map(c -> c.content).toList(), true);
                return handler.meHandleRecipe(recipe, true, slot);
            }
            var handler = handlers.right();
            handler.initMEHandleContents(recipe,
                    recipe.getInputContents(FluidRecipeCapability.CAP).stream().map(c -> c.content).toList(), true);
            return handler.meHandleRecipe(recipe, true, slot);
        }

        private void observe() {
            if (!observing) return;
            var logic = fixture.controller().getRecipeLogic();
            if (helper.getTick() % 1000 == 0) {
                LogUtils.getLogger().info("GameTest pattern waiting: {}", diagnostics());
            }
            if (logic.getProgress() > 0) {
                GTRecipe recipe = logic.getLastRecipe();
                helper.assertTrue(recipe != null && recipe.id.equals(id(target)), "控制器执行了其他配方：" + recipe);
                observed = recipe;
                expectedEu = RecipeHelper.getInputEUt(recipe) * recipe.duration;
            }
            helper.runAfterDelay(1, this::observe);
        }

        /** 复现真实故障态：宿主未暂停，但 tick 订阅已被丢掉、方块状态落到 server_tick=false。 */
        private void breakHostTickSubscription() {
            var logic = fixture.controller().getRecipeLogic();
            try {
                Field field = RecipeLogic.class.getDeclaredField("subscription");
                field.setAccessible(true);
                if (field.get(logic) instanceof TickableSubscription subscription) {
                    subscription.unsubscribe();
                }
                field.set(logic, null);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("无法复现宿主退订：RecipeLogic.subscription 读取失败", failure);
            }
        }

        /** 退订后必须真的睡着，否则这个用例什么也没考验到。 */
        private void assertHostAsleep() {
            // 此处 target 尚未赋值，diagnostics() 会取 recipes.indexOf(null) 而抛 NPE。
            helper.assertTrue(!hostTickable(), "宿主退订后仍未休眠，本用例失去意义");
        }

        private boolean hostTickable() {
            return Boolean.TRUE.equals(
                    fixture.controller().getBlockState().getValue(BlockProperties.SERVER_TICK));
        }

        private void assertEmptyInputs() {
            var inventory = fixture.assembly().getMergedInternalSlot();
            helper.assertTrue(inventory.left().getLong(GTItems.INTEGRATED_CIRCUIT.get()) == circuitsPushed
                            && inventory.left().values().longStream().sum() == circuitsPushed
                            && inventory.right().values().longStream().sum() == 0, "输入槽存在剩料 " + inventory);
        }

        private void assertFinished() {
            fixture.assertReady();
            helper.assertTrue(observed != null && fixture.controller().getRecipeLogic().getProgress() == 0,
                    "目标配方尚未完成 " + diagnostics());
            assertEmptyInputs();
            var buffer = fixture.assembly().getBuffer();
            for (String name : List.of("x", "y", "circuit")) {
                Item item = item(name);
                helper.assertTrue(buffer.getLong(AEItemKey.of(item)) == expectedOutputs.getOrDefault(item, 0L),
                        "目标产物缺失或串配方：target=" + target + " outputs=" + buffer);
            }
            helper.assertTrue(buffer.values().longStream().sum() == expectedOutputs.values().stream().mapToLong(Long::longValue).sum(),
                    "输出出现额外物品/流体 " + buffer);
            helper.assertTrue(initialEnergy - fixture.stored() == expectedEu && expectedEu > 0,
                    "实际耗电不等于执行配方总 EU：" + (initialEnergy - fixture.stored()) + " / " + expectedEu);
            observing = false;
        }

        private void log() {
            LogUtils.getLogger().info("GameTest pattern gate: assembly={} recipe={} EU={} duration={} outputs={} remaining={}",
                    fixture.assembly().getDefinition().getId(), observed.id, expectedEu, observed.duration,
                    fixture.assembly().getBuffer(), fixture.assembly().getMergedInternalSlot());
        }

        private String diagnostics() {
            var logic = fixture.controller().getRecipeLogic();
            var status = (IRecipeStatus) logic;
            return fixture.assembly().getDefinition().getId() + " target=" + target + " tick=" + helper.getTick()
                    + " status=" + logic.getStatus() + " progress=" + logic.getProgress() + "/" + logic.getDuration()
                    + " EU=" + fixture.stored() + " recipe=" + logic.getLastRecipe()
                    + " recipeStatus=" + status.getRecipeStatus() + " workingStatus=" + status.getWorkingStatus()
                    + " circuit=" + IntCircuitBehaviour.getCircuitConfiguration(fixture.assembly().getCircuitForRecipe(recipes.indexOf(target)))
                    + " cache=" + cachedIds()
                    + " inputs=" + fixture.assembly().getMergedInternalSlot() + " outputs=" + fixture.assembly().getBuffer();
        }

        private List<ResourceLocation> cachedIds() {
            var cached = fixture.assembly().getMETrait().getSlot2RecipesCache().get(recipes.indexOf(target));
            return cached == null ? List.of() : cached.stream().map(recipe -> recipe.id).sorted().toList();
        }

        private Item item(String name) {
            ResourceLocation id = new ResourceLocation("kubejs", "pattern_gate_" + name);
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id), "测试物品未注册 " + id);
            return ForgeRegistries.ITEMS.getValue(id);
        }
        private Item output(String name) { return item(name.startsWith("c") ? "circuit" : name); }
        private long amount(String name) { return name.equals("c2") ? 2 : 1; }
        private ResourceLocation id(String name) {
            return new ResourceLocation("gtl_gametest", (name.equals("foreign") ? "mixer/" : "assembler/") + "pattern_gate_" + name);
        }
    }
}
