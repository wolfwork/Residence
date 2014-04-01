package net.t00thpick1.residence.protection.yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.t00thpick1.residence.ConfigManager;
import net.t00thpick1.residence.api.ResidenceAPI;
import net.t00thpick1.residence.api.areas.CuboidArea;
import net.t00thpick1.residence.api.areas.ResidenceArea;
import net.t00thpick1.residence.api.flags.Flag;
import net.t00thpick1.residence.api.flags.FlagManager;
import net.t00thpick1.residence.protection.MemoryEconomyManager;
import net.t00thpick1.residence.protection.MemoryResidenceArea;
import net.t00thpick1.residence.protection.yaml.YAMLResidenceManager.ChunkRef;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public class YAMLResidenceArea extends MemoryResidenceArea {
    private ConfigurationSection section;
    private YAMLCuboidArea area;

    public YAMLResidenceArea(ConfigurationSection section, YAMLResidenceArea parent) throws Exception {
        this.section = section;
        this.name = section.getName();
        if (parent == null) {
            this.fullName = name;
        } else {
            this.fullName = parent.fullName + "." + name;
        }
        this.parent = parent;
        ConfigurationSection data = section.getConfigurationSection("Data");
        this.owner = data.getString("Owner");
        this.creationDate = data.getLong("CreationDate");
        this.enterMessage = data.getString("EnterMessage");
        this.leaveMessage = data.getString("LeaveMessage");
        this.teleportLocation = loadTeleportLocation(data.getConfigurationSection("TPLocation"));
        this.area = new YAMLCuboidArea(data.getConfigurationSection("Area"));
        if (data.isConfigurationSection("RentData")) {
            ConfigurationSection rentData = data.getConfigurationSection("RentData");
            this.lastPayment = rentData.getLong("LastPayment");
            this.renter = rentData.getString("Renter");
            this.nextPayment = rentData.getLong("NextPayment");
            this.autoRenew = rentData.getBoolean("IsAutoRenew");
        } else {
            this.lastPayment = 0;
            this.renter = null;
            this.nextPayment = 0;
            this.autoRenew = false;
        }
        data = data.getConfigurationSection("MarketData");
        this.isRentable = data.getBoolean("ForRent");
        this.isBuyable = data.getBoolean("ForSale");
        this.cost = data.getInt("Cost");
        this.rentPeriod = data.getLong("RentPeriod", 0);
        initMarketState();
        areaFlags = new HashMap<Flag, Boolean>();
        data = section.getConfigurationSection("Flags");
        for (String flagKey : data.getKeys(false)) {
            Flag flag = FlagManager.getFlag(flagKey);
            areaFlags.put(flag, data.getBoolean(flagKey));
        }
        playerFlags = new HashMap<String, Map<Flag, Boolean>>();
        data = section.getConfigurationSection("Players");
        for (String player : data.getKeys(false)) {
            Map<Flag, Boolean> pFlags = new HashMap<Flag, Boolean>();
            ConfigurationSection flags = data.getConfigurationSection(player);
            for (String flagKey : flags.getKeys(false)) {
                Flag flag = FlagManager.getFlag(flagKey);
                pFlags.put(flag, data.getBoolean(flagKey));
            }
            playerFlags.put(player, pFlags);
        }
        rentFlags = new HashMap<Flag, Boolean>();
        data = section.getConfigurationSection("RentFlags");
        for (String flagKey : data.getKeys(false)) {
            Flag flag = FlagManager.getFlag(flagKey);
            rentFlags.put(flag, data.getBoolean(flagKey));
        }
        subzones = new HashMap<String, ResidenceArea>();
        data = section.getConfigurationSection("Subzones");
        for (String subzone : data.getKeys(false)) {
            subzones.put(subzone, new YAMLResidenceArea(data.getConfigurationSection(subzone), this));
        }
        if (getParent() == null) {
            loadRentLinks();
        }
    }

    private Location loadTeleportLocation(ConfigurationSection section) {
        return new Location(getWorld(), section.getDouble("X"), section.getDouble("Y"), section.getDouble("Z"));
    }

    public YAMLResidenceArea(ConfigurationSection section, YAMLCuboidArea area, String owner, YAMLResidenceArea parent) {
        this.section = section;
        this.area = area;
        this.name = section.getName();
        this.parent = parent;
        if (parent == null) {
            this.fullName = name;
        } else {
            this.fullName = parent.fullName + "." + name;
        }
        section.createSection("Data");
        this.owner = owner;
        this.creationDate = System.currentTimeMillis();
        this.enterMessage = YAMLGroupManager.getDefaultEnterMessage(owner);
        this.leaveMessage = YAMLGroupManager.getDefaultLeaveMessage(owner);
        section.createSection("Area");
        section.createSection("MarketData");
        this.isBuyable = false;
        this.isRentable = false;
        this.cost = 0;
        this.autoRenewEnabled = ConfigManager.getInstance().isAutoRenewDefault();
        this.teleportLocation = getCenter();
        section.createSection("TPLocation");
        this.areaFlags = new HashMap<Flag, Boolean>();
        section.createSection("Flags");
        this.playerFlags = new HashMap<String, Map<Flag, Boolean>>();
        section.createSection("Players");
        this.rentFlags = new HashMap<Flag, Boolean>();
        section.createSection("RentFlags");
        this.subzones = new HashMap<String, ResidenceArea>();
        section.createSection("Subzones");
        this.rentLinks = new HashMap<String, ResidenceArea>();
        section.createSection("RentLinks");
        applyDefaultFlags();
    }

    private void loadRentLinks() {
        rentLinks = new HashMap<String, ResidenceArea>();
        ConfigurationSection links = section.getConfigurationSection("RentLinks");
        for (String rentLink : links.getStringList("Links")) {
            rentLinks.put(rentLink, getTopParent().getSubzoneByName(rentLink));
        }
        for (ResidenceArea subzone : subzones.values()) {
            ((YAMLResidenceArea) subzone).loadRentLinks();
        }
    }

    private void initMarketState() {
        MemoryEconomyManager econ = (MemoryEconomyManager) ResidenceAPI.getEconomyManager();
        if (isRented()) {
            econ.setRented(this);
        }
        if (isForRent()) {
            econ.setForRent(this);
        }
        if (isForSale()) {
            econ.setForSale(this);
        }
    }

    public boolean createSubzone(String name, String owner, CuboidArea area) {
        if (subzones.containsKey(name)) {
            return false;
        }
        if (!isAreaWithin(area)) {
            return false;
        }
        for (ResidenceArea subzone : getSubzoneList()) {
            if (subzone.checkCollision(area)) {
                return false;
            }
        }
        ConfigurationSection subzoneSection = section.getConfigurationSection("Subzones");
        try {
            subzones.put(name, new YAMLResidenceArea(subzoneSection.createSection(name), (YAMLCuboidArea) area, owner, this));
        } catch (Exception e) {
            subzoneSection.set(name, null);
            subzones.remove(name);
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean removeSubzone(String subzone) {
        if (subzones.remove(subzone) == null) {
            return false;
        }
        ConfigurationSection subzoneSection = section.getConfigurationSection("Subzones");
        subzoneSection.set(subzone, null);
        return true;
    }

    public boolean rename(String newName) {
        if (parent == null) {
            if (((YAMLResidenceManager) ResidenceAPI.getResidenceManager()).rename(this, newName)) {
                this.name = newName;
                return true;
            }
            return false;
        } else {
            ResidenceArea parent = getParent();
            if (parent.renameSubzone(name, newName)) {
                name = newName;
                return true;
            }
            return false;
        }
    }

    public boolean renameSubzone(String oldName, String newName) {
        if (!subzones.containsKey(oldName)) {
            return false;
        }
        if (subzones.containsKey(newName)) {
            return false;
        }
        ConfigurationSection subzoneSection = section.getConfigurationSection("Subzones");
        ConfigurationSection newSection = subzoneSection.createSection(newName);
        subzoneSection.set(oldName, null);
        YAMLResidenceArea area = (YAMLResidenceArea) subzones.remove(oldName);
        area.newSection(newSection);
        subzones.put(newName, area);
        return true;
    }

    @Override
    public boolean isAreaWithin(CuboidArea area) {
        return this.area.isAreaWithin(area);
    }

    @Override
    public boolean containsLocation(Location loc) {
        return this.area.containsLocation(loc);
    }

    @Override
    public boolean containsLocation(World world, int x, int y, int z) {
        return this.area.containsLocation(world, x, y, z);
    }

    @Override
    public boolean checkCollision(CuboidArea area) {
        return this.area.checkCollision(area);
    }

    @Override
    public long getSize() {
        return this.area.getSize();
    }

    @Override
    public int getXSize() {
        return this.area.getXSize();
    }

    @Override
    public int getYSize() {
        return this.area.getYSize();
    }

    @Override
    public int getZSize() {
        return this.area.getZSize();
    }

    @Override
    public Location getHighLocation() {
        return this.area.getHighLocation();
    }

    @Override
    public Location getLowLocation() {
        return this.area.getLowLocation();
    }

    @Override
    public World getWorld() {
        return this.area.getWorld();
    }

    @Override
    public Location getCenter() {
        return this.area.getCenter();
    }

    @Override
    public int getHighX() {
        return this.area.getHighX();
    }

    @Override
    public int getHighY() {
        return this.area.getHighY();
    }

    @Override
    public int getHighZ() {
        return this.area.getHighZ();
    }

    @Override
    public int getLowX() {
        return this.area.getLowX();
    }

    @Override
    public int getLowY() {
        return this.area.getLowY();
    }

    @Override
    public int getLowZ() {
        return this.area.getLowZ();
    }

    public void save() {
        ConfigurationSection data = section.getConfigurationSection("Data");
        data.set("Owner", owner);
        data.set("CreationDate", creationDate);
        data.set("EnterMessage", enterMessage);
        data.set("LeaveMessage", leaveMessage);
        ConfigurationSection tploc = data.getConfigurationSection("TPLocation");
        tploc.set("X", teleportLocation.getX());
        tploc.set("Y", teleportLocation.getX());
        tploc.set("Z", teleportLocation.getX());
        area.save(data.getConfigurationSection("Area"));
        if (isRented()) {
            ConfigurationSection rentData = data.createSection("RentData");
            rentData.set("LastPayment", lastPayment);
            rentData.getString("Renter", renter);
            rentData.getLong("NextPayment", nextPayment);
            rentData.getBoolean("IsAutoRenew", autoRenew);
        } else {
            data.set("RentData", null);
        }
        data = data.getConfigurationSection("MarketData");
        data.set("ForRent", isRentable);
        data.set("ForSale", isBuyable);
        data.set("Cost", cost);
        data.set("RentPeriod", rentPeriod);
        data = section.getConfigurationSection("Flags");
        for (Entry<Flag, Boolean> flag : areaFlags.entrySet()) {
            data.set(flag.getKey().getName(), flag.getValue());
        }
        data = section.getConfigurationSection("Players");
        for (Entry<String, Map<Flag, Boolean>> player : playerFlags.entrySet()) {
            if (!data.isConfigurationSection(player.getKey())) {
                data.createSection(player.getKey());
            }
            ConfigurationSection playerSection = data.getConfigurationSection(player.getKey());
            for (Entry<Flag, Boolean> flag : player.getValue().entrySet()) {
                playerSection.set(flag.getKey().getName(), flag.getValue());
            }
        }
        data = section.getConfigurationSection("RentFlags");
        for (Entry<Flag, Boolean> flag : rentFlags.entrySet()) {
            data.set(flag.getKey().getName(), flag.getValue());
        }
        data = section.getConfigurationSection("Subzones");
        for (ResidenceArea subzone : subzones.values()) {
            ((YAMLResidenceArea) subzone).save();
        }
        data = section.getConfigurationSection("RentLinks");
        List<String> rentLink = new ArrayList<String>(rentLinks.keySet());
        data.set("Links", rentLink);
    }

    public List<ChunkRef> getChunks() {
        return area.getChunks();
    }

    public void newSection(ConfigurationSection newSection) {
        this.section = newSection;
        section.createSection("Data");
        section.createSection("Area");
        section.createSection("MarketData");
        section.createSection("TPLocation");
        section.createSection("Flags");
        section.createSection("Players");
        section.createSection("RentFlags");
        section.createSection("Subzones");
        section.createSection("RentLinks");
        ConfigurationSection subzoneSection = section.createSection("Subzones");
        for (Entry<String, ResidenceArea> subzone : subzones.entrySet()) {
            ((YAMLResidenceArea) subzone.getValue()).newSection(subzoneSection.createSection(subzone.getKey()));
        }
    }
}