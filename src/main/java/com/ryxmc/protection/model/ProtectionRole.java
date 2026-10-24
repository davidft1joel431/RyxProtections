package com.ryxmc.protection.model;

public enum ProtectionRole {
    MEMBER  (0,"Miembro",    "§7","§8[§7ᴍɪᴇᴍʙʀᴏ§8]"),
    COLEADER(1,"CóLíder",   "§a","§8[§aᴄᴏʟɪᴅᴇʀ§8]"),
    LEADER  (2,"Líder",     "§b","§8[§bʟɪᴅᴇʀ§8]"),
    COOWNER (3,"Co-Dueño",  "§6","§8[§6ᴄᴏᴅᴜᴇɴ̃ᴏ§8]"),
    OWNER   (4,"Propietario","§e","§8[§e§lᴅᴜᴇɴ̃ᴏ§8]");

    private final int level; private final String displayName, color, tag;
    ProtectionRole(int l, String d, String c, String t){ level=l;displayName=d;color=c;tag=t; }

    public int    level()  { return level; }
    public String display(){ return displayName; }
    public String color()  { return color; }
    public String tag()    { return tag; }

    public boolean hasAtLeast(ProtectionRole r) { return level >= r.level; }
    public boolean canManage(ProtectionRole r)  { return level > r.level; }

    public boolean canAddMember()    { return level >= COLEADER.level; }
    public boolean canRemoveMember() { return level >= COLEADER.level; }
    public boolean canTogglePvP()    { return level >= COLEADER.level; }
    public boolean canSetRoles()     { return level >= LEADER.level; }
    public boolean canRename()       { return level >= COOWNER.level; }
    public boolean canBlacklist()    { return level >= COOWNER.level; }
    public boolean canDelete()       { return level >= OWNER.level; }

    public static ProtectionRole fromString(String s){
        if(s==null) return MEMBER;
        try{ return valueOf(s.toUpperCase()); } catch(Exception e){ return MEMBER; }
    }
}
