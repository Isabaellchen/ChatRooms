package com.bukkit.isabaellchen.chatrooms;


import java.util.ArrayList;
import org.bukkit.entity.Player;

/**
 *
 * @author Isa
 */
public class ChatRoom {

    private String name, password, motd, color;
    private ArrayList<Player> users;
    private ArrayList<String> admins, bans;
    private Boolean isPermanent;
    private String superAdmin;

    public ChatRoom(String name, Player admin) {
        this.name = name;
        motd = "Welcome to #"+this.name;
        users = new ArrayList<Player>();
        admins = new ArrayList<String>();
        superAdmin = admin.getName();
        bans = new ArrayList<String>();
        isPermanent = false;
        //color = Colors.LightBlue;
        password = "";

        users.add(admin);
        admins.add(admin.getName());
    }

    public ChatRoom(String name, String password, Player admin) {
        this.name = name;
        this.password = password;
        motd = "Welcome to #"+this.name;
        users = new ArrayList<Player>();
        admins = new ArrayList<String>();
        superAdmin = admin.getName();
        bans = new ArrayList<String>();
        isPermanent = false;
        color = "\u00a7b";

        users.add(admin);
        admins.add(admin.getName());
    }

    public ChatRoom(String name, String password, String motd, String color,
            ArrayList<String> admins, ArrayList<String> bans, String superAdmin) {
        this.name = name;
        this.password = password;
        this.motd = motd;
        this.color = color;
        this.admins = admins;
        this.bans = bans;
        this.superAdmin = superAdmin;

        this.isPermanent = true;
        this.users = new ArrayList<Player>();
    }

    public String getSuperAdmin() {
        return superAdmin;
    }

    public void setSuperAdmin(String playerName) {
        this.superAdmin = playerName;
    }

    public ArrayList<String> getAdmins() {
        return admins;
    }

    public boolean addAdmin(String admin) {
        if (!admins.contains(admin)) {
            admins.add(admin);
            return true;
        } else {
            return false;
        }
    }

    public boolean removeAdmin(String admin) {
        if (admins.contains(admin)) {
            admins.remove(admin);
            return true;
        } else {
            return false;
        }
    }

    public ArrayList<String> getBans() {
        return bans;
    }

    public boolean addBan(String player) {
        if (!bans.contains(player)) {
            bans.add(player);
            return true;
        } else {
            return false;
        }
    }

    public boolean removeBan(String player) {
        if (bans.contains(player)) {
            bans.remove(player);
            return true;
        } else {
            return false;
        }
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Boolean isPermanent() {
        return isPermanent;
    }

    public void setPermanent(Boolean isPermanent) {
        this.isPermanent = isPermanent;
    }

    public String getMotd() {
        return motd;
    }

    public void setMotd(String motd) {
        this.motd = motd;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public ArrayList<Player> getUsers() {
        return users;
    }

    public boolean addUser(Player player) {
        if (!users.contains(player)) {
            users.add(player);
            return true;
        } else {
            return false;
        }
    }

    public boolean removeUser(Player player) {
        if (users.contains(player)) {
            users.remove(player);
            return true;
        } else {
            return false;
        }
    }

}
