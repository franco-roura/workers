package com.talhanation.workers.entities;

import com.talhanation.workers.Main;
import com.talhanation.workers.entities.ai.EatGoal;
import com.talhanation.workers.entities.ai.SleepGoal;
import com.talhanation.workers.entities.ai.WorkerFollowOwnerGoal;
import com.talhanation.workers.entities.ai.WorkerMoveToHomeGoal;
import com.talhanation.workers.inventory.WorkerHireContainer;
import com.talhanation.workers.network.MessageHireGui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class AbstractWorkerEntity extends AbstractChunkLoaderEntity {
    private static final EntityDataAccessor<Optional<BlockPos>> START_POS =
            SynchedEntityData.defineId(AbstractWorkerEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DEST_POS =
            SynchedEntityData.defineId(AbstractWorkerEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> HOME =
            SynchedEntityData.defineId(AbstractWorkerEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> FOLLOW =
            SynchedEntityData.defineId(AbstractWorkerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> IS_WORKING =
            SynchedEntityData.defineId(AbstractWorkerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_PICKING_UP =
            SynchedEntityData.defineId(AbstractWorkerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> breakingTime =
            SynchedEntityData.defineId(AbstractWorkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> currentTimeBreak =
            SynchedEntityData.defineId(AbstractWorkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> previousTimeBreak =
            SynchedEntityData.defineId(AbstractWorkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HUNGER =
            SynchedEntityData.defineId(AbstractWorkerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_EATING =
            SynchedEntityData.defineId(AbstractWorkerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> OWNER_NAME =
            SynchedEntityData.defineId(AbstractWorkerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> PROFESSION_NAME =
            SynchedEntityData.defineId(AbstractWorkerEntity.class, EntityDataSerializers.STRING);
    int hurtTimeStamp = 0;

    public AbstractWorkerEntity(EntityType<? extends AbstractWorkerEntity> entityType, Level world) {
        super(entityType, world);
        this.setOwned(false);
        this.xpReward = 2;
    }

    /*
     * @Override
     * 
     * @NotNull protected PathNavigation createNavigation(@NotNull Level level) { if(this.getIsWorking()
     * && this.shouldDirectNavigation() && !this.getIsPickingUp() && !this.getFollow()){ return new
     * DirectPathNavigation(this, level); } else return new GroundPathNavigation(this, level); }
     */
    @Override
    @NotNull
    protected PathNavigation createNavigation(@NotNull Level level) {
        return new GroundPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new EatGoal(this));
        this.goalSelector.addGoal(0, new SleepGoal(this));
        this.goalSelector.addGoal(0, new OpenDoorGoal(this, true));
        // this.goalSelector.addGoal(1, new TransferItemsInChestGoal(this));
        this.goalSelector.addGoal(1, new WorkerMoveToHomeGoal<>(this, 6.0F));
        this.goalSelector.addGoal(2, new WorkerFollowOwnerGoal(this, 1.2D, 5.0F, 1.0F));
    }

    /////////////////////////////////// TICK/////////////////////////////////////////

    public double getMyRidingOffset() {
        return -0.35D;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.level.getProfiler().push("looting");
        if (!this.level.isClientSide && this.canPickUpLoot() && this.isAlive() && !this.dead
                && net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(this.level, this)) {
            for (ItemEntity itementity : this.level.getEntitiesOfClass(ItemEntity.class,
                    this.getBoundingBox().inflate(2.5D, 2.5D, 2.5D))) {
                if (!itementity.isRemoved() && !itementity.getItem().isEmpty() && !itementity.hasPickUpDelay()
                        && this.wantsToPickUp(itementity.getItem())) {
                    this.pickUpItem(itementity);
                }
            }
        }
    }

    public void tick() {
        super.tick();
        updateSwingTime();
        updateSwimming();
        updateHunger();

        if (hurtTimeStamp > 0)
            hurtTimeStamp--;
    }

    public void rideTick() {
        super.rideTick();
        if (this.getVehicle() instanceof PathfinderMob creatureentity) {
            this.yBodyRot = creatureentity.yBodyRot;
        }

    }

    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance diff, MobSpawnType reason,
            @Nullable SpawnGroupData spawnData, @Nullable CompoundTag nbt) {
        setRandomSpawnBonus();
        canPickUpLoot();
        return spawnData;
    }

    public void setRandomSpawnBonus() {
        getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier("heath_bonus",
                this.random.nextGaussian() * 0.10D, AttributeModifier.Operation.MULTIPLY_BASE));
        getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(new AttributeModifier("speed_bonus",
                this.random.nextGaussian() * 0.10D, AttributeModifier.Operation.MULTIPLY_BASE));

    }

    public void setDropEquipment() {
        this.dropEquipment();
    }

    //////////////////////////////////// REGISTER////////////////////////////////////

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(HOME, Optional.empty());
        this.entityData.define(START_POS, Optional.empty());
        this.entityData.define(DEST_POS, Optional.empty());
        this.entityData.define(IS_WORKING, false);
        this.entityData.define(IS_PICKING_UP, false);
        this.entityData.define(FOLLOW, false);
        this.entityData.define(IS_EATING, false);
        this.entityData.define(breakingTime, 0);
        this.entityData.define(currentTimeBreak, -1);
        this.entityData.define(previousTimeBreak, -1);
        this.entityData.define(HUNGER, 50F);
        this.entityData.define(OWNER_NAME, "");
        this.entityData.define(PROFESSION_NAME, "");
    }

    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("Follow", this.getFollow());
        nbt.putBoolean("isWorking", this.getIsWorking());
        nbt.putBoolean("isPickingUp", this.getIsPickingUp());
        nbt.putInt("breakTime", this.getBreakingTime());
        nbt.putInt("currentTimeBreak", this.getCurrentTimeBreak());
        nbt.putInt("previousTimeBreak", this.getPreviousTimeBreak());
        nbt.putString("OwnerName", this.getOwnerName());
        nbt.putFloat("Hunger", this.getHunger());
        nbt.putString("ProfessionName", this.getProfessionName());

        if (this.getStartPos() != null) {
            nbt.putInt("StartPosX", this.getStartPos().getX());
            nbt.putInt("StartPosY", this.getStartPos().getY());
            nbt.putInt("StartPosZ", this.getStartPos().getZ());
        }

        if (this.getDestPos() != null) {
            nbt.putInt("DestPosX", this.getDestPos().getX());
            nbt.putInt("DestPosY", this.getDestPos().getY());
            nbt.putInt("DestPosZ", this.getDestPos().getZ());
        }

        if (this.getHomePos() != null) {
            nbt.putInt("HomePosX", this.getHomePos().getX());
            nbt.putInt("HomePosY", this.getHomePos().getY());
            nbt.putInt("HomePosZ", this.getHomePos().getZ());
        }
    }

    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setFollow(nbt.getBoolean("Follow"));
        this.setBreakingTime(nbt.getInt("breakTime"));
        this.setIsPickingUp(nbt.getBoolean("isPickingUp"));
        this.setCurrentTimeBreak(nbt.getInt("currentTimeBreak"));
        this.setPreviousTimeBreak(nbt.getInt("previousTimeBreak"));
        this.setIsWorking(nbt.getBoolean("isWorking"));
        this.setHunger(nbt.getFloat("Hunger"));
        this.setOwnerName(nbt.getString("OwnerName"));
        this.setProfessionName(nbt.getString("ProfessionName"));

        if (nbt.contains("StartPosX") && nbt.contains("StartPosY") && nbt.contains("StartPosZ")) {
            BlockPos blockpos = new BlockPos(nbt.getInt("StartPosX"), nbt.getInt("StartPosY"), nbt.getInt("StartPosZ"));
            this.setStartPos(blockpos);
        }

        if (nbt.contains("DestPosX") && nbt.contains("DestPosY") && nbt.contains("DestPosZ")) {
            BlockPos blockpos = new BlockPos(nbt.getInt("DestPosX"), nbt.getInt("DestPosY"), nbt.getInt("DestPosZ"));
            this.setDestPos(blockpos);
        }

        if (nbt.contains("HomePosX") && nbt.contains("HomePosY") && nbt.contains("HomePosZ")) {
            BlockPos blockpos = new BlockPos(nbt.getInt("HomePosX"), nbt.getInt("HomePosY"), nbt.getInt("HomePosZ"));
            this.setHomePos(blockpos);
        }

    }

    //////////////////////////////////// GET////////////////////////////////////

    public String getProfessionName() {
        return entityData.get(PROFESSION_NAME);
    }

    public String getOwnerName() {
        return entityData.get(OWNER_NAME);
    }

    public BlockPos getHomePos() {
        return entityData.get(HOME).orElse(null);
    }

    public int getCurrentTimeBreak() {
        return this.entityData.get(currentTimeBreak);
    }

    public int getPreviousTimeBreak() {
        return this.entityData.get(previousTimeBreak);
    }

    public int getBreakingTime() {
        return this.entityData.get(breakingTime);
    }

    public float getHunger() {
        return this.entityData.get(HUNGER);
    }

    public BlockPos getWorkerOnPos() {
        return this.getOnPos();
    }

    public BlockPos getDestPos() {
        return this.entityData.get(DEST_POS).orElse(null);
    }

    public BlockPos getStartPos() {
        return this.entityData.get(START_POS).orElse(null);
    }

    public boolean getFollow() {
        return this.entityData.get(FOLLOW);
    }

    public boolean getIsWorking() {
        return this.entityData.get(IS_WORKING);
    }

    public boolean getIsEating() {
        return this.entityData.get(IS_EATING);
    }

    public boolean getIsPickingUp() {
        return this.entityData.get(IS_PICKING_UP);
    }

    public SoundEvent getHurtSound(DamageSource ds) {
        return SoundEvents.VILLAGER_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    protected float getSoundVolume() {
        return 0.4F;
    }

    protected float getStandingEyeHeight(Pose pos, EntityDimensions size) {
        return size.height * 0.9F;
    }

    public int getMaxHeadXRot() {
        return this.isInSittingPose() ? 20 : super.getMaxHeadXRot();
    }

    //////////////////////////////////// SET////////////////////////////////////

    public void setProfessionName(String string) {
        this.entityData.set(PROFESSION_NAME, string);
    }

    public void setOwnerName(String string) {
        this.entityData.set(OWNER_NAME, string);
    }

    public void setHomePos(BlockPos pos) {
        this.entityData.set(HOME, Optional.of(pos));

        LivingEntity owner = this.getOwner();
        if (owner != null)
            owner.sendSystemMessage(Component.literal(this.getName().getString() + ": " + TEXT_HOME.getString()));
    }

    public void setPreviousTimeBreak(int value) {
        this.entityData.set(previousTimeBreak, value);
    }

    public void setCurrentTimeBreak(int value) {
        this.entityData.set(currentTimeBreak, value);
    }

    public void setBreakingTime(int value) {
        this.entityData.set(breakingTime, value);
    }

    public void setHunger(float value) {
        this.entityData.set(HUNGER, value);
    }

    public void setDestPos(BlockPos pos) {
        this.entityData.set(DEST_POS, Optional.of(pos));
    }

    public void setStartPos(BlockPos pos) {
        this.entityData.set(START_POS, Optional.of(pos));
    }

    public void clearStartPos() {
        this.entityData.set(START_POS, Optional.empty());
    }

    public void setFollow(boolean bool) {
        String name = this.getName().getString() + ": ";
        if (getFollow() == bool)
            return;
        this.entityData.set(FOLLOW, bool);

        LivingEntity owner = this.getOwner();
        if (owner == null)
            return;

        if (bool) {
            owner.sendSystemMessage(Component.literal(name + TEXT_FOLLOW.getString()));
        } else if (this.getIsWorking())
            owner.sendSystemMessage(Component.literal(name + TEXT_CONTINUE.getString()));
        else
            owner.sendSystemMessage(Component.literal(name + TEXT_WANDER.getString()));
    }

    public void setIsWorking(boolean bool) {
        String name = this.getName().getString() + ": ";
        LivingEntity owner = this.getOwner();

        if (getIsWorking() != bool) {
            if (owner != null) {
                if (!isStarving()) {
                    if (bool) {
                        owner.sendSystemMessage(Component.literal(name + TEXT_WORKING.getString()));
                    } else
                        owner.sendSystemMessage(Component.literal(name + TEXT_DONE.getString()));
                } else if (isStarving()) {
                    owner.sendSystemMessage(Component.literal(name + TEXT_STARVING.getString()));
                    entityData.set(IS_WORKING, false);
                }
            }
            entityData.set(IS_WORKING, bool);
        }
    }

    public void setIsPickingUp(boolean bool) {
        entityData.set(IS_PICKING_UP, bool);
    }

    public void setIsEating(boolean bool) {
        entityData.set(IS_EATING, bool);
    }

    public void setOwned(boolean owned) {
        super.setTame(owned);
    }

    public void setEquipment() {
    }

    //////////////////////////////////// ATTACK
    //////////////////////////////////// FUNCTIONS////////////////////////////////////

    public boolean hurt(DamageSource dmg, float amt) {

        String name = this.getName().getString();
        String attacker_name;

        if (this.isInvulnerableTo(dmg)) {
            return false;
        } else {
            Entity entity = dmg.getEntity();
            this.setOrderedToSit(false);
            if (entity != null && !(entity instanceof Player) && !(entity instanceof AbstractArrow)) {
                amt = (amt + 1.0F) / 2.0F;
            }

            LivingEntity attacker = this.getLastHurtByMob();

            if (this.isTame() && attacker != null && hurtTimeStamp <= 0) {
                attacker_name = attacker.getName().getString();
                // String attacked = TEXT_ATTACKED.getString();

                LivingEntity owner = this.getOwner();
                if (owner != null && owner != attacker) {
                    owner.sendSystemMessage(TEXT_ATTACKED(name, attacker_name));
                    hurtTimeStamp = 80;
                }
            }

            return super.hurt(dmg, amt);
        }
    }

    public boolean doHurtTarget(Entity entity) {
        boolean flag = entity.hurt(DamageSource.mobAttack(this),
                (float) ((int) this.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        if (flag) {
            this.doEnchantDamageEffects(this, entity);

        }

        return flag;
    }

    public void die(DamageSource dmg) {
        super.die(dmg);

    }

    //////////////////////////////////// OTHER
    //////////////////////////////////// FUNCTIONS////////////////////////////////////

    public boolean needsToSleep() {
        return !this.level.isDay();
    }

    public void updateHunger() {
        if (getHunger() > 0 && getHunger() <= 100) {
            if (getIsWorking())
                setHunger((getHunger() - 0.0025F));
            else if (getIsWorking() && getLevel().isNight())
                setHunger((getHunger() - 0.006F));

            else if (isSleeping())
                setHunger((getHunger() + 0.0005F));

            else
                setHunger((getHunger() - 0.001F));
        }

        if (isStarving() && this.getIsWorking())
            this.setIsWorking(false);
    }

    public boolean needsToEat() {
        return (getHunger() <= 20F || getHealth() < getMaxHealth() * 0.2) || isStarving();
    }

    public boolean isStarving() {
        return (getHunger() <= 1F);
    }

    public boolean isSaturated() {
        return (getHunger() >= 90F);
    }

    public void resetWorkerParameters() {
        this.setBreakingTime(0);
        this.setCurrentTimeBreak(-1);
        this.setPreviousTimeBreak(-1);
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    public abstract int workerCosts();

    @Override
    public boolean canBreed() {
        return false;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void spawnTamingParticles(boolean smoke) {

    }

    public void workerSwingArm() {
        if (this.getRandom().nextInt(5) == 0) {
            if (!this.swinging) {
                this.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level.isClientSide) {
            return InteractionResult.CONSUME;
        } else {
            MutableComponent prefix = Component.literal(this.getName().getString() + ": ");
            if (this.isTame() && player.getUUID().equals(this.getOwnerUUID())) {
                if (player.isCrouching()) {
                    openGUI(player);
                }
                if (!player.isCrouching()) {
                    setFollow(!getFollow());
                    return InteractionResult.SUCCESS;
                }
            } else if (this.isTame() && !player.getUUID().equals(this.getOwnerUUID())) {
                player.sendSystemMessage(
                        prefix.append(TEXT_HELLO_OWNED(this.getProfessionName(), this.getOwnerName())));
            } else if (!this.isTame()) {
                player.sendSystemMessage(prefix.append(TEXT_HELLO(this.getProfessionName())));
                this.openHireGUI(player);
                this.navigation.stop();
                return InteractionResult.SUCCESS;
            }
            return super.mobInteract(player, hand);
        }
    }

    public boolean hire(Player player) {
        String name = this.getName().getString() + ": ";

        this.makeHireSound();

        this.tame(player);
        this.setOwnerName(player.getName().getString());
        this.setOrderedToSit(false);
        this.setOwnerUUID((player.getUUID()));
        this.setOwned(true);
        this.setFollow(true);
        this.navigation.stop();

        int i = this.random.nextInt(4);
        switch (i) {
            case 1 -> {
                String recruited1 = TEXT_RECRUITED1.getString();
                player.sendSystemMessage(Component.literal(name + recruited1));
            }
            case 2 -> {
                String recruited2 = TEXT_RECRUITED2.getString();
                player.sendSystemMessage(Component.literal(name + recruited2));
            }
            case 3 -> {
                String recruited3 = TEXT_RECRUITED3.getString();
                player.sendSystemMessage(Component.literal(name + recruited3));
            }
        }
        return true;
    }

    public void makeHireSound() {
        this.level.playSound(null, this.getX(), this.getY() + 4, this.getZ(), SoundEvents.VILLAGER_AMBIENT,
                this.getSoundSource(), 15.0F, 0.8F + 0.4F * this.random.nextFloat());
    }

    public abstract Predicate<ItemEntity> getAllowedItems();

    public abstract void openGUI(Player player);

    public abstract void initSpawn();

    public abstract boolean shouldDirectNavigation();

    public void openHireGUI(Player player) {
        this.navigation.stop();
        if (player instanceof ServerPlayer serverPlayer) {
            MenuProvider containerSupplier = new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.literal("MY MENU");
                }

                @Nullable
                @Override
                public AbstractContainerMenu createMenu(int i, @NotNull Inventory playerInventory,
                        @NotNull Player playerEntity) {
                    return new WorkerHireContainer(i, playerInventory.player, AbstractWorkerEntity.this,
                            playerInventory);
                }
            };

            Consumer<FriendlyByteBuf> extraDataWriter = packetBuffer -> {
                packetBuffer.writeUUID(getUUID());
            };

            NetworkHooks.openScreen((ServerPlayer) player, containerSupplier, extraDataWriter);
        } else {
            Main.SIMPLE_CHANNEL.sendToServer(new MessageHireGui(player, this.getUUID()));
        }
    }

    public static final MutableComponent TEXT_HELLO(String job) {
        return Component.translatable("chat.workers.text.hello", job);
    }

    public static final MutableComponent TEXT_HELLO_OWNED(String job, String owner) {
        return Component.translatable("chat.workers.text.hello_owned", job, owner);
    }

    public static final MutableComponent TEXT_RECRUITED1 = Component.translatable("chat.workers.text.recruited1");
    public static final MutableComponent TEXT_RECRUITED2 = Component.translatable("chat.workers.text.recruited2");
    public static final MutableComponent TEXT_RECRUITED3 = Component.translatable("chat.workers.text.recruited3");

    public static final MutableComponent TEXT_ATTACKED(String job, String attacker) {
        return Component.translatable("chat.workers.text.attacked");
    }

    public static final MutableComponent TEXT_WORKING = Component.translatable("chat.workers.text.working");
    public static final MutableComponent TEXT_DONE = Component.translatable("chat.workers.text.done");
    public static final MutableComponent TEXT_STARVING = Component.translatable("chat.workers.text.starving");

    public static final MutableComponent TEXT_FOLLOW = Component.translatable("chat.workers.text.follow");
    public static final MutableComponent TEXT_CONTINUE = Component.translatable("chat.workers.text.continue");
    public static final MutableComponent TEXT_WANDER = Component.translatable("chat.workers.text.wander");
    public static final MutableComponent TEXT_HOME = Component.translatable("chat.workers.text.home");
}
