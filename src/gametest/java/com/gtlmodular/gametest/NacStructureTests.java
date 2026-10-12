package com.gtlmodular.gametest;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.gametest.fixture.NacStructureFixture;
import com.gtlmodular.gametest.fixture.NacStructureLayout;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class NacStructureTests {

    private static final List<NacStructureFixture> fixtures = new ArrayList<>();

    @AfterBatch(batch = "nac_structure")
    public static void cleanup(ServerLevel level) {
        // 失败的用例也要释放区块，避免影响后续批次。
        fixtures.forEach(NacStructureFixture::releaseChunks);
        fixtures.clear();
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("nac_structure", NanochipModContext.modId() + ".nac_structure_lifecycle",
                NacStructureLayout.templateId().toString(), 6000, 0, true, NacStructureTests::lifecycle));
    }

    private static void lifecycle(GameTestHelper helper) {
        NacStructureFixture fixture = new NacStructureFixture(helper);
        fixtures.add(fixture);
        fixture.build();
        helper.startSequence()
                .thenWaitUntil(fixture::assertConnected)
                .thenExecute(() -> fixture.log("connected"))
                .thenExecute(fixture::breakModule)
                .thenWaitUntil(fixture::assertModuleDisconnected)
                .thenExecute(() -> fixture.log("module disconnected"))
                .thenExecute(fixture::restore)
                .thenWaitUntil(fixture::assertConnected)
                .thenExecute(() -> fixture.log("module recovered"))
                .thenExecute(fixture::breakHost)
                .thenWaitUntil(fixture::assertHostDisconnected)
                .thenExecuteFor(120, fixture::assertHostDisconnected)
                .thenExecute(() -> fixture.log("host disconnected"))
                .thenExecute(fixture::restore)
                .thenWaitUntil(fixture::assertConnected)
                .thenExecute(() -> fixture.log("host recovered"))
                .thenExecute(fixture::releaseChunks)
                .thenSucceed();
    }
}
