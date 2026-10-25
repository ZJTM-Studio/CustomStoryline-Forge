package com.yang458.customstoryline.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.yang458.customstoryline.data.StorylineData;
import com.yang458.customstoryline.event.StorylineEvents;
import com.yang458.customstoryline.manager.StorylineManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber
public class StorylineCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("storyline")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("new")
                        .then(Commands.argument("namespace", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    return createStoryline(ctx.getSource(), StringArgumentType.getString(ctx, "namespace"), player.blockPosition(), null);
                                })
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> createStoryline(ctx.getSource(), StringArgumentType.getString(ctx, "namespace"), BlockPosArgument.getBlockPos(ctx, "pos"), null))
                                        .then(Commands.argument("topos", BlockPosArgument.blockPos())
                                                .executes(ctx -> createStoryline(ctx.getSource(), StringArgumentType.getString(ctx, "namespace"), BlockPosArgument.getBlockPos(ctx, "pos"), BlockPosArgument.getBlockPos(ctx, "topos")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("test")
                        .then(Commands.argument("namespace", StringArgumentType.word())
                                .executes(ctx -> {
                                    String ns = StringArgumentType.getString(ctx, "namespace");
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    StorylineData data = StorylineManager.getStorylines().get(ns);
                                    if (data == null) {
                                        ctx.getSource().sendFailure(Component.translatable("command.customstoryline.test.not_found", ns));
                                        return 0;
                                    }
                                    StorylineEvents.triggerStoryline(player, ns, data);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.customstoryline.test.success", ns), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("unlock")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    StorylineEvents.LOCKED_PLAYERS.remove(target.getUUID());
                                    target.setInvulnerable(false);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.customstoryline.unlock.success", target.getName().getString()), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            List<String> errors = StorylineManager.loadAll(ctx.getSource().getServer());
                            if (errors.isEmpty()) {
                                ctx.getSource().sendSuccess(() -> Component.translatable("command.customstoryline.reload.success"), true);
                            } else {
                                ctx.getSource().sendFailure(Component.translatable("command.customstoryline.reload.error.header", errors.size()));
                                for (String err : errors) ctx.getSource().sendFailure(Component.translatable("command.customstoryline.reload.error.line", err));
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("remove")
                        .then(Commands.argument("namespace", StringArgumentType.word())
                                .executes(ctx -> {
                                    String ns = StringArgumentType.getString(ctx, "namespace");
                                    StorylineManager.removeStoryline(ctx.getSource().getServer(), ns);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.customstoryline.remove.success", ns), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    for (String ns : StorylineManager.getStorylines().keySet()) StorylineManager.setPlayerTriggered(target.getUUID(), ns, false);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.customstoryline.reset.success", target.getName().getString()), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("manage")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("namespace", StringArgumentType.word())
                                        .then(Commands.literal("set").executes(ctx -> manageState(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "namespace"), true)))
                                        .then(Commands.literal("reset").executes(ctx -> manageState(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "namespace"), false)))
                                )
                        )
                )
        );
    }

    private static int createStoryline(CommandSourceStack source, String ns, BlockPos pos1, BlockPos pos2) {
        StorylineData data = new StorylineData();
        data.trigger_position.add((double) pos1.getX());
        data.trigger_position.add((double) pos1.getY());
        data.trigger_position.add((double) pos1.getZ());
        if (pos2 != null) {
            data.trigger_position.add((double) pos2.getX());
            data.trigger_position.add((double) pos2.getY());
            data.trigger_position.add((double) pos2.getZ());
        }
        StorylineManager.saveStoryline(source.getServer(), ns, data);
        source.sendSuccess(() -> Component.translatable("command.customstoryline.new.success", ns), true);
        return 1;
    }

    private static int manageState(CommandSourceStack source, ServerPlayer player, String ns, boolean triggered) {
        StorylineManager.setPlayerTriggered(player.getUUID(), ns, triggered);
        String key = triggered ? "command.customstoryline.manage.set" : "command.customstoryline.manage.reset";
        source.sendSuccess(() -> Component.translatable(key, player.getName().getString(), ns), true);
        return 1;
    }
}