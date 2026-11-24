package com.miniworld.miniworld.command;

import com.miniworld.api.MiniWorldApi;
import com.miniworld.miniworld.world.MiniWorldRecord;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /miniworld 命令入口。 */
public final class MiniWorldCommands {
    private MiniWorldCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("miniworld")
                .then(Commands.literal("enter")
                        .executes(MiniWorldCommands::enterSelf)
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(MiniWorldCommands::enterTarget)))
                .then(Commands.literal("return")
                        .executes(MiniWorldCommands::returnSelf))
                .then(Commands.literal("info")
                        .executes(MiniWorldCommands::infoSelf)
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(MiniWorldCommands::infoTarget))));
    }

    private static int enterSelf(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        if (!MiniWorldApi.enter(player)) {
            context.getSource().sendFailure(Component.literal("进入个人小世界失败。"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("已进入你的个人小世界。"), false);
        return 1;
    }

    private static int enterTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (!MiniWorldApi.enter(target, target.getUUID(), true)) {
            context.getSource().sendFailure(Component.literal("进入目标玩家小世界失败。"));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.literal("已将 " + target.getGameProfile().getName() + " 传送到其个人小世界。"),
                true
        );
        return 1;
    }

    private static int returnSelf(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        MiniWorldApi.returnToOverworld(player);
        context.getSource().sendSuccess(() -> Component.literal("已返回主世界。"), false);
        return 1;
    }

    private static int infoSelf(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        return player == null ? 0 : sendInfo(context, player);
    }

    private static int infoTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return sendInfo(context, EntityArgument.getPlayer(context, "player"));
    }

    private static int sendInfo(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        String id = MiniWorldApi.getDimensionId(player.server, player.getUUID());
        if (id == null) {
            context.getSource().sendFailure(Component.literal("该玩家还没有个人小世界记录。"));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.literal(player.getGameProfile().getName() + " 的个人维度：" + id),
                false
        );
        return 1;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            return player;
        }
        context.getSource().sendFailure(Component.literal("该命令只能由玩家执行。"));
        return null;
    }
}
