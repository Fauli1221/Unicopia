package com.minelittlepony.unicopia.command;

import java.util.function.Function;

import com.minelittlepony.unicopia.entity.player.MagicReserves;
import com.minelittlepony.unicopia.entity.player.Pony;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class ManaCommand {
    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager
                .literal("mana")
                .then(CommandManager.argument("type", EnumArgumentType.of(ManaType.class)).executes(source -> {
                    var type = source.getArgument("type", ManaType.class);
                    var pony = Pony.of(source.getSource().getPlayer());
                    var bar = type.getBar(pony.getMagicalReserves());

                    source.getSource().getPlayer().sendMessage(Text.literal(type.name() + " is " + bar.get() + "/" + bar.getMax()));
                    return 0;
                })
                    .then(CommandManager.argument("value", FloatArgumentType.floatArg()).executes(source -> {
                        var type = source.getArgument("type", ManaType.class);
                        var pony = Pony.of(source.getSource().getPlayer());
                        var bar = type.getBar(pony.getMagicalReserves());

                        bar.set(source.getArgument("value", Float.class));
                        source.getSource().getPlayer().sendMessage(Text.literal("Set " + type.name() + " to " + bar.get() + "/" + bar.getMax()));
                        return 0;
                    }))
                )
        );
    }

    enum ManaType {
        EXERTION(MagicReserves::getExertion),
        EXHAUSTION(MagicReserves::getExhaustion),
        ENERGY(MagicReserves::getEnergy),
        MANA(MagicReserves::getMana),
        XP(MagicReserves::getXp);

        private final Function<MagicReserves, MagicReserves.Bar> getter;

        ManaType(Function<MagicReserves, MagicReserves.Bar> getter) {
            this.getter = getter;
        }

        public MagicReserves.Bar getBar(MagicReserves reserves) {
            return getter.apply(reserves);
        }
    }
}
