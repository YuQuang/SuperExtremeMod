package com.example.command;
import static net.minecraft.server.command.CommandManager.argument;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class WatchPlayer {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("watch")
            .executes(context -> {
                return 1;
            })
            .then(argument("target", EntityArgumentType.player())
                .executes(context -> {
                    return 1;
                })
            )
        );
    }
}