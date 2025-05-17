package com.example;
import static net.minecraft.server.command.CommandManager.argument;

/* Java API */
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/* Logger */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Fabric API */
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

/* Minecraft API */
import net.minecraft.world.World;
import net.minecraft.world.GameMode;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;


public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "SuperExtream";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Set<UUID> observingBeforeRevive = new HashSet<>();
    private static final Map<UUID, Integer> waitingToSwitchBack = new HashMap<>();
    private static final int OBSERVER_DURATION_SECONDS = 10;

    public static final String REVIVE_TAG = "_revive_time";
    public static final int REVIVE_TIME   = 900;
    public static final int EVERY_TICK    = 20;

	@Override
	public void onInitialize() {
        LOGGER.info("Init plugin...");
	    
        //world.getGameRules().get(GameRules.DO_IMMEDIATE_RESPAWN).set(true, world.getServer());

        /* 死亡後的流程
         * 1. 先復活玩家
         * 2. 傳送玩家到重生點
         * 3. 給玩家上負面效果
         * */
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            respawnPlayer(player);
            showDeathMessage(player);
            setScoreboard(
                player,
                player.getUuid().toString() + REVIVE_TAG,
                (int)(System.currentTimeMillis() / 1000L) + REVIVE_TIME
            );
        });

        /* 每個tick結算前執行 
         * 1. 每隔 EVERY_TICK 偵測玩家是否已到可復活時間
         * 2. 將正在觀戰的玩家拉回重生點
         * 3. 移除玩家身上的復活時間 tag
         * */
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            /*
             * 檢查是否有玩家還在觀察者模式
             * 1. 超過時間切換回生存模式
             * 2. 給予玩家復活無敵 Buff
             * */
            Iterator<Map.Entry<UUID, Integer>> iter = waitingToSwitchBack.entrySet().iterator();
            int now_time                            = (int)(System.currentTimeMillis()/1000L);
            while (iter.hasNext()) {
                Map.Entry<UUID, Integer> entry = iter.next();
                if (now_time >= entry.getValue()) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                    if (player != null) {
                        player.changeGameMode(GameMode.SURVIVAL);
                        player.sendMessage(Text.literal("你已重新進入生存模式。"), false);
                        addRespawnBuff(player, 30);
                    }
                    iter.remove();
                }
            }

            /*
             * 每隔 EVERY_TICK 執行一次
             * 1. 檢查所有玩家身上是否有復活時間的 tag
             * 2. 如果有，則檢查時間是否已到
             * 3. 如果已到，則將玩家傳送回重生點
             * 4. 如果還沒到，則顯示剩餘時間
             * 5. 如果還沒到，則給予玩家負面效果
             * */
            if( server.getTicks() % EVERY_TICK == 0 ){
                server.getPlayerManager().getPlayerList().forEach((player) -> {
                    // 檢查玩家身上有沒有復活時間的 tag
                    if(player.getScoreboard().getNullableObjective(
                        player.getUuid().toString() + REVIVE_TAG) == null
                    ) return;

                    // 獲取復活時間以及當前時間
                    Scoreboard scoreboard                 = player.getScoreboard();
                    ScoreboardObjective revive_objective  = player.getScoreboard()
                                                            .getNullableObjective(player.getUuid().toString() + REVIVE_TAG);
                    int revive_time                       = scoreboard.getScore(player, revive_objective).getScore();
                    
                    if( revive_time < now_time ){
                        respawnPlayer(player);
                        player.setCameraEntity(player);
                        player.changeGameMode(GameMode.SPECTATOR);

                        waitingToSwitchBack.put(
                            player.getUuid(),
                            now_time + OBSERVER_DURATION_SECONDS 
                        );

                        scoreboard.removeObjective(revive_objective);
                        player.sendMessage(Text.literal("復活成功，你將在 " + OBSERVER_DURATION_SECONDS + " 秒後重新進入生存模式。"), false);
                    }else if( revive_time > now_time ){
                        player.sendMessage(
                            Text.literal("剩餘復活時間:" + 
                                Integer.toString(revive_time - now_time)
                            ),
                        true);
                        addDeathDebuff(player, 3);
                    }
                });
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (observingBeforeRevive.contains(player.getUuid())) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (observingBeforeRevive.contains(player.getUuid())) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        
        watchPlayerCommand();
    }
    
    private void watchPlayerCommand(){
        /* 玩家觀戰指令，僅限死亡玩家能用 */
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            CommandManager.literal("watch")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                
                if (isPlayerReviving(player)) {
                    player.sendMessage(Text.literal("你還沒死"), false);
                    return 0;
                }
                
                player.setCameraEntity(player);
                player.changeGameMode(GameMode.SURVIVAL);
                respawnPlayer(player);
                player.sendMessage(Text.literal("已回復你的視角"), false);
                return 1;
            })
            .then(argument("target", EntityArgumentType.player())
                .executes(context -> {
                    ServerPlayerEntity viewer = context.getSource().getPlayer();

                    if (isPlayerReviving(viewer)) {
                        viewer.sendMessage(Text.literal("你還沒死"), false);
                        return 0;
                    }
 
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
                    if (viewer.equals(target)) {
                        viewer.sendMessage(Text.literal("不能觀察自己"), false);
                        return 0;
                    }
                    viewer.changeGameMode(GameMode.SPECTATOR);
                    viewer.setCameraEntity(target);
                    viewer.sendMessage(Text.literal("你現在正在觀察 " + target.getName().getString()), false);
                    return 1;
                })
            )
        ));
    }

    private boolean isPlayerReviving(ServerPlayerEntity player){
        if (player.getScoreboard().getNullableObjective(player.getUuid().toString() + REVIVE_TAG) == null
            || ( player.getScoreboard()
                .getOrCreateScore( player, player.getScoreboard().getNullableObjective(player.getUuid().toString() + REVIVE_TAG))
                .getScore() < (int)(System.currentTimeMillis()/1000L) )
        ) return true;
        return false;
    }

    private void addDeathDebuff(ServerPlayerEntity player, int duration){
        /* 幫玩家上死亡效果 */
        ArrayList<RegistryEntry<StatusEffect>> deathEffect = 
                new ArrayList<RegistryEntry<StatusEffect>>(Arrays.asList(
            StatusEffects.SLOWNESS,
            StatusEffects.REGENERATION,
            StatusEffects.RESISTANCE,
            StatusEffects.BLINDNESS,
            StatusEffects.JUMP_BOOST,
            StatusEffects.MINING_FATIGUE,
            StatusEffects.INVISIBILITY
        ));
        
        deathEffect.forEach((effect)->{    
            player.addStatusEffect(new StatusEffectInstance(
                effect, duration * 20, 128, false, false
            ));
        });
    }

    private void addRespawnBuff(ServerPlayerEntity player, int duration){
        /* 幫玩家上復活後的效果 */
        ArrayList<RegistryEntry<StatusEffect>> deathEffect = 
                new ArrayList<RegistryEntry<StatusEffect>>(Arrays.asList(
            StatusEffects.REGENERATION,
            StatusEffects.RESISTANCE
        ));
        
        deathEffect.forEach((effect)->{    
            player.addStatusEffect(new StatusEffectInstance(
                effect, duration * 20, 128, false, false
            ));
        });
    }

    private void setScoreboard(
        ServerPlayerEntity player,
        String objectiveName,
        int score
    ){
        /* 設定玩家的計分板 */
        ScoreboardObjective objective = player.getScoreboard().getNullableObjective(objectiveName);
        if ( objective == null ) {
            objective = player.getScoreboard().addObjective(
                objectiveName,
                ScoreboardCriterion.DUMMY,
                Text.literal(objectiveName),
                ScoreboardCriterion.RenderType.INTEGER,
                true,
                null
            );
        }
        player.getScoreboard().getOrCreateScore(player, objective).setScore(
            score
        );
    }

    private void showDeathMessage(ServerPlayerEntity player){
        /* 顯示死亡訊息 */
        player.networkHandler.sendPacket(
            new TitleS2CPacket(Text.of("§6你死了"))
        );
        player.networkHandler.sendPacket(
            new SubtitleS2CPacket(Text.of("§6復活剩餘" + Integer.toString(REVIVE_TIME) + "秒" ))
        );
    }

    private void respawnPlayer(ServerPlayerEntity player){
        /*
         * 重生玩家到原本的重生點
         * */
        player.requestRespawn();

        boolean isTeleportSuccess = false;
        if(player.getRespawn() != null){
            ServerWorld overworld = player.getServer().getWorld(World.OVERWORLD);
            BlockPos fallback = overworld.getSpawnPos();
            Set<PositionFlag> flags = EnumSet.noneOf(PositionFlag.class);
            isTeleportSuccess = player.teleport(
                player.getServer().getWorld(player.getRespawn().dimension()),
                player.getRespawn().pos().getX() + 0.5,
                player.getRespawn().pos().getY(),
                player.getRespawn().pos().getZ() + 0.5,
                flags,
                player.getYaw(),
                player.getPitch(),
                true
            );
        }
        player.setHealth(20.0f);
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0f);
        if(isTeleportSuccess) return;
        ServerWorld overworld = player.getServer().getWorld(World.OVERWORLD);
        BlockPos fallback = overworld.getSpawnPos();
        Set<PositionFlag> flags = EnumSet.noneOf(PositionFlag.class);
        player.teleport(
            overworld,
            fallback.getX() + 0.5, fallback.getY(), fallback.getZ() + 0.5,
            flags,
            player.getYaw(),
            player.getPitch(),
            true
        );
    }
    
    private void dropInventory(ServerPlayerEntity player) {
        /*
         * 將給定玩家身上所有物品全部丟出
         * */
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                player.dropItem(stack, true, false);
            }
        }
        player.getInventory().clear();
    }
}
