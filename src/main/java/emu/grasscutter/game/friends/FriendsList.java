package emu.grasscutter.game.friends;

import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.*;
import emu.grasscutter.net.proto.DealAddFriendResultTypeOuterClass.DealAddFriendResultType;
import emu.grasscutter.server.packet.send.*;
import it.unimi.dsi.fastutil.ints.*;
import java.util.List;

public class FriendsList extends BasePlayerManager {
    private final Int2ObjectMap<Friendship> friends;
    private final Int2ObjectMap<Friendship> pendingFriends;

    private boolean loaded = false;

    public FriendsList(Player player) {
        super(player);
        this.friends = new Int2ObjectOpenHashMap<Friendship>();
        this.pendingFriends = new Int2ObjectOpenHashMap<Friendship>();
    }

    public boolean hasLoaded() {
        return loaded;
    }

    public synchronized Int2ObjectMap<Friendship> getFriends() {
        return friends;
    }

    public synchronized Int2ObjectMap<Friendship> getPendingFriends() {
        return this.pendingFriends;
    }

    public synchronized boolean isFriendsWith(int uid) {
        return this.getFriends().containsKey(uid);
    }

    private synchronized Friendship getFriendshipById(int id) {
        Friendship friendship = this.getFriends().get(id);
        if (friendship == null) {
            friendship = this.getPendingFriendById(id);
        }
        return friendship;
    }

    private synchronized Friendship getFriendById(int id) {
        return this.getFriends().get(id);
    }

    private synchronized Friendship getPendingFriendById(int id) {
        return this.getPendingFriends().get(id);
    }

    public void addFriend(Friendship friendship) {
        getFriends().put(friendship.getFriendId(), friendship);
    }

    public void addPendingFriend(Friendship friendship) {
        getPendingFriends().put(friendship.getFriendId(), friendship);
    }

    public synchronized void handleFriendRequest(int targetUid, DealAddFriendResultType result) {
        Friendship myFriendship = this.getPendingFriendById(targetUid);
        if (myFriendship == null) {
            return;
        }

        if (myFriendship.getAskerId() == this.getPlayer().getUid()) {
            return;
        }

        Player target = getPlayer().getSession().getServer().getPlayerByUid(targetUid, true);
        if (target == null) {
            return;
        }

        Friendship theirFriendship = null;
        if (target.isOnline()) {
            theirFriendship = target.getFriendsList().getPendingFriendById(this.getPlayer().getUid());
        } else {
            theirFriendship = DatabaseHelper.getReverseFriendship(myFriendship);
        }

        if (theirFriendship == null) {
            this.getPendingFriends().remove(myFriendship.getOwnerId());
            myFriendship.delete();
            return;
        }

        if (result == DealAddFriendResultType.DealAddFriendResultType_DEAL_ADD_FRIEND_ACCEPT) {
            myFriendship.setIsFriend(true);
            theirFriendship.setIsFriend(true);

            this.getPendingFriends().remove(myFriendship.getOwnerId());
            this.addFriend(myFriendship);

            if (target.isOnline()) {
                target.getFriendsList().getPendingFriends().remove(this.getPlayer().getUid());
                target.getFriendsList().addFriend(theirFriendship);
            }

            myFriendship.save();
            theirFriendship.save();
        } else {
            this.getPendingFriends().remove(myFriendship.getOwnerId());
            myFriendship.delete();
            if (target.isOnline()) {
                theirFriendship = target.getFriendsList().getPendingFriendById(this.getPlayer().getUid());
            }
            theirFriendship.delete();
        }

        this.getPlayer().sendPacket(new PacketDealAddFriendRsp(targetUid, result));
    }

    public synchronized void deleteFriend(int targetUid) {
        Friendship myFriendship = this.getFriendById(targetUid);
        if (myFriendship == null) {
            return;
        }

        this.getFriends().remove(targetUid);
        myFriendship.delete();

        Friendship theirFriendship = null;
        Player friend = myFriendship.getFriendProfile().getPlayer();
        if (friend != null) {
            theirFriendship = friend.getFriendsList().getFriendById(this.getPlayer().getUid());
            if (theirFriendship != null) {
                friend.getFriendsList().getFriends().remove(theirFriendship.getFriendId());
                theirFriendship.delete();
                friend.sendPacket(new PacketDeleteFriendNotify(theirFriendship.getFriendId()));
            }
        } else {
            theirFriendship = DatabaseHelper.getReverseFriendship(myFriendship);
            if (theirFriendship != null) {
                theirFriendship.delete();
            }
        }

        this.getPlayer().sendPacket(new PacketDeleteFriendRsp(targetUid));
    }

    public synchronized void sendFriendRequest(int targetUid) {
        Player target = getPlayer().getSession().getServer().getPlayerByUid(targetUid, true);

        if (target == null || target == this.getPlayer()) {
            return;
        }

        if (this.getPendingFriends().containsKey(targetUid)
                || this.getFriends().containsKey(targetUid)) {
            return;
        }

        Friendship myFriendship = new Friendship(getPlayer(), target, getPlayer());
        Friendship theirFriendship = new Friendship(target, getPlayer(), getPlayer());

        this.addPendingFriend(myFriendship);

        if (target.isOnline() && target.getFriendsList().hasLoaded()) {
            target.getFriendsList().addPendingFriend(theirFriendship);
            target.sendPacket(new PacketAskAddFriendNotify(theirFriendship));
        }

        myFriendship.save();
        theirFriendship.save();

        this.getPlayer().sendPacket(new PacketAskAddFriendRsp(targetUid));
    }

    public int getFullFriendCount() {
        return this.getPendingFriends().size() + this.getFriends().size();
    }

    public synchronized void loadFromDatabase() {
        if (this.hasLoaded()) {
            return;
        }

        List<Friendship> friendships = DatabaseHelper.getFriends(player);
        friendships.forEach(this::loadFriendFromDatabase);

        this.loaded = true;
    }

    private void loadFriendFromDatabase(Friendship friendship) {
        friendship.setOwner(getPlayer());

        Player friend =
                getPlayer().getSession().getServer().getPlayerByUid(friendship.getFriendProfile().getUid());
        if (friend != null) {
            friendship.setFriendProfile(friend);

            if (friend.getFriendsList().hasLoaded()) {
                Friendship theirFriendship =
                        friend.getFriendsList().getFriendshipById(getPlayer().getUid());
                if (theirFriendship != null) {
                    theirFriendship.setFriendProfile(getPlayer());
                } else {
                    friendship.delete();
                    return;
                }
            }
        }

        if (friendship.isFriend()) {
            getFriends().put(friendship.getFriendId(), friendship);
        } else {
            getPendingFriends().put(friendship.getFriendId(), friendship);
            if (getPendingFriends().size() == 1) {
                getPlayer().getSession().send(new PacketAskAddFriendNotify(friendship));
            }
        }
    }

    public void save() {
        List<Friendship> friendships = DatabaseHelper.getReverseFriends(getPlayer());
        for (Friendship friend : friendships) {
            friend.setFriendProfile(this.getPlayer());
            friend.save();
        }
    }
}
