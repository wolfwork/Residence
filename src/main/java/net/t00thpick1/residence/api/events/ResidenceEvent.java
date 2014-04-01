package net.t00thpick1.residence.api.events;

import net.t00thpick1.residence.api.areas.PermissionsArea;
import org.bukkit.event.Event;

public abstract class ResidenceEvent extends Event {
    private final PermissionsArea residence;

    public ResidenceEvent(PermissionsArea residence) {
        this.residence = residence;
    }

    public final PermissionsArea getPermissionsArea() {
        return residence;
    }
}