package com.madreaper16.camperscache.entity;

import com.madreaper16.camperscache.config.ConfigLoot;
import com.madreaper16.camperscache.config.SettingsStore;
import com.madreaper16.camperscache.data.CampLedger;
import com.madreaper16.camperscache.data.CampState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.UUID;

public final class Camper extends Villager {
    private String campDimension = "";
    private int attackCooldown;
    private boolean chasing;

    public Camper(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        setCustomName(Component.literal("Camper"));
    }

    private ServerLevel campLevel(ServerLevel current) {
        if (campDimension.isEmpty()) campDimension = current.dimension().identifier().toString();
        return current.getServer()
                .getLevel(
                        ResourceKey.create(Registries.DIMENSION, Identifier.parse(campDimension)));
    }

    public CampState state(ServerLevel level) {
        ServerLevel home = campLevel(level);
        return home == null ? null : CampLedger.get(home).camps.get(getStringUUID());
    }

    private static int[] coordinates(BlockPos p) {
        return new int[] {p.getX(), p.getY(), p.getZ()};
    }

    private static BlockPos position(int[] p) {
        return new BlockPos(p[0], p[1], p[2]);
    }

    private static double horizontalDistanceSquared(Vec3 pos, int[] center) {
        double dx = pos.x - center[0] - 0.5, dz = pos.z - center[2] - 0.5;
        return dx * dx + dz * dz;
    }

    private CampState initializeCamp(ServerLevel level) {
        CampLedger ledger = CampLedger.get(level);
        CampState state = new CampState();
        BlockPos origin = blockPosition();
        state.center = coordinates(origin);
        double chestDistance = Double.MAX_VALUE,
                bedDistance = Double.MAX_VALUE,
                signDistance = Double.MAX_VALUE;
        for (BlockPos p :
                BlockPos.betweenClosed(origin.offset(-12, -4, -12), origin.offset(12, 5, 12))) {
            var block = level.getBlockState(p);
            double distance = p.distSqr(origin);
            if (block.is(Blocks.CAMPFIRE)) state.center = coordinates(p);
            if (level.getBlockEntity(p) instanceof ChestBlockEntity && distance < chestDistance) {
                state.chest = coordinates(p);
                chestDistance = distance;
            }
            if (block.getBlock() instanceof BedBlock
                    && block.getValue(BedBlock.PART) == BedPart.HEAD
                    && distance < bedDistance) {
                state.bed = coordinates(p);
                bedDistance = distance;
            }
            if (level.getBlockEntity(p) instanceof SignBlockEntity && distance < signDistance) {
                state.sign = coordinates(p);
                signDistance = distance;
            }
        }
        ledger.camps.put(getStringUUID(), state);
        ledger.setDirty();
        setHomeTo(position(state.center), SettingsStore.active().camperRadius);
        fillSupplies(level, state);
        return state;
    }

