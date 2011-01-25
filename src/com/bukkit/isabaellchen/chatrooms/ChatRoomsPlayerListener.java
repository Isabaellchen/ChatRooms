package com.bukkit.isabaellchen.chatrooms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.server.Packet;
import net.minecraft.server.Packet3Chat;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.Server;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.Event.Type;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Handle events for all Player related events
 * @author <yourname>
 */
public class ChatRoomsPlayerListener extends PlayerListener {

    private final ChatRooms plugin;
    private ArrayList<Player> generalFocus;
    private HashMap<String, ChatRoom> focusMap;
    private ExecutorService worker;
    private RoomManagement man;

    public ChatRoomsPlayerListener(ChatRooms instance, Server server) {
        plugin = instance;

        this.generalFocus = new ArrayList<Player>();
        this.focusMap = new HashMap<String, ChatRoom>();
        this.worker = Executors.newSingleThreadExecutor();
        this.man = new RoomManagement(this.worker, server);
    }

    @Override
    public void onPlayerJoin(PlayerEvent event) {
        Player player = event.getPlayer();

        if (generalFocus.contains(player)) {
            generalFocus.remove(player);
        }
        generalFocus.add(player);

        if (focusMap.get(player.getName()) != null) {
            focusMap.remove(player.getName());
        }
    }

    @Override
    public void onPlayerQuit(PlayerEvent event) {
        Player player = event.getPlayer();

        man.leaveAllRooms(player);
        if (generalFocus.contains(player)) {
            generalFocus.remove(player);
        }
        if (focusMap.containsKey(player.getName())) {
            focusMap.remove(player.getName());
        }
    }

    @Override
    public void onPlayerChat(PlayerChatEvent event) {
        final Player player = event.getPlayer();
        final String message = event.getMessage();

        if (event.isCancelled()) {
            return;
        }

        if (generalFocus.contains(player)) {
            return;
        }

        event.setCancelled(true);

        Runnable runnable = new Runnable() {

            @Override
            public void run() {

                ChatRoom room = focusMap.get(player.getName());

                man.broadcast(room, player, message);

            }
        };

        worker.execute(runnable);


    }

