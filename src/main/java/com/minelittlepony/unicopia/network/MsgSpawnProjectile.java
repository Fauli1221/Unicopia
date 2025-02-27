package com.minelittlepony.unicopia.network;

import java.util.Optional;

import com.minelittlepony.unicopia.InteractionManager;
import com.minelittlepony.unicopia.Owned;
import com.minelittlepony.unicopia.util.network.Packet;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;

/**
 * Sent by the server to spawn a projectile entity on the client.
 */
public class MsgSpawnProjectile extends EntitySpawnS2CPacket implements Packet<PlayerEntity> {

    MsgSpawnProjectile(PacketByteBuf buffer) {
        super(buffer);
    }

    @SuppressWarnings("unchecked")
    public MsgSpawnProjectile(Entity e) {
        super(e, Optional.ofNullable(e instanceof Owned ? ((Owned<Entity>)e).getMaster() : null).map(Entity::getId).orElse(0));
    }

    @Override
    public void toBuffer(PacketByteBuf buffer) {
        write(buffer);
    }


    @Override
    public void handle(PlayerEntity sender) {
        InteractionManager.instance().getClientNetworkHandler().handleSpawnProjectile(this);
    }
}
