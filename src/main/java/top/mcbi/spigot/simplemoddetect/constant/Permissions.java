package top.mcbi.spigot.simplemoddetect.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.permissions.Permissible;

@Getter
@RequiredArgsConstructor
public enum Permissions {
    BYPASS_PERMISSION("simplemoddetect.bypass"),
    NOTIFY_PERMISSION("simplemoddetect.notify"),
    RELOAD_PERMISSION("simplemoddetect.reload"),
    CHECK_PERMISSION("simplemoddetect.check"),
    LIST_PERMISSION("simplemoddetect.list"),
    DEBUG_PERMISSION("simplemoddetect.debug");

    private final String node;

    public boolean has(Permissible permissible) {
        return permissible.hasPermission(node);
    }
}
