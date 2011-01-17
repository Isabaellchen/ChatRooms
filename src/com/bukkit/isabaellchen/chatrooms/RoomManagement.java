package com.bukkit.isabaellchen.chatrooms;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.bukkit.entity.Player;
import org.bukkit.Server;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Manages actions on chatrooms and validates them.
 *
 * @author Isa
 */
public class RoomManagement {

    /**
     * Configuration for the xml file and datastrucure
     */
    private static final String PATH_TO_XML = "plugins/ChatRoomList.xml";
    private static final String XML_ROOT = "rooms";
    private static final String XML_ROOM = "room";
    private static final String XML_ROOM_NAME = "name";
    private static final String XML_PASSWORD = "password";
    private static final String XML_MOTD = "motd";
    private static final String XML_COLOR = "color";
    private static final String XML_ADMINS = "admins";
    private static final String XML_BANS = "bans";
    private static final String XML_SUPER_ADMIN = "sa";
    /**
     * HashMap of the currently exxisting ChatRooms.
     */
    private HashMap<String, ChatRoom> rooms;
    /**
     * Holds information to which ChatRooms a player is connected to.
     */
    private HashMap<String, ArrayList<ChatRoom>> connections;
    /**
     * When strict mode is enabled only serveradmins can create channels upon joining.
     */
    private Boolean strict;
    private ArrayList<String> admins;
    private String format;
    //private PropertiesFile config
    private static final String PATH_TO_CONFIG = "ChatRooms.properties";
    private static final String STRICT_MODE = "strict_channel_creation";
    private static final String SERVER_ADMIN_LIST = "adminnames";
    private static final String MESSAGE_FORMAT = "message_format";
    private ExecutorService worker;
    private Server server;
    private Properties config;

    public RoomManagement() {
    }

