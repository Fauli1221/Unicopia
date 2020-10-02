package com.minelittlepony.unicopia.ability.magic.spell;

import com.minelittlepony.unicopia.Affinity;
import com.minelittlepony.unicopia.EquinePredicates;
import com.minelittlepony.unicopia.ability.magic.Caster;
import com.minelittlepony.unicopia.ability.magic.Magical;
import com.minelittlepony.unicopia.block.state.StateMaps;
import com.minelittlepony.unicopia.util.MagicalDamageSource;
import com.minelittlepony.unicopia.util.PosHelper;
import com.minelittlepony.unicopia.util.VecHelper;
import com.minelittlepony.unicopia.util.shape.Shape;
import com.minelittlepony.unicopia.util.shape.Sphere;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Material;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Simple fire spell that triggers an effect when used on a block.
 */
public class FireSpell extends AbstractRangedAreaSpell {

    private static final Shape VISUAL_EFFECT_RANGE = new Sphere(false, 0.5);
    private static final Shape EFFECT_RANGE = new Sphere(false, 4);

    @Override
    public String getName() {
        return "fire";
    }

    @Override
    public Affinity getAffinity() {
        return Affinity.GOOD;
    }

    @Override
    public int getTint() {
        return 0xFF5D00;
    }

    @Override
    public boolean update(Caster<?> source) {
        return PosHelper.getAllInRegionMutable(source.getOrigin(), EFFECT_RANGE).reduce(false,
                (r, i) -> applyBlocks(source.getWorld(), i),
                (a, b) -> a || b)
                || applyEntities(null, source.getWorld(), source.getOrigin());
    }

    @Override
    public void render(Caster<?> source) {
        source.spawnParticles(VISUAL_EFFECT_RANGE, source.getLevel().get() * 6, pos -> {
            source.addParticle(ParticleTypes.LARGE_SMOKE, pos, Vec3d.ZERO);
        });
    }

    protected boolean applyBlocks(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Block id = state.getBlock();

        if (id != Blocks.AIR) {
            if (id == Blocks.ICE || id == Blocks.PACKED_ICE) {
                world.setBlockState(pos, (world.getDimension().isUltrawarm() ? Blocks.AIR : Blocks.WATER).getDefaultState());
                playEffect(world, pos);

                return true;
            } else if (id == Blocks.NETHERRACK) {
                if (world.getBlockState(pos.up()).getMaterial() == Material.AIR) {

                    if (world.random.nextInt(300) == 0) {
                        world.setBlockState(pos.up(), Blocks.FIRE.getDefaultState());
                    }

                    return true;
                }
            } else if (id == Blocks.REDSTONE_WIRE) {
                int power = world.random.nextInt(5) == 3 ? 15 : 3;

                sendPower(world, pos, power, 3, 0);

                return true;
            } else if (state.isIn(BlockTags.SAND) && world.random.nextInt(10) == 0) {
                if (isSurroundedBySand(world, pos)) {
                    world.setBlockState(pos, Blocks.GLASS.getDefaultState());

                    playEffect(world, pos);
                    return true;
                }
            } else if (state.isIn(BlockTags.LEAVES)) {
                if (world.getBlockState(pos.up()).getMaterial() == Material.AIR) {
                    world.setBlockState(pos.up(), Blocks.FIRE.getDefaultState());

                    playEffect(world, pos);
                    return true;
                }
            } else {
                BlockState newState = StateMaps.FIRE_AFFECTED.getConverted(state);

                if (!state.equals(newState)) {
                    world.setBlockState(pos, newState, 3);

                    playEffect(world, pos);
                    return true;
                }
            }
        }

        return false;
    }

    protected boolean applyEntities(Entity owner, World world, BlockPos pos) {
        return VecHelper
                .findAllEntitiesInRange(owner, world, pos, 3)
                .filter(i -> applyEntitySingle(owner, world, i))
                .count() > 0;
    }

    protected boolean applyEntitySingle(Entity owner, World world, Entity e) {
        if ((!e.equals(owner) ||
                (owner instanceof PlayerEntity && !EquinePredicates.PLAYER_UNICORN.test(owner))) && !(e instanceof ItemEntity)
        && !(e instanceof Magical)) {
            e.setOnFireFor(60);
            e.damage(getDamageCause(e, (LivingEntity)owner), 0.1f);
            playEffect(world, e.getBlockPos());
            return true;
        }

        return false;
    }

    protected DamageSource getDamageCause(Entity target, LivingEntity attacker) {
        return MagicalDamageSource.create("fire", attacker);
    }

    /**
     * Transmits power to a piece of redstone
     */
    private void sendPower(World w, BlockPos pos, int power, int max, int i) {
        BlockState state = w.getBlockState(pos);
        Block id = state.getBlock();

        if (i < max && id == Blocks.REDSTONE_WIRE) {
            i++;

            w.setBlockState(pos, state.with(RedstoneWireBlock.POWER, power));

            sendPower(w, pos.up(), power, max, i);
            sendPower(w, pos.down(), power, max, i);
            sendPower(w, pos.north(), power, max, i);
            sendPower(w, pos.south(), power, max, i);
            sendPower(w, pos.east(), power, max, i);
            sendPower(w, pos.west(), power, max, i);
        }
    }

    protected void playEffect(World world, BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        world.playSound(x + 0.5F, y + 0.5F, z + 0.5F, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.AMBIENT, 0.5F, 2.6F + (world.random.nextFloat() - world.random.nextFloat()) * 0.8F, true);

        for (int i = 0; i < 8; ++i) {
            world.addParticle(ParticleTypes.LARGE_SMOKE, x + Math.random(), y + Math.random(), z + Math.random(), 0, 0, 0);
        }
    }

    public static boolean isSurroundedBySand(World w, BlockPos pos) {
        return isSand(w, pos.up()) && isSand(w, pos.down()) &&
                isSand(w, pos.north()) && isSand(w, pos.south()) &&
                isSand(w, pos.east()) && isSand(w, pos.west());
    }

    public static boolean isSand(World world, BlockPos pos) {
        Block id = world.getBlockState(pos).getBlock();
        return id == Blocks.SAND || id == Blocks.GLASS;
    }
}