    private void fillSupplies(ServerLevel level, CampState state) {
        if (state.suppliesGenerated || state.chest == null) return;
        if (!(level.getBlockEntity(position(state.chest)) instanceof ChestBlockEntity chest))
            return;
        state.suppliesGenerated = true;
        // Leave existing chest contents alone.
        if (chest.isEmpty()) {
            ConfigLoot.fill("camp", chest, level, random.nextLong());
            chest.setChanged();
        }
        CampLedger.get(level).setDirty();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (!SettingsStore.active().enabled || !SettingsStore.active().camperEnabled) {
            getNavigation().stop();
            return;
        }
        // Use camp AI instead of normal villager jobs and breeding.
        CampState state = state(level);
        if (state == null) {
            if (!campDimension.equals(level.dimension().identifier().toString())) return;
            state = initializeCamp(level);
        }
        if (isPassenger()) {
            getNavigation().stop();
            return;
        }
        if (!campDimension.equals(level.dimension().identifier().toString())) return;
        if (attackCooldown > 0) attackCooldown--;
        if (tickCount % 5 == 0) acceptReturnedGoods(level, state);
        CampState current = state;
        if (tickCount % 20 == 0)
            setHomeTo(position(state.center), SettingsStore.active().camperRadius);
        ServerPlayer offender =
                level.players().stream()
                        .filter(p ->
                                p.isAlive()
                                        && !p.isSpectator()
                                        && !p.isCreative()
                                        && current.shouldAttack(p.getStringUUID()))
                        .filter(p ->
                                horizontalDistanceSquared(p.position(), current.center)
                                        <= SettingsStore.active().camperRadius
                                                * SettingsStore.active().camperRadius)
                        .min(Comparator.comparingDouble(this::distanceToSqr))
                        .orElse(null);
        if (offender != null
                && horizontalDistanceSquared(position(), state.center)
                        <= SettingsStore.active().camperRadius
                                * SettingsStore.active().camperRadius) {
            chasing = true;
            if (isSleeping()) stopSleeping();
            getLookControl().setLookAt(offender, 30, 30);
            if (tickCount % 10 == 0) getNavigation().moveTo(offender, 0.75);
            if (tickCount % 20 == 0) angryParticles(level);
            if (distanceToSqr(offender) <= 5 && attackCooldown == 0 && hasLineOfSight(offender)) {
                offender.hurtServer(level, damageSources().mobAttack(this), 1.0F);
                attackCooldown = 20;
            }
            return;
        }
        if (chasing) {
            getNavigation().stop();
            chasing = false;
        }
        if (horizontalDistanceSquared(position(), state.center)
                > SettingsStore.active().camperRadius * SettingsStore.active().camperRadius) {
            if (isSleeping()) stopSleeping();
            if (tickCount % 20 == 0) navigate(position(state.center), 0.6);
            return;
        }
        long time = Math.floorMod(level.getOverworldClockTime(), 24000);
        boolean night = time >= 12000 && time < 23900;
        if (!night && isSleeping()) stopSleeping();
        if (night
                && state.bed != null
                && level.getBlockState(position(state.bed)).getBlock() instanceof BedBlock) {
            if (!isSleeping()) {
                if (distanceToSqr(Vec3.atCenterOf(position(state.bed))) < 6)
                    startSleeping(position(state.bed));
                else if (tickCount % 20 == 0) navigate(position(state.bed), 0.55);
            }
            return;
        }
        if (isSleeping()) return;
        if (tickCount % 20 == 0 && updateNotice(level, state)) return;
        if (tickCount % 80 == 0 && getNavigation().isDone()) {
            for (int attempt = 0; attempt < 8; attempt++) {
                int radius = SettingsStore.active().camperRadius - 2;
                int dx = random.nextInt(radius * 2 + 1) - radius,
                        dz = random.nextInt(radius * 2 + 1) - radius;
                if (dx * dx + dz * dz > radius * radius) continue;
                BlockPos p = position(state.center).offset(dx, 0, dz);
                int y =
                        level.getHeight(
                                net.minecraft.world.level.levelgen.Heightmap.Types
                                        .MOTION_BLOCKING_NO_LEAVES,
                                p.getX(),
                                p.getZ());
                if (Math.abs(y - state.center[1]) > 8) continue;
                BlockPos target = new BlockPos(p.getX(), y, p.getZ());
                if (!level.getFluidState(target).isEmpty()) continue;
                navigate(target, 0.5);
                break;
            }
        }
    }