    RoomManagement(ExecutorService worker, Server server) {
        this.worker = worker;
        this.server = server;

        rooms = new HashMap<String, ChatRoom>();
        connections = new HashMap<String, ArrayList<ChatRoom>>();
        admins = new ArrayList<String>();

        config = new Properties();

        if (!new File(PATH_TO_CONFIG).exists()) {
            config.setProperty(STRICT_MODE, Boolean.FALSE.toString());
            config.setProperty(SERVER_ADMIN_LIST, "");
            config.setProperty(MESSAGE_FORMAT, "%1$s[%2$s] %3$s: %4$s");
            try {
                config.store(new FileOutputStream(PATH_TO_CONFIG), null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        try {
            config.load(new FileInputStream(PATH_TO_CONFIG));

            strict = Boolean.valueOf(config.getProperty(STRICT_MODE));
            String[] temp = config.getProperty(SERVER_ADMIN_LIST).split(",");

            for (String a : temp) {
                admins.add(a);
            }

            format = config.getProperty(MESSAGE_FORMAT);

        } catch (Exception e) {
            e.printStackTrace();
        }


        readRooms();
    }

    /**
     * Creates a new ChatRoom
     * @param roomName Name of the new room
     * @param password Password for the new room
     * @param player The player who attempts to create the new room.
     */
    public void createRoom(String roomName, String password, Player player) {

        ChatRoom room = null;

        if (password == null) {
            room = new ChatRoom(roomName, player);
        } else {
            room = new ChatRoom(roomName, password, player);
        }

        rooms.put(roomName, room);
    }

    /**
     * Removes an existing room.
     * @param roomName The name of the room that should be deleted
     * @return if the room was deleted
     */
    public boolean removeRoom(String roomName) {
        if (roomExists(roomName)) {
            rooms.remove(roomName);
            deleteRoomFromFile(roomName);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Makes a player join a room and sets him on the list of receivers for the
     * desired chatroom
     * @param roomName Name of the room to be entered
     * @param player The player who wants to join the specified room
     * @return if joining the room was succesful
     */
    public boolean joinRoom(String roomName, Player player) {
        return joinRoom(roomName, "", player);
    }

    /**
     * Makes a player join a room and sets him on the list of receivers for the
     * desired chatroom
     * @param roomName Name of the room to be entered
     * @param password Password for the room
     * @param player The player who wants to join the specified room
     * @return if joining the room was succesful
     */
    public boolean joinRoom(String roomName, String password, Player player) {

        if (!roomExists(roomName)) {
            if (!strict || /*player.isAdmin()*/ isServerAdmin(player)) {
                createRoom(roomName, password, player);
                player.sendMessage("Room " + roomName + " created, you are now the owner of this room.");
            } else {
                player.sendMessage("The room does not exist.");
                return false;
            }
        }

        ChatRoom room = rooms.get(roomName);

        //see if the player is banned
        if (room.getBans().contains(player.getName())) {
            player.sendMessage("You are banned from this channel.");
            return false;
        }

        //The actual process of entering
        if (password.equals(room.getPassword()) || player.getName().equals(room.getSuperAdmin())) {
            room.addUser(player);
            addConnection(player, room);
            player.sendMessage(room.getColor() + room.getMotd());
            broadcastSysMsg(room, player.getName() + " joined.");
            return true;
        } else {
            player.sendMessage("Wrong password.");
            return false;
        }
    }

    /**
     * Makes a player leave a room and unregisters the player from the list
     * of receivers
     * @param roomName Name of the room to be left
     * @param player Player that is leaving
     * @return if leaving the room was succesful
     */
    public boolean leaveRoom(String roomName, Player player) {

        ChatRoom room = rooms.get(roomName);

        if (room.getUsers().contains(player)) {
            room.removeUser(player);
            removeConnection(player, room);
            broadcastSysMsg(room, player.getName() + "has left");
            player.sendMessage("You left #" + roomName + ".");

            //If the room is empty and was not set to be permanent,
            //the room gets deleted
            if (room.getUsers().isEmpty() && !room.isPermanent()) {
                removeRoom(roomName);
            }
            return true;
        } else {
            player.sendMessage("You are no member of this channel.");
            return false;
        }

    }

    /**
     * Makes a player leave all chatrooms he was connected to, called on disconnect
     * @param player Player that is leaving all rooms he connected to
     */
    public void leaveAllRooms(Player player) {
        if (connections.containsKey(player.getName())) {
            ArrayList<ChatRoom> activeRooms = connections.get(player.getName());

            for (ChatRoom r : activeRooms) {
                r.removeUser(player);
            }
        }
    }

    /**
     * Promotes a player to an admin for the room this action was called in.
     * Only players in that room can be promoted to admins
     * This action is relative to the focus of the executing player
     * @param roomName Name of the room the Player becomes admin in
     * @param playerName The name of the PLayer that is going to be admin
     * @param player The player who wants to promote a player
     * @return if the promotion was succesful
     */
    public boolean makeAdmin(String roomName, String playerName, Player player) {

        ChatRoom room = rooms.get(roomName);

        //Only admins can promote admins
        if (!isAdmin(room, player)) {
            player.sendMessage("You don't have admin rights for this channel.");
            return false;
        }

        Player soonToBeAdmin = getPlayerByName(room, playerName);

        if (soonToBeAdmin == null) {
            player.sendMessage("Player " + playerName + " is no member of this channel.");
            return false;
        }

        if (room.getAdmins().contains(playerName) || /*soonToBeAdmin.isAdmin()*/ isServerAdmin(soonToBeAdmin)) {
            player.sendMessage("Player " + playerName + " is already admin of this channel.");
            return false;
        }

        room.addAdmin(soonToBeAdmin.getName());

        if (room.isPermanent()) {
            editRoomDocument(roomName, XML_ADMINS, room.getAdmins().toString());
        }

        broadcastSysMsg(room, player.getName() + " gave adminrights to " + playerName);

        return true;
    }

    /**
     * Revokes adminrights on a player in a chatroom
     * This action is relative to the focus of the executing player
     * @param roomName Name of the room this action takes place in
     * @param playerName The player to get amdinrights revoked
     * @param player The player who is trying to revoke adminrights on a player
     * @return if the action was succesful
     */
    public boolean revokeAdmin(String roomName, String playerName, Player player) {

        ChatRoom room = rooms.get(roomName);

        //Only admins for a channel can revoke adminrights.
        if (!isAdmin(room, player)) {
            player.sendMessage("You don't have admin rights for this channel.");
            return false;
        }

        String soonAdminNoMore = null;

        for (String p : room.getAdmins()) {
            if (p.equals(playerName)) {
                soonAdminNoMore = p;
                break;
            }
        }

        if (soonAdminNoMore == null) {
            player.sendMessage("Player " + playerName + " is no admin of this channel.");
            return false;
        }

        //Superadmins (selfexplanatory)
        if (soonAdminNoMore.equals(room.getSuperAdmin())) {
            player.sendMessage("Cannot revoke admin rights on superadmin");
            return false;
        }

        room.removeAdmin(soonAdminNoMore);

        if (room.isPermanent()) {
            editRoomDocument(roomName, XML_ADMINS, room.getAdmins().toString());
        }

        broadcastSysMsg(room, player.getName() + " revoked adminrights for " + playerName);

        return true;
    }

    /**
     * Set the Textcolor for a channel, only channeladmins can do that
     * This action is relative to the focus of the executing player
     * @param roomName The room to get a new color
     * @param color The desired Color
     * @param player The Player who wants to change the color
     * @return if the action was succesful
     */
    public boolean setTextColor(String roomName, String color, Player player) {

        ChatRoom room = rooms.get(roomName);

        if (!isAdmin(room, player)) {
            player.sendMessage("You don't have admin rights for this channel.");
            return false;
        }

        if (color.equals("\u00a7f")) {
            player.sendMessage("The color White is reserved for the general chat");
            return false;
        }

        room.setColor(color.toString());

        if (room.isPermanent()) {
            editRoomDocument(roomName, XML_COLOR, color);
        }

        player.sendMessage("New color for #" + roomName + " set.");

        return true;
    }

    /**
     * Set the Message of the day for a channel, only channeladmins can do that
     * This action is relative to the focus of the executing player
     * @param roomName Name of the room that schould get a new MOTD
     * @param motd The new MOTD
     * @param player The player who wants to set the MOTD
     * @return if the action was succesful
     */
    public boolean setMotd(String roomName, String motd, Player player) {

        ChatRoom room = rooms.get(roomName);

        if (!isAdmin(room, player)) {
            player.sendMessage("You don't have admin rights for this channel.");
            return false;
        }

        room.setMotd(motd);

        if (room.isPermanent()) {
            editRoomDocument(roomName, XML_MOTD, motd);
        }

        player.sendMessage("New MOTD for #" + roomName + " set.");

        return true;
    }

    /**
     * Set a new password for the channel, only admins can do that.
     * This action is relative to the focus of the executing player
     * @param roomName The name of the room that should get a new password
     * @param password The new password
     * @param player The player who wants to set the new password
     * @return if the action was succesful
     */
    public boolean setPasswd(String roomName, String password, Player player) {

        ChatRoom room = rooms.get(roomName);

        if (!isAdmin(room, player)) {
            player.sendMessage("You don't have admin rights for this channel.");
            return false;
        }

        room.setPassword(password);

        if (room.isPermanent()) {
            editRoomDocument(roomName, XML_PASSWORD, password);
        }

        player.sendMessage("You set " + password + " as the new Password for #" + roomName);

        return true;
    }

    /**
     * Kicks a player from a channel. He has to login again to read and write in that channel
     * This action is relative to the focus of the executing player
     * @param roomName Name of the room the player is kicked out of
     * @param playerName Name of the player that should be kicked
     * @param reason (optional) give a reason why he was kicked
     * @param player The player who wants to kick someone out
     * @return if the action was succesful
     */
    public boolean kick(String roomName, String playerName, String reason, Player player) {

        ChatRoom room = rooms.get(roomName);

        if (!isAdmin(room, player)) {
            player.sendMessage("You don't have admin rights for this channel.");
            return false;
        }

        Player affectedPlayer = server.getPlayer(playerName);

        if (isAdmin(room, affectedPlayer)) {
            player.sendMessage("You can not kick channel admins.");
            return false;
        }

        Player soonToBeKicked = getPlayerByName(room, playerName);
        room.removeUser(soonToBeKicked);
        soonToBeKicked.sendMessage("You were kicked from channel " + roomName
                + " by " + player.getName() + ": " + reason);

        broadcastSysMsg(room, player.getName() + " kicked " + playerName);
        player.sendMessage("You kicked " + playerName + " from #" + roomName + ".");

        return true;
    }

    /**
     * Bans a player from a channel.
     * This action is relative to the focus of the executing player
     * @param roomName Name of the room the player is banned
     * @param playerName Name of the player that should be banned
     * @param reason (optional) give a reason why he was banned
     * @param player The player who wants to ban someone
     * @return if the action was succesful
     */
    public boolean ban(String roomName, String playerName, String reason, Player player) {

        ChatRoom room = rooms.get(roomName);

        if (!isAdmin(room, player)) {
            player.sendMessage("You don't have admin rights for this channel.");
            return false;
        }

        Player affectedPlayer = server.getPlayer(playerName);

        if (isAdmin(room, affectedPlayer)) {
            player.sendMessage("You can not ban channel admins.");
            return false;
        }

        Player soonToBeBanned = getPlayerByName(room, playerName);
        room.removeUser(soonToBeBanned);
        room.addBan(playerName);

        if (room.isPermanent()) {
            editRoomDocument(roomName, XML_BANS, room.getBans().toString());
        }

        soonToBeBanned.sendMessage("You were banned from channel " + roomName
                + " by " + player.getName() + ": " + reason);

        broadcastSysMsg(room, player.getName() + " banned " + playerName);
        return true;
    }

    /**
     * Lifts the ban on a player for a channel
     * @param roomName Name of the channel the player should be unbanned
     * @param playerName The player to be unbanned
     * @param player The player that wants to unban
     * @return if the action was succesful
     */
    public boolean unban(String roomName, String playerName, Player player) {

        ChatRoom room = rooms.get(roomName);

        if (!isAdmin(room, player)) {
            player.sendMessage("You don't have admin rights for this channel.");
            return false;
        }

        if (!room.getBans().contains(playerName)) {
            player.sendMessage("Player is not banned on this channel.");
            return false;
        }

        room.removeBan(playerName);

        if (room.isPermanent()) {
            editRoomDocument(roomName, XML_BANS, room.getBans().toString());
        }

        player.sendMessage("You unbanned " + playerName + " from #" + roomName + ".");

        return true;
    }

    /**
     * Check if a room exists
     * @param roomName The name of the room
     * @return if the room exists
     */
    public boolean roomExists(String roomName) {
        return rooms.containsKey(roomName);
    }

    /**
     * Check if a player is admin of a certain room
     * @param room The room to check
     * @param player The player to check
     * @return if the player is admin in that room
     */
    public boolean isAdmin(ChatRoom room, Player player) {
        if (room.getAdmins().contains(player.getName()) || /*player.isAdmin()*/ isServerAdmin(player)) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isServerAdmin(Player player) {
        String name = player.getName();
        return admins.contains(name);
    }

    /**
     * Sets a channel permanent if true or temporary if false.
     * Permanent channels dont get deleted upon server reboot or if they are empty.
     * Only Serveradmins can make channels permanent.
     * @param roomName Name of the desired room
     * @param player The player who wants to make the channel permanent
     */
    public void setPermanent(String roomName, Boolean permanent, Player player) {
        if (!/*player.isAdmin()*/isServerAdmin(player)) {
            player.sendMessage("Only serveradmins can make channels permanent/temporary");
            return;
        }

        if (!roomExists(roomName)) {
            player.sendMessage("The channel " + roomName + " does not exist.");
            return;
        }

        ChatRoom room = rooms.get(roomName);

        if (permanent) {
            if (!room.isPermanent()) {
                saveRoom(room);
            }

            room.setPermanent(true);
            player.sendMessage("Channel " + roomName + " is now permanent.");

        } else {
            if (room.isPermanent()) {
                deleteRoomFromFile(roomName);
            }
            
            room.setPermanent(false);
            player.sendMessage("Channel " + roomName + " is no longer Permanent.");

        }
    }

    /**
     * If strict is true only serveradmins can create new channels
     * @param strict True for strict room creation, false for loose room creation
     * @param playerThe player who wants to set this configuration
     */
    public void setStrict(Boolean strict, Player player) {
        if (!/*player.isAdmin()*/isServerAdmin(player)) {
            player.sendMessage("Only serveradmins can set strict/loose config");
            return;
        }
        this.strict = strict;
        config.setProperty(STRICT_MODE, strict.toString());
        try {
            config.store(new FileOutputStream(PATH_TO_CONFIG), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (strict) {
            player.sendMessage("Only server admins can create chatrooms");
        } else {
            player.sendMessage("Everyone can create chatrooms");
        }
    }

    /**
     * Set a new Superadmin for a channel
     * Only serveradmins can set new superadmins
     * @param roomName Name of the desired room
     * @param playerName Name of the new superadmin
     * @param player The player who wants to set a new superadmin
     */
    public void setSuperadmin(String roomName, String playerName, Player player) {
        if (!/*player.isAdmin()*/isServerAdmin(player)) {
            player.sendMessage("Only serveradmins can set superadmins.");
            return;
        }

        if (!roomExists(roomName)) {
            player.sendMessage("The room does not exist.");
            return;
        }

        ChatRoom room = rooms.get(roomName);
        room.setSuperAdmin(playerName);
        room.addAdmin(playerName);

        if (room.isPermanent()) {
            editRoomDocument(roomName, XML_SUPER_ADMIN, playerName);
            editRoomDocument(roomName, XML_ADMINS, room.getAdmins().toString());
        }

        player.sendMessage("You made " + playerName + " superadmin for #" + roomName);
    }

    /**
     * Gets a Player object for a player in a certain room.
     * @param room The room to look for the player
     * @param playerName The name of the desired player
     * @return The Player-Object of the desired player
     */
    public Player getPlayerByName(ChatRoom room, String playerName) {
        for (Player p : room.getUsers()) {
            if (p.getName().equals(playerName)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Adds a connection to the list of active connections.
     * @param player The player who has a new connection
     * @param room The room the player gets connected to
     */
    public void addConnection(Player player, ChatRoom room) {
        String playerName = player.getName();

        if (!connections.containsKey(playerName)) {
            connections.put(playerName, new ArrayList<ChatRoom>());
        }

        connections.get(playerName).add(room);
    }

    /**
     * Removes a connection
     * @param player The player who broke a connection
     * @param room The room the connection was lost to
     */
    public void removeConnection(Player player, ChatRoom room) {
        String playerName = player.getName();

        connections.get(playerName).remove(room);

        if (connections.get(playerName).isEmpty()) {
            connections.remove(playerName);
        }
    }

    /**
     * Broadcasts messages to all players connected to a channel
     * @param room The room the message should be broadcast to
     * @param player The player who wants to broadcast the message
     * @param message The message to be broadcasted
     */
    public void broadcast(ChatRoom room, Player player, String message) {
        String output = String.format(format, room.getColor(), room.getName(), player.getDisplayName(), message);
        for (Player p : room.getUsers()) {
            p.sendMessage(output);
        }
    }

    /**
     * Broadcasts system messages to a channel
     * @param room The room the message is broadcasted to
     * @param message The message to be broadcasted
     */
    public void broadcastSysMsg(ChatRoom room, String message) {
        for (Player p : room.getUsers()) {
            p.sendMessage(room.getColor() + "[" + room.getName() + "] /" + message);
        }
    }

    /**
     * Returns a room by its name
     * @param roomName The name of the room you are looking for
     * @return null if the room does not exist or the room you requested.
     */
    public ChatRoom getRoom(String roomName) {
            return rooms.get(roomName);
    }

    /**
     * Returnsa ll active channels
     * @return a hasmap of active channels
     */
    public HashMap<String, ChatRoom> getRooms() {
        return rooms;
    }

    /**
     * Sends a list of active channels to a player
     * @param player The player to get the list
     */
    public void listRooms(Player player) {
        if (rooms.isEmpty()) {
            player.sendMessage("There are no chatrooms");
            return;
        }
        for (ChatRoom r : rooms.values()) {
            String hasPw = "";
            if (!r.getPassword().equals("")) {
                hasPw = "*";
            }
            player.sendMessage(r.getName() + "[" + r.getUsers().size() + "]" + hasPw);
        }
        player.sendMessage("*Password protected");
    }

    /**
     * Outputs the current active connections of the player
     * @param player The player who wants to see his connections
     */
    public void getConnections(Player player) {
        String result = "You are connected to:";
        if (connections.containsKey(player.getName())) {
            for (ChatRoom r : connections.get(player.getName())) {
                result += " " + r.getName();
            }
        }
        player.sendMessage(result);
    }

    /**
     * Checks if a player joined a channel
     * @param player
     * @param roomName
     * @return
     */
    public boolean isConnected(Player player, String roomName) {
        boolean result = false;
        for (ChatRoom r : connections.get(player.getName())) {
            if (roomName.equals(r.getName())) {
                return true;
            }
        }
        return result;
    }

    /**
     * Kills a room, deletes it and returns an arraylist of the players
     * that were connected to that channel.
     * Only serveradmins can kill channels.
     * @param roomName The room to be killed
     * @param player The player who wants to kill the room
     * @return an ArrayList of player objects that were in that channel.
     */
    public ArrayList<Player> killRoom(String roomName, Player player) {
        if (!/*player.isAdmin()*/isServerAdmin(player)) {
            player.sendMessage("Only server admins can remove rooms.");
            return null;
        }

        if (!roomExists(roomName)) {
            player.sendMessage("The room does not exist.");
            return null;
        }

        ChatRoom room = rooms.get(roomName);
        rooms.remove(roomName);
        player.sendMessage("You removed #" + roomName + ".");

        return room.getUsers();
    }

    /**
     * Lists the playrs that are currently connected to that channel
     * @param roomName The name of the channel
     * @param player The player who wants to see the list
     */
    public void listPlayers(String roomName, Player player) {
        if (roomExists(roomName)) {
            String result = "";
            ChatRoom room = rooms.get(roomName);

            for (Player p : room.getUsers()) {
                result += " " + p.getName();
                if (room.getAdmins().contains(p.getName())) {
                    result += "[A]";
                }
            }
            player.sendMessage(result);
        } else {
            player.sendMessage("Room does not exist.");
        }
    }

    /**
     * Saves a room to the ChatRoomList.xml
     * @param room The room to be saved
     */
    public void saveRoom(final ChatRoom room) {
        Runnable runnable = new Runnable() {

            public void run() {
                Document document = readDocument(PATH_TO_XML);

                Element root = null;

                if (!document.hasChildNodes()) {
                    root = document.createElement(XML_ROOT);
                    document.appendChild(root);
                } else {
                    root = (Element) document.getDocumentElement();
                }

                Element newRoom = document.createElement(XML_ROOM);
                root.appendChild(newRoom);

                Element roomNameElement = document.createElement(XML_ROOM_NAME);
                roomNameElement.setTextContent(room.getName());
                newRoom.appendChild(roomNameElement);

                Element passwordElement = document.createElement(XML_PASSWORD);
                passwordElement.setTextContent(room.getPassword());
                newRoom.appendChild(passwordElement);

                Element motdElement = document.createElement(XML_MOTD);
                motdElement.setTextContent(room.getMotd());
                newRoom.appendChild(motdElement);

                Element colorElement = document.createElement(XML_COLOR);
                colorElement.setTextContent(room.getColor());
                newRoom.appendChild(colorElement);

                Element adminsElement = document.createElement(XML_ADMINS);
                adminsElement.setTextContent(room.getAdmins().toString());
                newRoom.appendChild(adminsElement);

                Element bansElement = document.createElement(XML_BANS);
                bansElement.setTextContent(room.getBans().toString());
                newRoom.appendChild(bansElement);

                Element superAdminElement = document.createElement(XML_SUPER_ADMIN);
                superAdminElement.setTextContent(room.getSuperAdmin());
                newRoom.appendChild(superAdminElement);


                writeDocument(PATH_TO_XML, document);
            }
        };
        worker.execute(runnable);
    }

    /**
     * Deletes a room from the xml file
     * @param roomName Name of the room to be deleted
     */
    public void deleteRoomFromFile(final String roomName) {
        Runnable runnable = new Runnable() {

            public void run() {
                Document document = readDocument(PATH_TO_XML);

                Element root = (Element) document.getDocumentElement();

                NodeList roomList = document.getElementsByTagName(XML_ROOM);

                for (int s = 0; s < roomList.getLength(); s++) {

                    Node roomNode = roomList.item(s);

                    if (roomNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element roomElement = (Element) roomNode;

                        NodeList roomNameNodeList = roomElement.getElementsByTagName(XML_ROOM_NAME);
                        String currentRoomName = roomNameNodeList.item(0).getTextContent();

                        if (currentRoomName.equals(roomName)) {
                            root.removeChild(roomNode);
                            break;
                        }
                    }
                }

                writeDocument(PATH_TO_XML, document);
            }
        };
        worker.execute(runnable);
    }

    /**
     * Read the xml file of rooms and adds them to the HashMap of active rooms
     */
    public void readRooms() {
        Runnable runnable = new Runnable() {

            public void run() {
                Document document = readDocument(PATH_TO_XML);

                //If the file is empty, return.
                if (!document.hasChildNodes()) {
                    return;
                }

                NodeList roomList = document.getElementsByTagName(XML_ROOM);

                for (int s = 0; s < roomList.getLength(); s++) {

                    Node roomNode = roomList.item(s);

                    if (roomNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element roomElement = (Element) roomNode;

                        NodeList roomNameNode = roomElement.getElementsByTagName(XML_ROOM_NAME);
                        String roomName = roomNameNode.item(0).getTextContent();

                        NodeList roomPasswdNode = roomElement.getElementsByTagName(XML_PASSWORD);
                        String roomPasswd = roomPasswdNode.item(0).getTextContent();

                        NodeList roomSuNode = roomElement.getElementsByTagName(XML_SUPER_ADMIN);
                        String roomSu = roomSuNode.item(0).getTextContent();

                        NodeList roomAdminsNode = roomElement.getElementsByTagName(XML_ADMINS);
                        String roomAdmins = roomAdminsNode.item(0).getTextContent();

                        NodeList roomBansNode = roomElement.getElementsByTagName(XML_BANS);
                        String roomBans = roomBansNode.item(0).getTextContent();

                        NodeList roomMotdNode = roomElement.getElementsByTagName(XML_MOTD);
                        String roomMotd = roomMotdNode.item(0).getTextContent();

                        NodeList roomColorNode = roomElement.getElementsByTagName(XML_COLOR);
                        String roomColor = roomColorNode.item(0).getTextContent();

                        ArrayList<String> admins = new ArrayList<String>();

                        roomAdmins = roomAdmins.substring(1, roomAdmins.length() - 1);
                        String[] tempAdmins = roomAdmins.split(", ");
                        for (String a : tempAdmins) {
                            admins.add(a);
                        }

                        ArrayList<String> bans = new ArrayList<String>();

                        roomBans = roomBans.substring(1, roomBans.length() - 1);
                        String[] tempBans = roomBans.split(", ");
                        for (String b : tempBans) {
                            bans.add(b);
                        }

                        ChatRoom room = new ChatRoom(roomName, roomPasswd, roomMotd,
                                roomColor, admins, bans, roomSu);

                        rooms.put(roomName, room);

                    }
                }
            }
        };
        worker.execute(runnable);
    }

    /**
     * Reads a xml file and puts it into a Document object
     * @param path The path to the xml file
     * @return The xml file as Document
     */
    public Document readDocument(String path) {
        Document document = null;
        try {
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            DocumentBuilder loader = factory.newDocumentBuilder();


            File file = new File(path);
            if (file.exists()) {
                document = loader.parse(file);
            } else {
                document = loader.newDocument();
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
        return document;
    }

    /**
     * Writes a document to a xml file defined by the path
     * @param path the relative or absolute path to the xml file
     * @param document The document to be serialized
     */
    public void writeDocument(String path, Document document) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            DOMSource source = new DOMSource(document);

            File file = new File(path);
            StreamResult result = new StreamResult(file);
            transformer.transform(source, result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Edits a certain room property in the xml file
     * @param roomName The name of the room
     * @param property The kind of property to be changed
     * @param newValue The new value for the property
     */
    public void editRoomDocument(final String roomName, final String property, final String newValue) {
        Runnable runnable = new Runnable() {

            public void run() {
                Document document = readDocument(PATH_TO_XML);

                NodeList roomList = document.getElementsByTagName(XML_ROOM);
                for (int s = 0; s < roomList.getLength(); s++) {
                    Node roomNode = roomList.item(s);

                    if (roomNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element roomElement = (Element) roomNode;

                        NodeList roomNameNodeList = roomElement.getElementsByTagName(XML_ROOM_NAME);
                        String currentRoomName = roomNameNodeList.item(0).getTextContent();

                        if (currentRoomName.equals(roomName)) {
                            NodeList nodeList = roomElement.getElementsByTagName(property);
                            nodeList.item(0).setTextContent(newValue);
                            break;
                        }
                    }
                }

                writeDocument(PATH_TO_XML, document);
            }
        };
        worker.execute(runnable);
    }
}
