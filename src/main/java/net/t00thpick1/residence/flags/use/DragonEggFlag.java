package net.t00thpick1.residence.flags.use;

import net.t00thpick1.residence.Residence;
import net.t00thpick1.residence.api.Flag;
import net.t00thpick1.residence.api.FlagManager;
import net.t00thpick1.residence.api.ResidenceAPI;
import net.t00thpick1.residence.locale.LocaleLoader;
import net.t00thpick1.residence.utils.Utilities;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class DragonEggFlag extends Flag implements Listener {
    private DragonEggFlag(String flag, FlagType type, Flag parent) {
        super(flag, type, parent);
    }

    public static final DragonEggFlag FLAG = new DragonEggFlag(LocaleLoader.getString("Flags.Flags.DragonEgg"), FlagType.ANY, UseFlag.FLAG);

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack handItem = player.getItemInHand();
        if (handItem != null && Utilities.isTool(handItem.getType())) {
            return;
        }
        if (Utilities.isAdminMode(player)) {
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Material mat = block.getType();
        if (mat != Material.DRAGON_EGG) {
            return;
        }
        if (!ResidenceAPI.getPermissionsAreaByLocation(block.getLocation()).allowAction(player, this)) {
            event.setCancelled(true);
            player.sendMessage(LocaleLoader.getString("Flags.Messages.FlagDeny", LocaleLoader.getString("Flags.Messages.UseFlagDeny", getName())));
        }
    }

    public static void initialize() {
        FlagManager.addFlag(FLAG);
        Plugin plugin = Residence.getInstance();
        plugin.getServer().getPluginManager().registerEvents(FLAG, plugin);
    }
}