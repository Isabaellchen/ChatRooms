
package com.bukkit.isabaellchen.chatrooms;

import java.io.File;
import java.util.HashMap;
import org.bukkit.Player;
import org.bukkit.Server;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;

/**
 * ChatRooms for Bukkit
 *
 * @author <yourname>
 */
public class ChatRooms extends JavaPlugin {
    private final ChatRoomsPlayerListener playerListener;
    private final HashMap<Player, Boolean> debugees = new HashMap<Player, Boolean>();

    public ChatRooms(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File plugin, ClassLoader cLoader) {
        super(pluginLoader, instance, desc, plugin, cLoader);

        this.playerListener = new ChatRoomsPlayerListener(this, instance);

        // NOTE: Event registration should be done in onEnable not here as all events are unregistered when a plugin is disabled
    }



    public void onEnable() {
        // TODO: Place any custom enable code here including the registration of any events

        // Register our events
        PluginManager pm = getServer().getPluginManager();

        pm.registerEvent(Event.Type.PLAYER_CHAT, playerListener , Priority.Normal, this);
        pm.registerEvent(Event.Type.PLAYER_COMMAND, playerListener , Priority.Normal, this);
        pm.registerEvent(Event.Type.PLAYER_JOIN, playerListener , Priority.Normal, this);
        pm.registerEvent(Event.Type.PLAYER_QUIT, playerListener , Priority.Normal, this);


        // EXAMPLE: Custom code, here we just output some info so we can check all is well
        PluginDescriptionFile pdfFile = this.getDescription();
        System.out.println( pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!" );
    }
    public void onDisable() {
        // TODO: Place any custom disable code here

        // NOTE: All registered events are automatically unregistered when a plugin is disabled

        // EXAMPLE: Custom code, here we just output some info so we can check all is well
        System.out.println("Goodbye world!");
    }
    public boolean isDebugging(final Player player) {
        if (debugees.containsKey(player)) {
            return debugees.get(player);
        } else {
            return false;
        }
    }

    public void setDebugging(final Player player, final boolean value) {
        debugees.put(player, value);
    }
}
