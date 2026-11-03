package com.madreaper16.camperscache;

import com.madreaper16.camperscache.data.CampLedger;
import com.madreaper16.camperscache.data.CampState;
import com.madreaper16.camperscache.data.ChestHooks;
import com.mojang.serialization.JsonOps;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;

import java.util.Map;

public final class CamperGameTests {
    @GameTest
    public void debtsArePerPlayerAndRequireExactGoods(GameTestHelper test) {
        CampState state = new CampState();
        state.open("alice");
        test.assertTrue(
                state.shouldAttack("alice") && !state.shouldAttack("bob"),
                "Only the opener is targeted");
        state.stolen("alice", "minecraft:gunpowder", 4);
        test.assertTrue(
                state.returnGoods("alice", "minecraft:gunpowder", 4) == 0,
                "Apology comes before restitution");
        test.assertTrue(
                state.apologize("alice") && !state.shouldAttack("alice"), "Emerald stops attacks");
        test.assertTrue(
                !state.apologize("alice"), "Never charge a second apology for the same dispute");
        test.assertTrue(
                state.returnGoods("alice", "minecraft:firework_rocket", 64) == 0,
                "Crafted derivatives do not repay gunpowder");
        test.assertTrue(
                state.returnGoods("bob", "minecraft:gunpowder", 4) == 0,
                "A bystander's drop does not repay another player's debt");
        test.assertTrue(
                state.returnGoods("alice", "minecraft:gunpowder", 2) == 2,
                "Partial restitution accepted");
        test.assertTrue(
                state.offenders.get("alice").owed.get("minecraft:gunpowder") == 2,
                "Remaining quantity is exact");
        test.assertTrue(
                state.returnGoods("alice", "minecraft:gunpowder", 64) == 2,
                "Only take the outstanding amount");
        test.assertTrue(!state.offenders.containsKey("alice"), "Final restitution clears dispute");
        state.open("alice");
        test.assertTrue(
                state.apologize("alice") && state.offenders.isEmpty(),
                "Opening without theft needs only an emerald");
        test.succeed();
    }

    @GameTest
    public void ledgerPersistsAngerAndPartialRestitution(GameTestHelper test) {
        var ledger = new CampLedger();
        var state = new CampState();
        state.center = new int[] {10, 64, -5};
        state.chest = new int[] {11, 64, -5};
        state.stolen("alice", "minecraft:gunpowder", 7);
        state.apologize("alice");
        state.returnGoods("alice", "minecraft:gunpowder", 3);
        state.open("bob");
        ledger.camps.put("owner", state);
        var encoded = CampLedger.CODEC.encodeStart(JsonOps.INSTANCE, ledger).getOrThrow();
        var restored = CampLedger.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        var camp = restored.ownerOf(new BlockPos(11, 64, -5));
        test.assertTrue(camp != null, "Chest ownership survives serialization");
        test.assertTrue(
                camp.offenders.get("alice").apologyPaid
                        && camp.offenders.get("alice").owed.get("minecraft:gunpowder") == 4,
                "Partial repayment survives saving");
        test.assertTrue(
                camp.shouldAttack("bob") && !camp.shouldAttack("alice"),
                "Per-player anger survives saving");
        test.succeed();
    }

    @GameTest
    public void removalCountsItemsInsteadOfEnchantmentOrSlot(GameTestHelper test) {
        CampState state = new CampState();
        ChestHooks.recordRemoval(
                state,
                "alice",
                Map.of("minecraft:gunpowder", 16, "minecraft:diamond_helmet", 1),
                Map.of("minecraft:gunpowder", 10));
        test.assertTrue(
                state.offenders
                        .get("alice")
                        .owed
                        .equals(Map.of("minecraft:gunpowder", 6, "minecraft:diamond_helmet", 1)),
                "Item type and quantity are tracked");
        ChestHooks.recordRemoval(
                state,
                "alice",
                Map.of("minecraft:gunpowder", 10),
                Map.of("minecraft:gunpowder", 12));
        test.assertTrue(
                state.offenders.get("alice").owed.get("minecraft:gunpowder") == 6,
                "Depositing cannot create negative theft");
        test.succeed();
    }

