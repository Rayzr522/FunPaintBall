
package com.rayzr522.funpaintball.minigame;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitRunnable;

import com.rayzr522.funpaintball.Config;
import com.rayzr522.funpaintball.FunPaintBall;
import com.rayzr522.funpaintball.config.ISerializable;
import com.rayzr522.funpaintball.config.Serialized;

public class Arena implements ISerializable {

    /**
     * Indicates that the game is waiting for enough players to join before
     * starting
     */
    public static final int      WAITING     = 0;
    /**
     * Indicates that the game is about to start (countdown)
     */
    public static final int      STARTING    = 1;
    /**
     * Indicates that the game is running
     */
    public static final int      RUNNING     = 2;

    /**
     * The ID of the blue team
     */
    public static final int      BLUE_TEAM   = 0;
    /**
     * The ID of the red team
     */
    public static final int      RED_TEAM    = 1;

    @Serialized
    protected String             name;
    @Serialized
    protected int                minPlayers  = 2;
    @Serialized
    protected int                maxPlayers  = 20;

    @Serialized
    protected Region             arenaRegion = new Region();
    @Serialized
    protected Region             lobbyRegion = new Region();
    @Serialized
    protected Region             deathBox    = new Region();

    @Serialized
    protected Location           arenaBlueSpawn;
    @Serialized
    protected Location           arenaRedSpawn;
    @Serialized
    protected Location           lobbySpawn;
    @Serialized
    protected Location           deathBoxSpawn;
    @Serialized
    protected Location           exit;

    protected List<User>         users       = new ArrayList<>();

    protected boolean            valid;

    /**
     * The current game state
     * 
     * @see Arena#WAITING
     * @see Arena#STARTING
     * @see Arena#RUNNING
     */
    private int                  state       = 0;

    /**
     * All runnables that have been started with
     * {@link Arena#later(BukkitRunnable, double)}. Used for stopping all active
     * runnables when a match stops.
     */
    private List<BukkitRunnable> runnables   = new ArrayList<>();

    /**
     * Used for choosing which team a person is on when the game starts.
     * Alternates each time someone joins.
     */
    private boolean              blueTeam    = false;

    /**
     * The score of the red team
     */
    private int                  scoreRed    = 0;
    /**
     * The score of the blue team
     */
    private int                  scoreBlue   = 0;

    public Arena() {
        this("default");
    }

    /**
     * Initializes an Arena instance
     * 
     * @param name
     *            the name of the arena
     * @param minPlayers
     *            the minimum number of players for the arena
     * @param maxPlayers
     *            the maximum number of players for the arena
     */
    public Arena(String name, int minPlayers, int maxPlayers) {

        this.name = name;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;

    }

    /**
     * Uses the default number of minimum players and maximum players defined in
     * {@link Config}
     * 
     * @param name
     *            the name of the arena
     */
    public Arena(String name) {
        this(name, Config.DEFAULT_MINPLAYERS, Config.DEFAULT_MAXPLAYERS);
    }

    @Override
    public void onDeserialize() {
    }

    @Override
    public void onPreSerialize() {
    }

    /**
     * Adds a user to the arena
     * 
     * @param user
     *            the player
     * @return Whether or not they were allowed to join
     */
    public final boolean join(User user) {

        if (!isValid()) {
            return false;
        }
        if (users.size() >= maxPlayers) {
            return false;
        }
        if (users.contains(user)) {
            return false;
        }

        users.add(user);
        onJoin(user);
        return true;

    }

    /**
     * Removes a user from the arena
     * 
     * @param user
     *            the player
     */
    public void leave(User user) {

        if (users.remove(user)) {
            user.teleport(exit);
            user.setTeam(-1);
            user.restoreData();
        }

    }

    public void switchState(int state) {
        // int oldState = this.state;
        this.state = state;

        for (BukkitRunnable runnable : runnables) {
            try {
                runnable.cancel();
            } catch (IllegalStateException e) {

            } catch (Exception e) {
                System.err.println("Failed to stop runnable!");
                e.printStackTrace();
            }
        }

        if (state == WAITING) {
            stop();
        } else if (state == STARTING) {

            for (int i = 1; i <= 5; i++) {

                final int time = i;

                later(new BukkitRunnable() {

                    public void run() {
                        broadcast("starting-in", time);
                    };
                }, Config.WAIT_START - time);

            }

            later(new BukkitRunnable() {

                @Override
                public void run() {
                    switchState(RUNNING);
                }

            }, Config.WAIT_START);

        } else if (state == RUNNING) {

            start();

        }
    }

