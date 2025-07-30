package com.suff.beaconrangeplus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Beacon;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;

public class BeaconRangePlus extends JavaPlugin implements Listener {

    private final Map<Location, Integer> beaconRanges = new HashMap<>();
    private int defaultRange = 50; // Default range if not configured

    @Override
    public void onEnable() {
        // Load config
        saveDefaultConfig();
        defaultRange = getConfig().getInt("default-range", 50);

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Schedule repeating task to update beacon effects
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::updateBeaconEffects, 0L, 100L);

        getLogger().info("Beacon Range Plus enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Beacon Range Plus disabled!");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType().toString().contains("BEACON")) {
            // Check if this beacon has a custom range in config
            Location loc = event.getBlock().getLocation();
            String locKey = locationToString(loc);
            int range = getConfig().getInt("beacons." + locKey, defaultRange);
            beaconRanges.put(loc, range);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Scan for beacons in loaded chunks
        for (var blockState : event.getChunk().getTileEntities()) {
            if (blockState instanceof Beacon) {
                Location loc = blockState.getLocation();
                String locKey = locationToString(loc);
                int range = getConfig().getInt("beacons." + locKey, defaultRange);
                beaconRanges.put(loc, range);
            }
        }
    }

    private void updateBeaconEffects() {
        for (Map.Entry<Location, Integer> entry : beaconRanges.entrySet()) {
            Location loc = entry.getKey();
            int range = entry.getValue();

            if (!(loc.getBlock().getState() instanceof Beacon)) {
                beaconRanges.remove(loc);
                continue;
            }

            Beacon beacon = (Beacon) loc.getBlock().getState();
            PotionEffect primaryEffect = beacon.getPrimaryEffect();

            if (primaryEffect != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getLocation().distance(loc) <= range) {
                        player.addPotionEffect(new PotionEffect(
                                primaryEffect.getType(),
                                primaryEffect.getDuration(),
                                primaryEffect.getAmplifier(),
                                primaryEffect.isAmbient(),
                                primaryEffect.hasParticles(),
                                primaryEffect.hasIcon()
                        ));
                    }
                }
            }
        }
    }

    private String locationToString(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("setbeaconrange")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                return true;
            }

            Player player = (Player) sender;

            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Usage: /setbeaconrange <range>");
                return true;
            }

            try {
                int range = Integer.parseInt(args[0]);
                if (range < 10 || range > 200) {
                    player.sendMessage(ChatColor.RED + "Range must be between 10 and 200!");
                    return true;
                }

                Block target = player.getTargetBlockExact(10);
                if (target == null || !target.getType().toString().contains("BEACON")) {
                    player.sendMessage(ChatColor.RED + "You must be looking at a beacon!");
                    return true;
                }

                Location loc = target.getLocation();
                beaconRanges.put(loc, range);

                // Save to config
                String locKey = locationToString(loc);
                getConfig().set("beacons." + locKey, range);
                saveConfig();

                player.sendMessage(ChatColor.GREEN + "Beacon range set to " + range + " blocks!");
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid number format!");
            }

            return true;
        }
        return false;
    }
}