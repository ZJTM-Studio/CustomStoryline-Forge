package com.yang458.customstoryline.event;

import com.yang458.customstoryline.CustomstorylineMod;
import com.yang458.customstoryline.data.ActionData;
import com.yang458.customstoryline.data.StorylineData;
import com.yang458.customstoryline.manager.StorylineManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod.EventBusSubscriber
public class StorylineEvents {

    public static class PlayerLockState {
        public boolean blockMove = false;
        public boolean blockTouch = false;
        public boolean blockPerspective = false;
        public boolean blockMobTarget = false;
        public float yaw;
        public float pitch;
        public Vec3 lockPos;
    }

    public static class DelayedTask {
        public long executeTick;
        public Runnable runnable;

        public DelayedTask(long executeTick, Runnable runnable) {
            this.executeTick = executeTick;
            this.runnable = runnable;
        }
    }

    public static final Map<UUID, PlayerLockState> LOCKED_PLAYERS = new HashMap<>();
    private static final Map<UUID, Set<String>> PLAYER_IN_TRIGGER_ZONE = new HashMap<>();
    private static final Map<UUID, Long> LAST_HINT_TICK = new HashMap<>();
    private static final List<DelayedTask> SCHEDULED_TASKS = new ArrayList<>();
    private static final Pattern SCORE_PATTERN = Pattern.compile("@score:([a-zA-Z0-9_]+)");

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        StorylineManager.getConfigDir(event.getServer());
        StorylineManager.loadAll(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            long currentTick = event.getServer().getTickCount();
            SCHEDULED_TASKS.removeIf(task -> {
                if (currentTick >= task.executeTick) {
                    try {
                        task.runnable.run();
                    } catch (Exception e) {
                        CustomstorylineMod.LOGGER.error("[Customstoryline] Exception occurred during delayed task execution", e);
                    }
                    return true;
                }
                return false;
            });
        }
    }

    private static void sendBlockHint(ServerPlayer player, String translationKey) {
        long currentTick = player.getServer().getTickCount();
        long lastTick = LAST_HINT_TICK.getOrDefault(player.getUUID(), 0L);
        if (currentTick - lastTick >= 20) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable(translationKey)));
            LAST_HINT_TICK.put(player.getUUID(), currentTick);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;

        PlayerLockState state = LOCKED_PLAYERS.get(player.getUUID());
        if (state != null) {
            if (state.blockMove) {
                if (player.getX() != state.lockPos.x || player.getY() != state.lockPos.y || player.getZ() != state.lockPos.z) {
                    sendBlockHint(player, "message.customstoryline.block.move");
                    player.teleportTo(player.serverLevel(), state.lockPos.x, state.lockPos.y, state.lockPos.z,
                            state.blockPerspective ? state.yaw : player.getYRot(),
                            state.blockPerspective ? state.pitch : player.getXRot());
                }
                player.setDeltaMovement(0, 0, 0);
            }

            if (state.blockPerspective) {
                if (player.getYRot() != state.yaw || player.getXRot() != state.pitch) {
                    sendBlockHint(player, "message.customstoryline.block.perspective");
                    player.connection.teleport(player.getX(), player.getY(), player.getZ(), state.yaw, state.pitch);
                }
            }

            if (state.blockMobTarget) {
                player.setInvulnerable(true);
                if (player.tickCount % 5 == 0) {
                    AABB area = player.getBoundingBox().inflate(64.0D);
                    List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, area);
                    for (Mob mob : mobs) {
                        if (mob.getTarget() == player) {
                            mob.setTarget(null);
                            mob.setLastHurtByMob(null);
                            mob.getNavigation().stop();
                        }
                    }
                }
            } else {
                player.setInvulnerable(false);
            }
        } else {
            if (player.isInvulnerable() && !player.isCreative() && !player.isSpectator()) {
                player.setInvulnerable(false);
            }
        }

        if (player.tickCount % 5 != 0) return;

        Set<String> currentZones = PLAYER_IN_TRIGGER_ZONE.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
        for (Map.Entry<String, StorylineData> entry : StorylineManager.getStorylines().entrySet()) {
            String namespace = entry.getKey();
            StorylineData data = entry.getValue();
            if (data.trigger_position == null || data.trigger_position.size() < 3) continue;

            boolean inTrigger = isPlayerInTrigger(player, data.trigger_position);
            if (inTrigger) {
                if (!currentZones.contains(namespace)) {
                    currentZones.add(namespace);
                    if (data.config.uniqueness && data.globalTriggered) continue;
                    if (!data.config.repeat && StorylineManager.hasPlayerTriggered(player.getUUID(), namespace)) continue;
                    triggerStoryline(player, namespace, data);
                }
            } else {
                currentZones.remove(namespace);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerLockState state = LOCKED_PLAYERS.get(player.getUUID());
            if (state != null && state.blockMove) {
                event.setCanceled(true);
                player.setDeltaMovement(player.getDeltaMovement().x, 0, player.getDeltaMovement().z);
                sendBlockHint(player, "message.customstoryline.block.jump");
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerLockState state = LOCKED_PLAYERS.get(player.getUUID());
            if (state != null && state.blockTouch) {
                event.setCanceled(true);
                sendBlockHint(player, "message.customstoryline.block.interact");
                if (event instanceof PlayerInteractEvent.RightClickBlock) {
                    player.connection.send(new ClientboundContainerSetContentPacket(0, 0, player.inventoryMenu.getItems(), player.inventoryMenu.getCarried()));
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player && event.getEntity() instanceof Mob) {
            PlayerLockState state = LOCKED_PLAYERS.get(player.getUUID());
            if (state != null && state.blockMobTarget) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerLockState state = LOCKED_PLAYERS.get(player.getUUID());
            if (state != null && state.blockMobTarget) {
                event.setCanceled(true);
            }
        }
    }

    private static boolean isPlayerInTrigger(ServerPlayer player, List<Double> pos) {
        int px = player.blockPosition().getX(), py = player.blockPosition().getY(), pz = player.blockPosition().getZ();
        int x1 = pos.get(0).intValue(), y1 = pos.get(1).intValue(), z1 = pos.get(2).intValue();
        int x2 = pos.size() >= 6 ? pos.get(3).intValue() : x1;
        int y2 = pos.size() >= 6 ? pos.get(4).intValue() : y1;
        int z2 = pos.size() >= 6 ? pos.get(5).intValue() : z1;

        return px >= Math.min(x1, x2) && px <= Math.max(x1, x2) &&
                py >= Math.min(y1, y2) && py <= Math.max(y1, y2) &&
                pz >= Math.min(z1, z2) && pz <= Math.max(z1, z2);
    }

    public static void triggerStoryline(ServerPlayer player, String namespace, StorylineData data) {
        StorylineManager.setPlayerTriggered(player.getUUID(), namespace, true);
        if (data.config.uniqueness) data.globalTriggered = true;

        PlayerLockState state = new PlayerLockState();
        state.blockMove = data.config.block_player_operation;
        state.blockTouch = data.config.block_touch;
        state.blockMobTarget = data.config.block_mob_target;
        state.lockPos = player.position();
        state.yaw = player.getYRot();
        state.pitch = player.getXRot();

        if (data.config.block_perspective != null && data.config.block_perspective.enabled) {
            state.blockPerspective = true;
            state.yaw = data.config.block_perspective.yaw;
            state.pitch = data.config.block_perspective.pitch;
        }

        if (state.blockMove || state.blockTouch || state.blockPerspective || state.blockMobTarget) {
            LOCKED_PLAYERS.put(player.getUUID(), state);
        }

        CustomstorylineMod.LOGGER.info("[Customstoryline] Player {} triggered storyline: {}. Lock state: Move={}, Touch={}, Perspective={}, Invulnerable={}",
                player.getScoreboardName(), namespace, state.blockMove, state.blockTouch, state.blockPerspective, state.blockMobTarget);

        if (state.blockMobTarget) {
            player.setInvulnerable(true);
            AABB area = player.getBoundingBox().inflate(64.0D);
            List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, area);
            for (Mob mob : mobs) {
                if (mob.getTarget() == player) {
                    mob.setTarget(null);
                    mob.setLastHurtByMob(null);
                    mob.getNavigation().stop();
                }
            }
        }

        MinecraftServer server = player.getServer();
        if (server == null) return;

        if (data.action != null) {
            for (ActionData action : data.action) {
                int delayTicks = parseDelayTicks(action.delay_trigger);

                if (delayTicks > 0) {
                    long executeTime = server.getTickCount() + delayTicks;
                    SCHEDULED_TASKS.add(new DelayedTask(executeTime, () -> {
                        if (server.getPlayerList().getPlayer(player.getUUID()) != null) {
                            executeAction(player, action);
                        }
                    }));
                    CustomstorylineMod.LOGGER.debug("[Customstoryline] Action queued for {} ticks (Delay: {})", delayTicks, action.delay_trigger);
                } else {
                    executeAction(player, action);
                }
            }
        }
    }

    private static int parseDelayTicks(String delayStr) {
        if (delayStr == null || delayStr.trim().isEmpty()) return 0;
        delayStr = delayStr.toLowerCase().replace(" ", "").trim();
        try {
            if (delayStr.endsWith("tick") || delayStr.endsWith("ticks")) {
                return (int) Math.round(Double.parseDouble(delayStr.replace("ticks", "").replace("tick", "").trim()));
            } else {
                return (int) Math.round(Double.parseDouble(delayStr.replace("s", "").trim()) * 20);
            }
        } catch (NumberFormatException e) {
            CustomstorylineMod.LOGGER.warn("[Customstoryline] Invalid delay_trigger format: '{}'. Defaulting to 0.", delayStr);
            return 0;
        }
    }

    private static void executeAction(ServerPlayer player, ActionData action) {
        if (action.unlock_block) {
            LOCKED_PLAYERS.remove(player.getUUID());
            player.setInvulnerable(false);
            CustomstorylineMod.LOGGER.info("[Customstoryline] Player {} locked states forcefully cleared (unlock_block).", player.getScoreboardName());
            return;
        }

        PlayerLockState state = LOCKED_PLAYERS.get(player.getUUID());
        boolean needState = action.set_block_player_operation != null ||
                action.set_block_touch != null ||
                action.set_block_mob_target != null ||
                action.set_block_perspective != null ||
                (action.set_player_perspective != null && action.set_player_perspective.size() >= 2);

        if (state == null && needState) {
            state = new PlayerLockState();
            state.lockPos = player.position();
            state.yaw = player.getYRot();
            state.pitch = player.getXRot();
            LOCKED_PLAYERS.put(player.getUUID(), state);
        }

        if (state != null) {
            if (action.set_block_player_operation != null) state.blockMove = action.set_block_player_operation;
            if (action.set_block_touch != null) state.blockTouch = action.set_block_touch;
            if (action.set_block_mob_target != null) {
                state.blockMobTarget = action.set_block_mob_target;
                if (!state.blockMobTarget) player.setInvulnerable(false);
                else player.setInvulnerable(true);
            }
            if (action.set_block_perspective != null) state.blockPerspective = action.set_block_perspective;

            if (action.set_player_perspective != null && action.set_player_perspective.size() >= 2) {
                state.yaw = action.set_player_perspective.get(0);
                state.pitch = action.set_player_perspective.get(1);
                state.blockPerspective = true;
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), state.yaw, state.pitch);
            }

            if (!state.blockMove && !state.blockTouch && !state.blockPerspective && !state.blockMobTarget) {
                LOCKED_PLAYERS.remove(player.getUUID());
                player.setInvulnerable(false);
            }
        }

        if (action.text != null) player.sendSystemMessage(Component.literal(parsePlaceholders(action.text, player)));
        if (action.actionbar != null) player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(parsePlaceholders(action.actionbar, player))));
        if (action.title != null) {
            Component titleComp = Component.literal(parsePlaceholders(action.title, player));
            Component subComp = action.subtitle != null ? Component.literal(parsePlaceholders(action.subtitle, player)) : Component.empty();
            player.connection.send(new ClientboundSetTitleTextPacket(titleComp));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subComp));
        } else if (action.subtitle != null) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(parsePlaceholders(action.subtitle, player))));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        }

        if (action.effect != null) {
            try {
                String effectId = action.effect.trim().toLowerCase();
                if (!effectId.contains(":")) effectId = "minecraft:" + effectId;

                ResourceLocation effectLoc = ResourceLocation.tryParse(effectId);
                if (effectLoc != null) {
                    MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectLoc);
                    if (effect != null) {
                        int duration = action.effect_duration != null ? action.effect_duration : 200;
                        int amplifier = action.effect_amplifier != null ? action.effect_amplifier : 0;
                        player.addEffect(new MobEffectInstance(effect, duration, amplifier));
                        CustomstorylineMod.LOGGER.debug("[Customstoryline] Applied effect {} (Amp: {}, Duration: {} ticks) to player {}", effectId, amplifier, duration, player.getScoreboardName());
                    } else {
                        CustomstorylineMod.LOGGER.warn("[Customstoryline] Effect ID '{}' not found in registry.", effectId);
                    }
                }
            } catch (Exception e) {
                CustomstorylineMod.LOGGER.error("[Customstoryline] Failed to apply effect '{}' to player {}", action.effect, player.getScoreboardName(), e);
            }
        }
    }

    private static String parsePlaceholders(String text, ServerPlayer player) {
        if (text == null) return "";
        String parsed = text.replace("@self", player.getScoreboardName());
        Matcher matcher = SCORE_PATTERN.matcher(parsed);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String objectiveName = matcher.group(1);
            net.minecraft.world.scores.Objective obj = player.getScoreboard().getObjective(objectiveName);
            int score = obj != null ? player.getScoreboard().getOrCreatePlayerScore(player.getScoreboardName(), obj).getScore() : 0;
            matcher.appendReplacement(sb, String.valueOf(score));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}