    /**
     * Starts the match
     */
    public void start() {

        onStart();

    }

    /**
     * Stops the match
     */
    public void stop() {

        onStop();

    }

    public void forceStop() {

        for (User u : users) {

            u.teleport(exit);
            u.restoreData();

        }

    }

    protected void onStart() {

        for (User u : users) {

            u.teleport(getSpawn(u.getTeam()));

        }

        broadcast("match-started");

    }

    protected void onStop() {

        int winningTeam = scoreBlue == scoreRed ? -1 : (scoreBlue > scoreRed ? BLUE_TEAM : RED_TEAM);

        switch (winningTeam) {
            case BLUE_TEAM:
                broadcast("team-won", Config.TEAM_BLUE, scoreBlue, scoreRed);
                break;
            case RED_TEAM:
                broadcast("team-won", Config.TEAM_RED, scoreRed, scoreBlue);
                break;
            case -1:
                broadcast("tie", scoreBlue, scoreRed);
                break;
        }

        for (User u : users) {

            u.teleport(exit);
            u.getPlayer().playSound(u.getLocation(), Sound.ENTITY_FIREWORK_BLAST, 1.0f, 1.0f);
            u.getPlayer().playSound(u.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);

        }

    }

    /**
     * Called when a user joins the match
     * 
     * @param user
     *            the user
     */
    protected void onJoin(User user) {

        broadcast("player-joined", user.getName(), users.size(), maxPlayers);

        blueTeam = !blueTeam;
        user.setTeam(blueTeam ? BLUE_TEAM : RED_TEAM);

        user.storeData();
        user.getPlayer().getInventory().clear();
        user.teleport(lobbySpawn);
        user.getPlayer().setGameMode(GameMode.SURVIVAL);

        if (state == WAITING && users.size() >= minPlayers) {
            switchState(STARTING);
        }

    }

    /**
     * Called when a user dies
     * 
     * @param user
     *            the user
     */
    public void onDeath(User user) {
        sendToDeathBox(user);
        switch (user.getTeam()) {
            case BLUE_TEAM:
                scoreRed++;
                anouncePoint(RED_TEAM);
                break;
            case RED_TEAM:
                scoreBlue++;
                anouncePoint(BLUE_TEAM);
                break;
        }
    }

    /**
     * Announces to all players in the match that a point has been scored
     * 
     * @param team
     *            which team scored a point
     */
    public void anouncePoint(int team) {
        switch (team) {
            case BLUE_TEAM:
                broadcast("point-scored", Config.TEAM_BLUE, scoreBlue, scoreRed);
                break;
            case RED_TEAM:
                broadcast("point-scored", Config.TEAM_RED, scoreBlue, scoreRed);
                break;
        }
    }

    /**
     * Broadcast a message to all players in this arena
     * 
     * @param msg
     *            the key of the message to send
     * @param objects
     *            the values to insert into the message
     */
    public void broadcast(String msg, Object... objects) {
        for (User u : users) {
            u.send(msg, objects);
        }
    }

    /**
     * Sends a {@link User} to the death box for {@link Config#WAIT_DEATH}
     * seconds
     * 
     * @param user
     *            the player
     */
    public void sendToDeathBox(User user) {
        user.teleport(deathBoxSpawn);
        later(new BukkitRunnable() {

            @Override
            public void run() {
                user.teleport(getSpawn(user.getTeam()));
            }

        }, Config.WAIT_DEATH);
    }

