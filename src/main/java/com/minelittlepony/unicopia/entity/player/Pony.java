package com.minelittlepony.unicopia.entity.player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import com.minelittlepony.unicopia.Affinity;
import com.minelittlepony.unicopia.client.UnicopiaClient;
import com.minelittlepony.unicopia.InteractionManager;
import com.minelittlepony.unicopia.Race;
import com.minelittlepony.unicopia.UTags;
import com.minelittlepony.unicopia.WorldTribeManager;
import com.minelittlepony.unicopia.ability.AbilityDispatcher;
import com.minelittlepony.unicopia.ability.magic.Affine;
import com.minelittlepony.unicopia.ability.magic.Spell;
import com.minelittlepony.unicopia.ability.magic.spell.SpellType;
import com.minelittlepony.unicopia.advancement.UCriteria;
import com.minelittlepony.unicopia.entity.PonyContainer;
import com.minelittlepony.unicopia.entity.Living;
import com.minelittlepony.unicopia.entity.Trap;
import com.minelittlepony.unicopia.entity.effect.SunBlindnessStatusEffect;
import com.minelittlepony.unicopia.item.UItems;
import com.minelittlepony.unicopia.item.toxin.Toxin;
import com.minelittlepony.unicopia.network.Channel;
import com.minelittlepony.unicopia.network.MsgOtherPlayerCapabilities;
import com.minelittlepony.unicopia.network.MsgRequestSpeciesChange;
import com.minelittlepony.unicopia.network.Transmittable;
import com.minelittlepony.unicopia.util.Copieable;
import com.minelittlepony.unicopia.util.MagicalDamageSource;
import com.minelittlepony.unicopia.util.Tickable;
import com.minelittlepony.common.util.animation.LinearInterpolator;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.minelittlepony.common.util.animation.Interpolator;
import com.mojang.authlib.GameProfile;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class Pony extends Living<PlayerEntity> implements Transmittable, Copieable<Pony> {

    private static final TrackedData<Integer> RACE = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    static final TrackedData<Float> ENERGY = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.FLOAT);
    static final TrackedData<Float> EXHAUSTION = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.FLOAT);
    static final TrackedData<Float> EXERTION = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.FLOAT);
    static final TrackedData<Float> MANA = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.FLOAT);
    static final TrackedData<Float> XP = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final TrackedData<NbtCompound> EFFECT = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.TAG_COMPOUND);

    private final AbilityDispatcher powers = new AbilityDispatcher(this);
    private final PlayerPhysics gravity = new PlayerPhysics(this);
    private final PlayerCharmTracker charms = new PlayerCharmTracker(this);
    private final PlayerAttributes attributes = new PlayerAttributes(this);
    private final PlayerCamera camera = new PlayerCamera(this);
    private final ManaContainer mana;
    private final PlayerLevelStore levels;

    private final List<Tickable> tickers;

    private final Interpolator interpolator = new LinearInterpolator();

    private boolean dirty;
    private boolean speciesSet;
    private boolean speciesPersisted;

    private int ticksHanging;

    private float magicExhaustion = 0;

    @Nullable
    private Race clientPreferredRace;

    private boolean invisible = false;

    private int ticksInSun;
    private boolean hasShades;

    public Pony(PlayerEntity player) {
        super(player, EFFECT);
        this.mana = new ManaContainer(this);
        this.levels = new PlayerLevelStore(this);
        this.tickers = Lists.newArrayList(gravity, mana, attributes, charms);

        player.getDataTracker().startTracking(RACE, Race.HUMAN.ordinal());
    }

    public static void registerAttributes(DefaultAttributeContainer.Builder builder) {
        builder.add(PlayerAttributes.EXTENDED_REACH_DISTANCE);
        builder.add(PlayerAttributes.EXTRA_MINING_SPEED);
        builder.add(PlayerAttributes.ENTITY_GRAVTY_MODIFIER);
    }

    @Override
    public Race getSpecies() {
        if (getMaster() == null) {
            return Race.HUMAN;
        }

        return Race.fromId(getMaster().getDataTracker().get(RACE));
    }

    @Override
    public void setSpecies(Race race) {
        race = race.validate(entity);
        speciesSet = true;
        ticksInSun = 0;
        entity.getDataTracker().set(RACE, race.ordinal());

        gravity.updateFlightState();
        entity.sendAbilitiesUpdate();
    }

    public MagicReserves getMagicalReserves() {
        return mana;
    }

    public PlayerCharmTracker getCharms() {
        return charms;
    }

    @Override
    public LevelStore getLevel() {
        return levels;
    }

    @Override
    public boolean isInvisible() {
        return invisible && getSpellSlot().isPresent();
    }

    public boolean isSpeciesPersisted() {
        return speciesPersisted;
    }

    @Override
    public void setInvisible(boolean invisible) {
        this.invisible = invisible;
    }

    @Override
    public Affinity getAffinity() {
        return getSpecies().getAffinity();
    }

    public void setDirty() {
        dirty = true;
    }

    @Override
    public void sendCapabilities(boolean full) {
        dirty = false;

        if (entity instanceof ServerPlayerEntity) {
            MsgOtherPlayerCapabilities packet = new MsgOtherPlayerCapabilities(full, this);
            Channel.SERVER_PLAYER_CAPABILITIES.send((ServerPlayerEntity)entity, packet);
            Channel.SERVER_OTHER_PLAYER_CAPABILITIES.send(entity.world, packet);
        }
    }

    public AbilityDispatcher getAbilities() {
        return powers;
    }

    @Override
    public PlayerPhysics getPhysics() {
        return gravity;
    }

    public float getExtendedReach() {
        return (float)entity.getAttributeInstance(PlayerAttributes.EXTENDED_REACH_DISTANCE).getValue();
    }

    public float getBlockBreakingSpeed() {
        return (float)entity.getAttributeInstance(PlayerAttributes.EXTRA_MINING_SPEED).getValue();
    }

    public Motion getMotion() {
        return gravity;
    }

    public PlayerCamera getCamera() {
        return camera;
    }

    public Interpolator getInterpolator() {
        return interpolator;
    }

    @Override
    public boolean beforeUpdate() {

        if (!speciesSet && getWorld() instanceof ServerWorld) {
            setSpecies(WorldTribeManager.forWorld((ServerWorld)getWorld()).getDefaultRace());
            setDirty();
        }

        if (isClientPlayer() && !speciesSet) {
            Race race = UnicopiaClient.getPreferredRace();

            if (race != clientPreferredRace) {
                clientPreferredRace = race;

                if (race != getSpecies()) {
                    Channel.CLIENT_REQUEST_SPECIES_CHANGE.send(new MsgRequestSpeciesChange(race));
                }
            }
        }

        if (isClient()) {
            if (entity.hasVehicle() && entity.isSneaking()) {

                Entity ridee = entity.getVehicle();

                if (ridee instanceof Trap) {
                    if (((Trap)ridee).attemptDismount(entity)) {
                        entity.stopRiding();
                    } else {
                        entity.setSneaking(false);
                    }
                } else {
                    entity.stopRiding();

                    if (ridee instanceof ServerPlayerEntity) {
                        ((ServerPlayerEntity)ridee).networkHandler.sendPacket(new EntityPassengersSetS2CPacket(ridee));
                    }
                }
            }
        }

        magicExhaustion = burnFood(magicExhaustion);

        powers.tick();

        return false;
    }

    public boolean isHanging() {
        return entity.getAttributeInstance(PlayerAttributes.ENTITY_GRAVTY_MODIFIER).hasModifier(PlayerAttributes.BAT_HANGING);
    }

    public boolean canHangAt(BlockPos pos) {
        BlockState state = getWorld().getBlockState(pos);

        return state.hasSolidTopSurface(getWorld(), pos, getEntity(), Direction.DOWN);
    }

    private BlockPos getHangingPos() {
        BlockPos pos = getOrigin();
        return new BlockPos(pos.getX(), pos.getY() + entity.getEyeHeight(entity.getPose()) + 2, pos.getZ());
    }

    @Override
    public void tick() {

        if (isHanging()) {
            if (ticksHanging++ > 40) {
                if (entity.getVelocity().horizontalLengthSquared() > 0.01
                        || entity.isSneaking()
                        || !canHangAt(getHangingPos())) {


                    entity.getAttributes().getCustomInstance(PlayerAttributes.ENTITY_GRAVTY_MODIFIER).removeModifier(PlayerAttributes.BAT_HANGING);
                    entity.calculateDimensions();
                }
            }
        } else {
            ticksHanging = 0;
        }

        if (getSpecies() == Race.BAT) {
            if (SunBlindnessStatusEffect.hasSunExposure(entity)) {
                if (ticksInSun < 200) {
                    ticksInSun++;
                }

                if (ticksInSun == 1) {
                    entity.addStatusEffect(new StatusEffectInstance(SunBlindnessStatusEffect.INSTANCE, SunBlindnessStatusEffect.MAX_DURATION * 10, 1, true, false));
                    UCriteria.LOOK_INTO_SUN.trigger(entity);

                    if (isClient() && isClientPlayer()) {
                        InteractionManager.instance().playLoopingSound(entity, InteractionManager.SOUND_EARS_RINGING);
                    }
                }
            } else if (ticksInSun > 0) {
                ticksInSun--;
            }

            boolean hasShades = entity.getEquippedStack(EquipmentSlot.HEAD).isIn(UTags.SHADES);
            if (!this.hasShades && hasShades) {
                UCriteria.WEAR_SHADES.trigger(entity);
            }
            this.hasShades = hasShades;
        }

        tickers.forEach(Tickable::tick);

        super.tick();

        if (dirty) {
            sendCapabilities(true);
        }
    }

    public Optional<Float> onImpact(float distance, float damageMultiplier, DamageSource cause) {

        float g = gravity.getGravityModifier();

        boolean extraProtection = getSpellSlot().get(SpellType.SHIELD, false).isPresent();

        if (g != 1 || extraProtection || getSpecies().canFly() && !entity.isCreative() && !entity.isSpectator()) {

            if (extraProtection) {
                distance /= (getLevel().get() + 1);
                if (entity.isSneaking()) {
                    distance /= 2;
                }
            }

            distance = Math.max(0, (distance * g) - 5);

            handleFall(distance, damageMultiplier, cause);
            return Optional.of(distance);
        }

        handleFall(distance, damageMultiplier, cause);
        return Optional.empty();
    }

    @Override
    public boolean subtractEnergyCost(double foodSubtract) {
        if (!entity.isCreative() && !entity.world.isClient) {

            float currentMana = mana.getMana().get();
            float foodManaRatio = 10;

            if (currentMana >= foodSubtract * foodManaRatio) {
                mana.getMana().set(currentMana - (float)foodSubtract * foodManaRatio);
            } else {
                mana.getMana().set(0);
                foodSubtract -= currentMana / foodManaRatio;

                magicExhaustion += foodSubtract;
            }
        }

        return entity.getHealth() > 0;
    }

    private float burnFood(float foodSubtract) {
        int lostLevels = (int)Math.floor(foodSubtract);
        if (lostLevels > 0) {
            int food = entity.getHungerManager().getFoodLevel() - lostLevels;

            if (food < 0) {
                entity.getHungerManager().add(-entity.getHungerManager().getFoodLevel(), 0);
                entity.damage(MagicalDamageSource.EXHAUSTION, -food/2);
            } else {
                entity.getHungerManager().add(-lostLevels, 0);
            }
        }

        return foodSubtract - lostLevels;
    }

    @Override
    protected Stream<ItemStack> getInventoryStacks() {
        return Streams.concat(
                super.getInventoryStacks(),
                entity.getInventory().main.stream()
        );
    }

    @Override
    protected void giveBackItem(ItemStack stack) {
        if (!entity.giveItemStack(stack)) {
            entity.dropItem(stack, false);
        }
    }

    public Optional<Text> trySleep(BlockPos pos) {

        if (UItems.ALICORN_AMULET.isApplicable(entity)) {
            return Optional.of(new TranslatableText("block.unicopia.bed.not_tired"));
        }

        return findAllSpellsInRange(10)
                .filter(p -> p instanceof Pony && ((Pony)p).isEnemy(this))
                .findFirst()
                .map(p -> new TranslatableText("block.unicopia.bed.not_safe"));
    }

    @Override
    public boolean isEnemy(Affine other) {
        return getCharms().getArmour().contains(UItems.ALICORN_AMULET) || super.isEnemy(other);
    }

    public void onEat(ItemStack stack) {
        if (getSpecies() == Race.CHANGELING) {
            Toxin.LOVE_SICKNESS.afflict(getMaster(), stack);
        }
    }

    @Override
    public void toNBT(NbtCompound compound) {
        super.toNBT(compound);
        compound.putString("playerSpecies", getSpecies().name());

        compound.putFloat("magicExhaustion", magicExhaustion);

        compound.put("powers", powers.toNBT());
        compound.put("gravity", gravity.toNBT());
        compound.put("charms", charms.toNBT());

        getSpellSlot().get(true).ifPresent(effect ->{
            compound.put("effect", SpellType.toNBT(effect));
        });
    }

    @Override
    public void fromNBT(NbtCompound compound) {
        super.fromNBT(compound);
        speciesPersisted = true;
        setSpecies(Race.fromName(compound.getString("playerSpecies")));

        powers.fromNBT(compound.getCompound("powers"));
        gravity.fromNBT(compound.getCompound("gravity"));
        charms.fromNBT(compound.getCompound("charms"));

        magicExhaustion = compound.getFloat("magicExhaustion");

        if (compound.contains("effect")) {
            getSpellSlot().put(SpellType.fromNBT(compound.getCompound("effect")));
        }
    }

    @Override
    public void copyFrom(Pony oldPlayer) {
        speciesPersisted = oldPlayer.speciesPersisted;
        if (!oldPlayer.getEntity().isRemoved()) {
            setSpell(oldPlayer.getSpellSlot().get(true).orElse(null));
        }
        oldPlayer.setSpell(null);
        setSpecies(oldPlayer.getSpecies());
        setDirty();
    }

    @Override
    public void setSpell(@Nullable Spell effect) {
        super.setSpell(effect);
        setDirty();
    }

    public boolean isClientPlayer() {
        return InteractionManager.instance().isClientPlayer(getMaster());
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static Pony of(@Nullable PlayerEntity player) {
        return player == null ? null : ((PonyContainer<Pony>)player).get();
    }

    public static boolean equal(GameProfile one, GameProfile two) {
        return one == two || (one != null && two != null && one.getId().equals(two.getId()));
    }

    public static boolean equal(Entity one, Entity two) {
        return Objects.equals(one, two) || (one instanceof PlayerEntity && two instanceof PlayerEntity && equal((PlayerEntity)one, (PlayerEntity)two));
    }

    public static boolean equal(PlayerEntity one, PlayerEntity two) {
        return one == two || (one != null && two != null && equal(one.getGameProfile(), two.getGameProfile()));
    }
}