    @GameTest
    public void camperBindsSuppliesAndEmeraldApology(GameTestHelper test) {
        var level = test.getLevel();
        test.setBlock(1, 1, 1, Blocks.CHEST);
        test.setBlock(0, 1, 1, Blocks.CAMPFIRE);
        var camper = test.spawn(CampersCache.CAMPER, 1, 1, 2);
        test.runAfterDelay(
                5,
                () -> {
                    CampState state = camper.state(level);
                    test.assertTrue(
                            state != null && state.chest != null, "Camper binds to camp chest");
                    var chest =
                            (ChestBlockEntity)
                                    level.getBlockEntity(
                                            new BlockPos(
                                                    state.chest[0],
                                                    state.chest[1],
                                                    state.chest[2]));
                    test.assertTrue(!chest.isEmpty(), "Camp supplies are generated");
                    ServerPlayer player = test.makeMockServerPlayerInLevel();
                    state.open(player.getStringUUID());
                    player.setItemInHand(
                            InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 2));
                    camper.mobInteract(player, InteractionHand.MAIN_HAND);
                    test.assertTrue(
                            player.getMainHandItem().getCount() == 1,
                            "Apology takes exactly one emerald");
                    test.assertTrue(
                            !state.offenders.containsKey(player.getStringUUID()),
                            "Opening-only dispute is forgiven");
                    test.succeed();
                });
    }

    @GameTest(padding = 30)
    public void realChestClickAndDroppedRestitution(GameTestHelper test) {
        var level = test.getLevel();
        test.setBlock(1, 1, 1, Blocks.CHEST);
        test.setBlock(0, 1, 1, Blocks.CAMPFIRE);
        var camper = test.spawn(CampersCache.CAMPER, 1, 1, 2);
        test.runAfterDelay(
                5,
                () -> {
                    var state = camper.state(level);
                    var chest =
                            (ChestBlockEntity)
                                    level.getBlockEntity(
                                            new BlockPos(
                                                    state.chest[0],
                                                    state.chest[1],
                                                    state.chest[2]));
                    ServerPlayer player = test.makeMockServerPlayerInLevel();
                    player.setPos(camper.position().add(1, 0, 0));
                    var menu = ChestMenu.threeRows(7, player.getInventory(), chest);
                    test.assertTrue(
                            state.shouldAttack(player.getStringUUID()),
                            "Actual chest open creates anger through the mixin");
                    int slot = 0;
                    while (slot < chest.getContainerSize()
                            && !chest.getItem(slot).is(Items.GUNPOWDER)) slot++;
                    test.assertTrue(
                            slot < chest.getContainerSize(),
                            "Camp contains its guaranteed gunpowder");
                    int amount = chest.getItem(slot).getCount();
                    menu.clicked(slot, 0, ContainerInput.QUICK_MOVE, player);
                    test.assertTrue(
                            state.offenders
                                            .get(player.getStringUUID())
                                            .owed
                                            .get("minecraft:gunpowder")
                                    == amount,
                            "Actual shift-click records stolen count");
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD));
                    camper.mobInteract(player, InteractionHand.MAIN_HAND);
                    test.assertTrue(
                            !state.shouldAttack(player.getStringUUID())
                                    && state.offenders.containsKey(player.getStringUUID()),
                            "Apology stops punches but retains theft debt");
                    var dropped =
                            new ItemEntity(
                                    level,
                                    camper.getX(),
                                    camper.getY(),
                                    camper.getZ(),
                                    new ItemStack(Items.GUNPOWDER, amount + 3));
                    dropped.setThrower(player);
                    dropped.setNeverPickUp();
                    level.addFreshEntity(dropped);
                    test.runAfterDelay(
                            10,
                            () -> {
                                test.assertTrue(
                                        !state.offenders.containsKey(player.getStringUUID()),
                                        "Dropped matching goods clear theft");
                                test.assertTrue(
                                        dropped.getItem().getCount() == 3,
                                        "Excess returned goods remain available");
                                camper.die(level.damageSources().genericKill());
                                test.assertTrue(
                                        CampLedger.get(level).ownerOf(chest.getBlockPos()) == null,
                                        "Death releases chest ownership");
                                test.succeed();
                            });
                });
    }

    @GameTest(structure = "campers_cache:camp", maxTicks = 400, padding = 30)
    public void designedCampSpawnsCamperAndSleeps(GameTestHelper test) {
        test.setTime(13000);
        test.runAfterDelay(
                120,
                () -> {
                    var camper = test.findOneEntity(CampersCache.CAMPER);
                    test.assertTrue(
                            camper.state(test.getLevel()) != null,
                            "Template spawns the custom Camper");
                    test.assertTrue(
                            camper.isSleeping(),
                            "Camper reaches the designed bed and sleeps at night");
                    test.setTime(1000);
                    test.runAfterDelay(
                            5,
                            () -> {
                                test.assertTrue(
                                        !camper.isSleeping(), "Camper wakes during daytime");
                                var state = camper.state(test.getLevel());
                                String offender = java.util.UUID.randomUUID().toString();
                                state.stolen(offender, "minecraft:gunpowder", 4);
                                state.apologize(offender);
                                test.runAfterDelay(
                                        160,
                                        () -> {
                                            var sign =
                                                    (SignBlockEntity)
                                                            test.getLevel()
                                                                    .getBlockEntity(
                                                                            new BlockPos(
                                                                                    state.sign[0],
                                                                                    state.sign[1],
                                                                                    state.sign[2]));
                                            String message =
                                                    sign
                                                            .getText(SignTextSlot.FRONT)
                                                            .getMessages(false)
                                                            .stream()
                                                            .map(c -> c.getString())
                                                            .collect(
                                                                    java.util.stream.Collectors
                                                                            .joining(" "));
                                            test.assertTrue(
                                                    message.toLowerCase(java.util.Locale.ROOT)
                                                                    .contains("gunpowder")
                                                            && message.contains("4"),
                                                    "Camp notice lists outstanding item and quantity; text="
                                                            + message
                                                            + ", camper="
                                                            + camper.position()
                                                            + ", sign="
                                                            + java.util.Arrays.toString(
                                                                    state.sign));
                                            test.succeed();
                                        });
                            });
                });
    }

    @GameTest(padding = 40, maxTicks = 100)
    public void punchesStopWhenOffenderLeavesCamp(GameTestHelper test) {
        var level = test.getLevel();
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        test.setBlock(1, 1, 1, Blocks.CHEST);
        test.setBlock(0, 1, 1, Blocks.CAMPFIRE);
        var camper = test.spawn(CampersCache.CAMPER, 1, 1, 2);
        test.runAfterDelay(
                5,
                () -> {
                    var state = camper.state(level);
                    ServerPlayer player =
                            (ServerPlayer) test.makeMockServerPlayer(GameType.SURVIVAL);
                    var connection =
                            new net.minecraft.network.Connection(
                                    net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
                    new io.netty.channel.embedded.EmbeddedChannel(connection);
                    var cookie =
                            net.minecraft.server.network.CommonListenerCookie.createInitial(
                                    player.getGameProfile(), false);
                    level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
                    player.connection.handleAcceptPlayerLoad(
                            new net.minecraft.network.protocol.game
                                    .ServerboundPlayerLoadedPacket());
                    player.setGameMode(GameType.SURVIVAL);
                    player.setInvulnerableTime(0);
                    player.setPos(camper.position().add(0.8, 0, 0));
                    state.open(player.getStringUUID());
                    test.runAfterDelay(
                            3,
                            () -> {
                                test.assertTrue(
                                        player.getHealth() < player.getMaxHealth(),
                                        "Camper punches offending survival player; creative="
                                                + player.isCreative()
                                                + ", invulnerable="
                                                + player.isInvulnerable()
                                                + ", player="
                                                + player.position()
                                                + ", camper="
                                                + camper.position()
                                                + ", center="
                                                + java.util.Arrays.toString(state.center)
                                                + ", sight="
                                                + camper.hasLineOfSight(player)
                                                + ", difficulty="
                                                + level.getDifficulty());
                                player.setPos(
                                        state.center[0] + 26.5,
                                        state.center[1] + 1,
                                        state.center[2] + 0.5);
                                float health = player.getHealth();
                                test.runAfterDelay(
                                        5,
                                        () -> {
                                            test.assertTrue(
                                                    camper.getNavigation().isDone(),
                                                    "Camper cancels pursuit when player leaves 20-block radius");
                                            test.assertTrue(
                                                    player.getHealth() == health,
                                                    "No further punches beyond boundary");
                                            test.assertTrue(
                                                    state.shouldAttack(player.getStringUUID()),
                                                    "Leaving does not erase the dispute");
                                            test.succeed();
                                        });
                            });
                });
    }
}