    /**
     * Get the correct spawn for the given team
     * 
     * @param team
     *            a team number
     * @return The spawn point for that team
     */
    public Location getSpawn(int team) {
        return team == BLUE_TEAM ? arenaBlueSpawn : arenaRedSpawn;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name
     *            the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the minPlayers
     */
    public int getMinPlayers() {
        return minPlayers;
    }

    /**
     * @param minPlayers
     *            the minPlayers to set
     */
    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    /**
     * @return the maxPlayers
     */
    public int getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * @param maxPlayers
     *            the maxPlayers to set
     */
    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    /**
     * @return the arenaRegion
     */
    public Region getArenaRegion() {
        return arenaRegion;
    }

    /**
     * @param arenaRegion
     *            the arenaRegion to set
     */
    public void setArenaRegion(Region arenaRegion) {
        this.arenaRegion = arenaRegion;
    }

    /**
     * @return the lobbyRegion
     */
    public Region getLobbyRegion() {
        return lobbyRegion;
    }

    /**
     * @param lobbyRegion
     *            the lobbyRegion to set
     */
    public void setLobbyRegion(Region lobbyRegion) {
        this.lobbyRegion = lobbyRegion;
    }

    /**
     * Check whether this Arena is currently valid (has all required data like
     * regions, spawns, etc.)
     * 
     * @return
     */
    public boolean isValid() {
        return valid = !(arenaRegion == null || lobbyRegion == null || deathBox == null || arenaBlueSpawn == null || arenaRedSpawn == null || lobbySpawn == null || deathBoxSpawn == null
                || exit == null) && arenaRegion.isValid()
                && lobbyRegion.isValid() && deathBox.isValid();
    }

    /**
     * Get the current state of the arena
     * 
     * @return
     */
    public int currentState() {
        return state;
    }

    /**
     * @return the deathBox
     */
    public Region getDeathBox() {
        return deathBox;
    }

    /**
     * @param deathBox
     *            the deathBox to set
     */
    public void setDeathBox(Region deathBox) {
        this.deathBox = deathBox;
    }

    /**
     * @return the arenaBlueSpawn
     */
    public Location getArenaBlueSpawn() {
        return arenaBlueSpawn;
    }

    /**
     * @param arenaBlueSpawn
     *            the arenaBlueSpawn to set
     */
    public void setArenaBlueSpawn(Location arenaBlueSpawn) {
        this.arenaBlueSpawn = arenaBlueSpawn;
    }

    /**
     * @return the arenaRedSpawn
     */
    public Location getArenaRedSpawn() {
        return arenaRedSpawn;
    }

    /**
     * @param arenaRedSpawn
     *            the arenaRedSpawn to set
     */
    public void setArenaRedSpawn(Location arenaRedSpawn) {
        this.arenaRedSpawn = arenaRedSpawn;
    }

    /**
     * @return the lobbySpawn
     */
    public Location getLobbySpawn() {
        return lobbySpawn;
    }

    /**
     * @param lobbySpawn
     *            the lobbySpawn to set
     */
    public void setLobbySpawn(Location lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
    }

    /**
     * @return the deathBoxSpawn
     */
    public Location getDeathBoxSpawn() {
        return deathBoxSpawn;
    }

    /**
     * @param deathBoxSpawn
     *            the deathBoxSpawn to set
     */
    public void setDeathBoxSpawn(Location deathBoxSpawn) {
        this.deathBoxSpawn = deathBoxSpawn;
    }

    /**
     * @return the exit
     */
    public Location getExit() {
        return exit;
    }

    /**
     * @param exit
     *            the exit to set
     */
    public void setExit(Location exit) {
        this.exit = exit;
    }

    /**
     * @return the users
     */
    public List<User> getUsers() {
        return users;
    }

    /**
     * @param users
     *            the users to set
     */
    public void setUsers(List<User> users) {
        this.users = users;
    }

    /**
     * @return the state
     */
    public int getState() {
        return state;
    }

    /**
     * @param state
     *            the state to set
     */
    public void setState(int state) {
        this.state = state;
    }

    /**
     * 
     * @param runnable
     *            the {@link BukkitRunnable} to run
     * @param seconds
     *            how many seconds until this runs
     */
    public void later(BukkitRunnable runnable, double seconds) {
        runnable.runTaskLater(FunPaintBall.INSTANCE, (long) (seconds * 20));
        runnables.add(runnable);
    }

    /**
     * Checks whether a location is within any of the regions of this arena
     * 
     * @param location
     *            the location to check
     * @return Whether or not the location is within any of the regions
     */
    public boolean isInArena(Location location) {
        return arenaRegion.inRegion(location) || lobbyRegion.inRegion(location) || deathBox.inRegion(location);
    }

}
