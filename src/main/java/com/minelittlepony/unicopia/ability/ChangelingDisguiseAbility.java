package com.minelittlepony.unicopia.ability;


import org.jetbrains.annotations.Nullable;

import com.minelittlepony.unicopia.EquinePredicates;
import com.minelittlepony.unicopia.USounds;
import com.minelittlepony.unicopia.ability.data.Hit;
import com.minelittlepony.unicopia.ability.magic.spell.AbstractDisguiseSpell;
import com.minelittlepony.unicopia.ability.magic.spell.effect.SpellType;
import com.minelittlepony.unicopia.entity.player.Pony;
import com.minelittlepony.unicopia.mixin.MixinFallingBlockEntity;
import com.minelittlepony.unicopia.particle.UParticles;
import com.minelittlepony.unicopia.util.Trace;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;

/**
 * Changeling ability to disguise themselves as other players.
 */
public class ChangelingDisguiseAbility extends ChangelingFeedAbility {

    @Nullable
    @Override
    public Hit tryActivate(Pony player) {
        if (player.getMaster().isCreative() || player.getMagicalReserves().getMana().getPercentFill() >= 0.9F) {
            return Hit.INSTANCE;
        }
        return null;
    }

    @Override
    public void apply(Pony iplayer, Hit data) {
        PlayerEntity player = iplayer.getMaster();

        if (!player.isCreative() && iplayer.getMagicalReserves().getMana().getPercentFill() < 0.9F) {
            return;
        }

        Trace trace = Trace.create(player, 10, 1, EquinePredicates.VALID_FOR_DISGUISE);

        Entity looked = trace.getEntity().map(AbstractDisguiseSpell::getAppearance).orElseGet(() -> trace.getBlockPos().map(pos -> {
            if (!iplayer.getReferenceWorld().isAir(pos)) {
                return MixinFallingBlockEntity.createInstance(player.getEntityWorld(), 0, 0, 0, iplayer.getReferenceWorld().getBlockState(pos));
            }
            return null;
        }).orElse(null));

        player.getEntityWorld().playSound(null, player.getBlockPos(), USounds.ENTITY_PLAYER_CHANGELING_TRANSFORM, SoundCategory.PLAYERS, 1.4F, 0.4F);

        iplayer.getSpellSlot().get(SpellType.CHANGELING_DISGUISE, true)
            .orElseGet(() -> SpellType.CHANGELING_DISGUISE.withTraits().apply(iplayer))
            .setDisguise(looked);

        if (!player.isCreative()) {
            iplayer.getMagicalReserves().getMana().multiply(0.1F);
        }

        player.calculateDimensions();
        iplayer.setDirty();
    }

    @Override
    public void preApply(Pony player, AbilitySlot slot) {
        player.getMagicalReserves().getEnergy().add(20);
        player.spawnParticles(UParticles.CHANGELING_MAGIC, 5);
    }

    @Override
    public void postApply(Pony player, AbilitySlot slot) {
        player.getMagicalReserves().getEnergy().set(0);
        player.spawnParticles(UParticles.CHANGELING_MAGIC, 5);
    }
}
