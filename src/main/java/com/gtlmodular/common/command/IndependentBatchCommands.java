package com.gtlmodular.common.command;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtlmodular.common.machine.independent.control.IndependentLogicAccess;
import com.gtlmodular.common.machine.independent.control.IndependentMachineController;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class IndependentBatchCommands {
    private IndependentBatchCommands() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(IndependentBatchCommands::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("gtlmodular_threads").requires(source -> source.hasPermission(2))
                .then(argument("pos", BlockPosArgument.blockPos())
                        .then(literal("status").executes(context -> {
                            for (var line : controller(context).recoveryStatus()) {
                                context.getSource().sendSuccess(() -> line, false);
                            }
                            return 1;
                        }))
                        .then(literal("retry").then(argument("token", UuidArgument.uuid())
                                .then(literal("confirm_remainders").executes(context -> recover(context, false)))))
                        .then(literal("settle").then(argument("token", UuidArgument.uuid())
                                .then(literal("confirm_settled").executes(context -> recover(context, true)))))));
    }

    private static IndependentMachineController controller(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        var controller = IndependentLogicAccess.controller(MetaMachine.getMachine(context.getSource().getLevel(), pos));
        if (controller == null || controller.detached()) {
            throw new SimpleCommandExceptionType(Component.literal("该位置没有可管理的独立批次会话")).create();
        }
        return controller;
    }

    private static int recover(CommandContext<CommandSourceStack> context, boolean settled) throws CommandSyntaxException {
        var controller = controller(context);
        try {
            controller.recover(UuidArgument.getUuid(context, "token"), settled, context.getSource().getTextName());
        } catch (RuntimeException exception) {
            context.getSource().sendFailure(Component.literal("恢复未完成：" + exception.getMessage()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(settled
                ? "已确认外部结清并移除记录；未返料或补发产物。" : "已解除该批次的故障锁定，将按保存的余量重试。"), true);
        return 1;
    }
}