    private void navigate(BlockPos p, double speed) {
        getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, speed);
    }

    private void acceptReturnedGoods(ServerLevel level, CampState state) {
        for (ItemEntity item :
                level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(2.5))) {
            if (!(item.getOwner() instanceof Player player)) continue;
            ItemStack stack = item.getItem();
            int accepted =
                    state.returnGoods(
                            player.getStringUUID(),
                            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                            stack.getCount());
            if (accepted == 0) continue;
            stack.shrink(accepted);
            if (stack.isEmpty()) item.discard();
            else item.setItem(stack);
            CampLedger.get(level).setDirty();
            if (!state.offenders.containsKey(player.getStringUUID())) happyParticles(level);
        }
    }

    // Cycle the sign when more items are missing than it can show.
    private boolean updateNotice(ServerLevel level, CampState state) {
        if (state.sign == null
                || !(level.getBlockEntity(position(state.sign)) instanceof SignBlockEntity sign))
            return false;
        var disputes =
                state.offenders.entrySet().stream()
                        .filter(e -> e.getValue().apologyPaid && !e.getValue().owed.isEmpty())
                        .toList();
        String[] lines = {"Camper's Cache", "Private supplies", "Please ask first", ""};
        if (!disputes.isEmpty()) {
            var dispute = disputes.get(Math.floorMod(tickCount / 100, disputes.size()));
            var goods = dispute.getValue().owed.entrySet().stream().toList();
            var good = goods.get(Math.floorMod(tickCount / 100, goods.size()));
            if (distanceToSqr(Vec3.atCenterOf(position(state.sign))) > 9) {
                navigate(position(state.sign), 0.55);
                return true;
            }
            String name =
                    new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(good.getKey())))
                            .getHoverName()
                            .getString();
            String[] words = name.split(" ");
            String first = "", second = "";
            for (String word : words) {
                if ((first + " " + word).trim().length() <= 13 && second.isEmpty())
                    first = (first + " " + word).trim();
                else second = (second + " " + word).trim();
            }
            var player = level.getPlayerByUUID(UUID.fromString(dispute.getKey()));
            String playerName = player == null ? "Return my goods" : player.getName().getString();
            lines =
                    new String[] {
                        playerName.substring(0, Math.min(13, playerName.length())),
                        "Return " + good.getValue() + " x",
                        first,
                        second
                    };
        }
        var mutable = sign.getText(SignTextSlot.FRONT).asMutable();
        for (int i = 0; i < 4; i++) mutable.setLine(i, Component.literal(lines[i]));
        var text = mutable.asImmutable();
        if (!text.equals(sign.getText(SignTextSlot.FRONT))) {
            sign.setText(text, SignTextSlot.FRONT);
            sign.setText(text, SignTextSlot.BACK);
            sign.setWaxed(true);
            sign.setChanged();
            level.sendBlockUpdated(
                    sign.getBlockPos(), sign.getBlockState(), sign.getBlockState(), 3);
        }
        return !disputes.isEmpty();
    }

    public void angryParticles(ServerLevel level) {
        playSound(SoundEvents.VILLAGER_NO, 1, 1);
        level.sendParticles(
                ParticleTypes.ANGRY_VILLAGER, getX(), getEyeY() + 0.3, getZ(), 3, 0.2, 0.1, 0.2, 0);
    }

    private void happyParticles(ServerLevel level) {
        playSound(SoundEvents.VILLAGER_YES, 1, 1);
        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER, getX(), getEyeY(), getZ(), 6, 0.3, 0.4, 0.3, 0);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        CampState state = state(level);
        if (state == null) return InteractionResult.SUCCESS;
        if (!SettingsStore.active().enabled || !SettingsStore.active().camperEnabled)
            return InteractionResult.SUCCESS;
        CampState.Offense offense = state.offenders.get(player.getStringUUID());
        if (offense != null) {
            if (player.getItemInHand(hand).is(Items.EMERALD)
                    && state.apologize(player.getStringUUID())) {
                player.getItemInHand(hand).shrink(1);
                CampLedger.get(campLevel(level)).setDirty();
                getNavigation().stop();
                if (isSleeping()) stopSleeping();
                if (!state.offenders.containsKey(player.getStringUUID())) happyParticles(level);
                else {
                    playSound(SoundEvents.VILLAGER_YES, 1, 1);
                    updateNotice(level, state);
                }
            } else {
                angryParticles(level);
                if (offense.apologyPaid) message(player, "Drop the missing goods near the Camper.");
                else message(player, "Offer an emerald to apologize.");
            }
            return InteractionResult.SUCCESS;
        }
        if (isPassenger()
                || !campDimension.equals(level.dimension().identifier().toString())
                || horizontalDistanceSquared(position(), state.center)
                        > SettingsStore.active().camperRadius * SettingsStore.active().camperRadius)
            angryParticles(level);
        else message(player, "Nothing to trade today.");
        return InteractionResult.SUCCESS;
    }

    private static void message(Player player, String text) {
        if (player instanceof ServerPlayer serverPlayer)
            serverPlayer.sendOverlayMessage(Component.literal(text));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getEntity() instanceof Player player) {
            var state = state(level);
            if (state != null
                    && SettingsStore.active().enabled
                    && SettingsStore.active().camperEnabled) {
                state.open(player.getStringUUID());
                CampLedger.get(campLevel(level)).setDirty();
            }
        }
        return super.hurtServer(level, source, damage);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level && campLevel(level) != null) {
            CampLedger ledger = CampLedger.get(campLevel(level));
            ledger.camps.remove(getStringUUID());
            ledger.setDirty();
        }
        super.die(source);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("CampersCacheDimension", campDimension);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        campDimension = input.getStringOr("CampersCacheDimension", "");
    }
}