    @Override
    public void onPlayerCommand(PlayerChatEvent event) {
        final Player player = event.getPlayer();
        final String[] split = event.getMessage().split(" ");

        //ChatRoomCommandThread commandThread = new ChatRoomCommandThread(player, split, generalFocus, focusMap, man);

        if (split.length == 2) {

            if (split[0].equals("/j") || split[0].equals("/join")) {
                if (split[1].equals("g") || split[1].equals("general")) {
                    player.sendMessage("The names g and general are reserved for the general chat.");
                    return;
                }
                man.joinRoom(split[1], player);
                if (generalFocus.contains(player)) {
                    generalFocus.remove(player);
                }
                focusMap.put(player.getName(), man.getRoom(split[1]));

            }

            if (split[0].equals("/l") || split[0].equals("/leave")) {
                man.leaveRoom(split[1], player);
                focusMap.remove(player.getName());
                generalFocus.add(player);

            }

            if (split[0].equals("/f") || split[0].equals("/focus")) {
                if (split[1].equals("general") || split[1].equals("g")) {
                    if (focusMap.containsKey(player.getName())) {
                        focusMap.remove(player.getName());
                    }
                    if (!generalFocus.contains(player)) {
                        generalFocus.add(player);
                    }
                    player.sendMessage("Switched focus to general Chat");

                } else {
                    if (generalFocus.contains(player)) {
                        generalFocus.remove(player);
                    }
                    if (man.roomExists(split[1]) && man.isConnected(player, split[1])) {
                        focusMap.put(player.getName(), man.getRoom(split[1]));
                        player.sendMessage("Switched focus to #" + split[1]);
                    } else {
                        player.sendMessage("Can not switch focus");
                    }

                }
            }

            if (split[0].equals("/chat")) {

                if (split[1].equals("list")) {
                    man.listRooms(player);

                }

                if (split[1].equals("cons")) {
                    man.getConnections(player);

                }

                if (split[1].equals("strict")) {
                    man.setStrict(true, player);

                }

                if (split[1].equals("loose")) {
                    man.setStrict(false, player);

                }

                if (split[1].equals("help")) {
                    player.sendMessage("\u00a76ChatRooms Help:");
                    player.sendMessage("\u00a7fBy default rooms are created upon joining and removed if they are empty and not made permanent.");
                    player.sendMessage("\u00a7fWhen server is strict, only serveradmins can create new rooms");
                    player.sendMessage("\u00a7b/join|/j <channelname> (<password>) \u00a7fto join a chatroom");
                    player.sendMessage("\u00a7b/leave|/l <channelname> \u00a7fto leave a chatroom");
                    player.sendMessage("\u00a7b/focus|/f <channelname> \u00a7fto chat in the desired room");
                    player.sendMessage("\u00a7b/focus|/f g|general \u00a7fto chat in general/global chat");
                    player.sendMessage("\u00a7b/cmsg <channelname> <message> \u007afto directly message a chatroom");
                    player.sendMessage("\u00a7b/chat list \u00a7fto list chatrooms");
                    player.sendMessage("\u00a7b/chat cons \u00a7fto list your current connections");
                    player.sendMessage("\u00a7b/chat who <channelname> \u00a7fto get a list of players in that room");
                    player.sendMessage("\u00a7b/chat adminhelp \u00a7fto get help as an admin of a room");
                }

                if (split[1].equals("adminhelp")) {
                    player.sendMessage("\u00a76ChatRooms Admin-Help:");
                    player.sendMessage("\u00a7b/chat kick <playername> (<reason>) \u00a7fto kick a player (and give areason)");
                    player.sendMessage("\u00a7b/chat ban <playername> (<reason>) \u00a7fto ban a player (and give areason)");
                    player.sendMessage("\u00a7b/chat unban <playername> \u00a7fto unban a player");
                    player.sendMessage("\u00a7b/chat admin <playername> \u00a7fto give admin rights to a player");
                    player.sendMessage("\u00a7b/chat revokeadmin <playername> \u00a7fto revoke admin rights on a player");
                    player.sendMessage("\u00a7b/chat password <password> \u00a7fto set a new password for the room");
                    player.sendMessage("\u00a7b/chat motd <message> \u00a7fto set a new MOTD for the room");
                    player.sendMessage("\u00a7b/chat color <color> \u00a7fto set a new textcolor for the room");
                }

                if (split[1].equals("serveradminhelp")) {
                    if (man.isServerAdmin(player)) {
                        player.sendMessage("\u00a76ChatRooms Server-Admin-Help:");
                        player.sendMessage("\u00a7fYou have adminrights on every channel!");
                        player.sendMessage("\u00a7b/chat perm <roomname> \u00a7froom exists even if empty or server gets reset");
                        player.sendMessage("\u00a7b/chat temp <roomname> \u00a7froom is removed when empty or server gets reset");
                        player.sendMessage("\u00a7b/chat remove <roomname> \u00a7fcompletely removes a room from the server");
                        player.sendMessage("\u00a7b/chat strict \u00a7fonly serveradmins can create new rooms");
                        player.sendMessage("\u00a7b/chat loose \u00a7feveryone can create new rooms");
                        player.sendMessage("\u00a7b/chat su <roomname> <playername> \u00a7fmakes a player superadmin of a room");
                    } else {
                        player.sendMessage("Only server admins need that");
                    }
                }
            }
        }

        if (split.length == 3) {

            if (split[0].equals("/j") || split[0].equals("/join")) {
                if (split[1].equals("g") || split[1].equals("general")) {
                    player.sendMessage("The names g and general are reserved for the general chat.");
                    return;
                }
                man.joinRoom(split[1], split[2], player);
                if (generalFocus.contains(player)) {
                    generalFocus.remove(player);
                }
                focusMap.put(player.getName(), man.getRoom(split[1]));

            }

            if (split[0].equals("/chat")) {

                if (split[1].equals("perm")) {
                    man.setPermanent(split[2], true, player);

                }

                if (split[1].equals("temp")) {
                    man.setPermanent(split[2], false, player);

                }

                if (split[1].equals("remove")) {
                    ArrayList<Player> orphans = man.killRoom(split[2], player);
                    if (orphans == null) {
                        return;
                    }
                    for (Player p : orphans) {
                        if (focusMap.get(p.getName()).getName().equals(split[2])) {
                            focusMap.remove(p.getName());
                            generalFocus.add(p);
                            p.sendMessage("#" + split[2] + " has been removed.");
                        }
                    }

                }

                if (split[1].equals("who")) {
                    String roomName = split[2];
                    if (!focusMap.containsKey(player.getName())) {
                        roomName = focusMap.get(player.getName()).getName();
                    }
                    man.listPlayers(roomName, player);

                }

                if (focusMap.containsKey(player.getName())) {
                    String roomName = focusMap.get(player.getName()).getName();

                    if (split[1].equals("admin")) {
                        man.makeAdmin(roomName, split[2], player);

                    }

                    if (split[1].equals("password")) {
                        man.setPasswd(roomName, split[2], player);

                    }

                    if (split[1].equals("revokeadmin")) {
                        man.revokeAdmin(roomName, split[2], player);

                    }

                    if (split[1].equals("color")) {
                        if (split[2].equals("black")) {
                            man.setTextColor(roomName, "\u00a70", player);

                        }
                        if (split[2].equals("blue")) {
                            man.setTextColor(roomName, "\u00a73", player);

                        }
                        if (split[2].equals("darkPurple")) {
                            man.setTextColor(roomName, "\u00a79", player);

                        }
                        if (split[2].equals("gold")) {
                            man.setTextColor(roomName, "\u00a76", player);

                        }
                        if (split[2].equals("gray")) {
                            man.setTextColor(roomName, "\u00a78", player);

                        }
                        if (split[2].equals("green")) {
                            man.setTextColor(roomName, "\u00a72", player);

                        }
                        if (split[2].equals("lightblue")) {
                            man.setTextColor(roomName, "\u00a7b", player);

                        }
                        if (split[2].equals("lightgray")) {
                            man.setTextColor(roomName, "\u00a77", player);

                        }
                        if (split[2].equals("lightgreen")) {
                            man.setTextColor(roomName, "\u00a7a", player);

                        }
                        if (split[2].equals("lightpurple")) {
                            man.setTextColor(roomName, "\u00a7d", player);

                        }
                        if (split[2].equals("navy")) {
                            man.setTextColor(roomName, "\u00a71", player);

                        }
                        if (split[2].equals("purple")) {
                            man.setTextColor(roomName, "\u00a75", player);

                        }
                        if (split[2].equals("red")) {
                            man.setTextColor(roomName, "\u00a74", player);

                        }
                        if (split[2].equals("rose")) {
                            man.setTextColor(roomName, "\u00a7c", player);

                        }
                        if (split[2].equals("yellow")) {
                            man.setTextColor(roomName, "\u00a7e", player);

                        }
                    }

                    if (split[1].equals("unban")) {
                        man.unban(roomName, split[2], player);

                    }
                }
            }
        }

        if (split.length >= 2) {

            if (split[0].equals("/cmsg")) {
                final String roomName = split[1];

                if (roomName.equals("general") || roomName.equals("g")) {

                    if (generalFocus.contains(player)) {

                        String message = "";
                        for (int i = 2; i < split.length; i++) {
                            message += split[i] + " ";
                        }
                        fakeChatMessage(player, message);
                        return;

                    } else {

                        String currentRoom = focusMap.get(player.getName()).getName();
                        focusMap.remove(player.getName());
                        generalFocus.add(player);

                        String message = "";
                        for (int i = 2; i < split.length; i++) {
                            message += split[i] + " ";
                        }
                        fakeChatMessage(player, message);

                        generalFocus.remove(player);
                        focusMap.put(player.getName(), man.getRoom(currentRoom));

                        return;
                    }
                }
                if (man.roomExists(roomName) && man.isConnected(player, roomName)) {

                    Runnable runnable = new Runnable() {

                        public void run() {
                            ChatRoom room = man.getRoom(roomName);
                            String message = "";
                            for (int i = 1; i < split.length; i++) {
                                message += split[i] + " ";
                            }
                            man.broadcast(room, player, message);
                        }
                    };
                    worker.execute(runnable);

                } else {
                    player.sendMessage("You need to be member of " + split[1] + "!");
                }
            }

            if (split[0].equals("/me")) {

                if (focusMap.containsKey(player.getName())) {
                    final String roomName = focusMap.get(player.getName()).getName();

                    Runnable runnable = new Runnable() {

                        public void run() {
                            ChatRoom room = man.getRoom(roomName);
                            String message = player.getDisplayName() + " ";
                            for (int i = 2; i < split.length; i++) {
                                message += split[i] + " ";
                            }
                            man.broadcastSysMsg(room, message);
                        }
                    };
                    worker.execute(runnable);
                }
            }

            if (split[0].equals("/chat")) {

                if (focusMap.containsKey(player.getName())) {
                    String roomName = focusMap.get(player.getName()).getName();

                    if (split[1].equals("motd")) {
                        String text = "";
                        for (int i = 2; i < split.length; i++) {
                            text += split[i] + " ";
                        }
                        man.setMotd(roomName, text, player);

                    }

                    if (split[1].equals("kick")) {
                        String reason = "";
                        for (int i = 3; i < split.length; i++) {
                            reason += split[i] + " ";
                        }
                        man.kick(roomName, split[2], reason, player);

                    }

                    if (split[1].equals("ban")) {
                        String reason = "";
                        for (int i = 3; i < split.length; i++) {
                            reason += split[i] + " ";
                        }
                        man.ban(roomName, split[2], reason, player);

                    }
                }
            }

            if (split[0].substring(0, 3).equals("/c-")) {
                final String roomName = split[0].substring(3, split[0].length());

                if (roomName.equals("general") || roomName.equals("g")) {

                    if (generalFocus.contains(player)) {

                        String message = "";
                        for (int i = 1; i < split.length; i++) {
                            message += split[i] + " ";
                        }
                        fakeChatMessage(player, message);
                        return;

                    } else {

                        String currentRoom = focusMap.get(player.getName()).getName();
                        focusMap.remove(player.getName());
                        generalFocus.add(player);

                        String message = "";
                        for (int i = 1; i < split.length; i++) {
                            message += split[i] + " ";
                        }
                        fakeChatMessage(player, message);

                        generalFocus.remove(player);
                        focusMap.put(player.getName(), man.getRoom(currentRoom));

                        return;
                    }
                }
                if (man.roomExists(roomName) && man.isConnected(player, roomName)) {

                    Runnable runnable = new Runnable() {

                        public void run() {
                            ChatRoom room = man.getRoom(roomName);
                            String message = "";
                            for (int i = 1; i < split.length; i++) {
                                message += split[i] + " ";
                            }
                            man.broadcast(room, player, message);
                        }
                    };
                    worker.execute(runnable);

                } else {
                    player.sendMessage("You need to be member of " + split[1] + "!");
                }
            }
        }

        if (split.length == 4) {

            if (split[0].equals("/chat")) {

                if (split[1].equals("su")) {
                    man.setSuperadmin(split[2], split[3], player);

                }

            }
        }
    }

    public void fakeChatMessage(Player player, String message) {

        PlayerChatEvent event = new PlayerChatEvent(Type.PLAYER_CHAT, player, message);
        message = String.format(event.getFormat(), event.getPlayer().getDisplayName(), event.getMessage());

        CraftPlayer cp = (CraftPlayer) player;
        cp.getHandle().b.f.a((Packet) new Packet3Chat(message));
    }
}
