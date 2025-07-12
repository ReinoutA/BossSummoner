package com.reinout;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.mcmonkey.sentinel.SentinelTrait;

import java.util.Objects;

public class Bossmob extends JavaPlugin implements Listener {

    private static final double MAX_HEALTH = 200.0;

    private NPC herobrine;
    private BossBar bossBar;
    private double currentHealth = MAX_HEALTH;
    private boolean enraged = false;
    private boolean summonedMinions = false;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Bossmob plugin enabled!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("spawnboss") && sender instanceof Player player) {
            spawnBoss(player.getLocation());
            return true;
        }
        return false;
    }

    private void spawnBoss(Location loc) {
        if (herobrine != null && herobrine.isSpawned()) {
            sendGlobalMessage("§cHerobrine is already active!");
            return;
        }

        herobrine = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "Herobrine");
        herobrine.spawn(loc);
        herobrine.setProtected(false);

        // Configure Sentinel
        SentinelTrait sentinel = herobrine.getOrAddTrait(SentinelTrait.class);
        sentinel.setHealth((float) MAX_HEALTH);
        sentinel.damage = 6;
        sentinel.attackRate = 10;
        sentinel.allTargets.targets.clear();
        sentinel.allTargets.targets.add("players");
        sentinel.respawnTime = -1;
        sentinel.armor = 4;

        // Weapon
        Objects.requireNonNull(((LivingEntity) herobrine.getEntity()).getEquipment())
                .setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

        loc.getWorld().spawnParticle(Particle.SMOKE_LARGE, loc, 30, 1, 1, 1, 0.05);
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1f, 1f);

        // Boss bar
        bossBar = Bukkit.createBossBar("§4Herobrine Health", BarColor.RED, BarStyle.SOLID);
        bossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }

        currentHealth = MAX_HEALTH;
        enraged = false;
        summonedMinions = false;

        sendGlobalMessage("§4Herobrine has appeared...");
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!isHerobrine(event.getEntity())) return;

        double damage = event.getFinalDamage();
        currentHealth = Math.max(0, currentHealth - Math.abs(damage));
        bossBar.setProgress(Math.max(0.0, currentHealth / MAX_HEALTH));

        getLogger().info("Herobrine took damage: " + damage + ", currentHealth: " + currentHealth);

        // Blind attacker if enraged
        if (enraged && event.getDamager() instanceof Player player) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 1));
            player.sendMessage("§cHerobrine's rage blinded you!");
        }

        // Trigger enraged state
        if (!enraged && currentHealth < MAX_HEALTH * 0.5) {
            enraged = true;
            sendGlobalMessage("§cHerobrine is enraged!");
            herobrine.getEntity().getWorld().playSound(
                    herobrine.getEntity().getLocation(),
                    Sound.ENTITY_ENDER_DRAGON_GROWL,
                    1.0f,
                    0.5f
            );
        }

        // Summon minions
        if (!summonedMinions && currentHealth < MAX_HEALTH * 0.25) {
            summonedMinions = true;
            sendGlobalMessage("§4Herobrine summons his minions!");
            for (int i = 0; i < 3; i++) {
                Skeleton minion = herobrine.getEntity().getWorld()
                        .spawn(herobrine.getEntity().getLocation(), Skeleton.class);
                minion.setCustomName("§7Herobrine's Minion");
                minion.setCustomNameVisible(true);
            }
            Location loc = herobrine.getEntity().getLocation();
            loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 20, 1, 1, 1, 0.05);
            loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1f, 1.5f);
        }

        // Defeated
        if (currentHealth <= 0) {
            sendGlobalMessage("§aHerobrine has been defeated!");
            herobrine.destroy();
            bossBar.removeAll();
        }
    }

    @EventHandler
    public void onBossAttack(EntityDamageByEntityEvent event) {
        if (herobrine == null || !herobrine.isSpawned()) return;
        if (!event.getDamager().getUniqueId().equals(herobrine.getEntity().getUniqueId())) return;

        if (event.getEntity() instanceof Player player) {
            player.setFireTicks(60); // 3 seconds fire
            player.sendMessage("§6Herobrine's touch burns!");
        }
    }

    private boolean isHerobrine(Entity entity) {
        return herobrine != null && herobrine.isSpawned()
                && entity.getUniqueId().equals(herobrine.getEntity().getUniqueId());
    }

    private void sendGlobalMessage(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }
}
