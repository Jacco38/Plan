package main.java.com.djrapitops.plan.command.commands.manage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import main.java.com.djrapitops.plan.Phrase;
import main.java.com.djrapitops.plan.Plan;
import main.java.com.djrapitops.plan.command.CommandType;
import main.java.com.djrapitops.plan.command.SubCommand;
import main.java.com.djrapitops.plan.data.UserData;
import main.java.com.djrapitops.plan.data.cache.DBCallableProcessor;
import main.java.com.djrapitops.plan.database.Database;
import main.java.com.djrapitops.plan.utilities.DataCombineUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Rsl1122
 */
public class ManageCombineCommand extends SubCommand {

    private Plan plugin;

    /**
     * Class Constructor.
     *
     * @param plugin Current instance of Plan
     */
    public ManageCombineCommand(Plan plugin) {
        super("combine", "plan.manage", Phrase.CMD_USG_MANAGE_COMBINE + "", CommandType.CONSOLE_WITH_ARGUMENTS, Phrase.ARG_MOVE + "");
        this.plugin = plugin;
    }

    /**
     * Subcommand inspect.
     *
     * Adds player's data from DataCache/DB to the InspectCache for amount of
     * time specified in the config, and clears the data from Cache with a timer
     * task.
     *
     * @param sender
     * @param cmd
     * @param commandLabel
     * @param args Player's name or nothing - if empty sender's name is used.
     * @return true in all cases.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Phrase.COMMAND_REQUIRES_ARGUMENTS.parse(Phrase.USE_COMBINE + ""));
            return true;
        }
        String fromDB = args[0].toLowerCase();
        String toDB = args[1].toLowerCase();
        if (!fromDB.equals("mysql") && !fromDB.equals("sqlite")) {
            sender.sendMessage(Phrase.MANAGE_ERROR_INCORRECT_DB + fromDB);
            return true;
        }
        if (!toDB.equals("mysql") && !toDB.equals("sqlite")) {
            sender.sendMessage(Phrase.MANAGE_ERROR_INCORRECT_DB + toDB);
            return true;
        }
        if (fromDB.equals(toDB)) {
            sender.sendMessage(Phrase.MANAGE_ERROR_SAME_DB + "");
            return true;
        }
        if (!Arrays.asList(args).contains("-a")) {
            sender.sendMessage(Phrase.COMMAND_ADD_CONFIRMATION_ARGUMENT.parse(Phrase.WARN_REWRITE.parse(args[1])));
            return true;
        }

        Database fromDatabase = null;
        Database toDatabase = null;
        for (Database database : plugin.getDatabases()) {
            if (fromDB.equalsIgnoreCase(database.getConfigName())) {
                fromDatabase = database;
                fromDatabase.init();
            }
            if (toDB.equalsIgnoreCase(database.getConfigName())) {
                toDatabase = database;
                toDatabase.init();
            }
        }
        if (fromDatabase == null) {
            sender.sendMessage(Phrase.MANAGE_DATABASE_FAILURE + "");
            plugin.logError(fromDB + " was null!");
            return true;
        }
        if (toDatabase == null) {
            sender.sendMessage(Phrase.MANAGE_DATABASE_FAILURE + "");
            plugin.logError(toDB + " was null!");
            return true;
        }

        final Set<UUID> fromUUIDS = fromDatabase.getSavedUUIDs();
        final Set<UUID> toUUIDS = toDatabase.getSavedUUIDs();
        try {
            if (fromUUIDS.isEmpty() && toUUIDS.isEmpty()) {
                sender.sendMessage(Phrase.MANAGE_ERROR_NO_PLAYERS + " (" + fromDB + ")");
                return true;
            }

            final Database moveFromDB = fromDatabase;
            final Database moveToDB = toDatabase;
            (new BukkitRunnable() {
                @Override
                public void run() {
                    sender.sendMessage(Phrase.MANAGE_PROCESS_START.parse());
                    HashMap<UUID, UserData> allFromUserData = new HashMap<>();
                    HashMap<UUID, UserData> allToUserData = new HashMap<>();
                    for (UUID uuid : fromUUIDS) {
                        moveFromDB.giveUserDataToProcessors(uuid, new DBCallableProcessor() {
                            @Override
                            public void process(UserData data) {
                                allFromUserData.put(uuid, data);
                            }
                        });
                    }
                    for (UUID uuid : toUUIDS) {
                        moveToDB.giveUserDataToProcessors(uuid, new DBCallableProcessor() {
                            @Override
                            public void process(UserData data) {
                                allToUserData.put(uuid, data);
                            }
                        });
                    }
                    while (fromUUIDS.size() > allFromUserData.size() || toUUIDS.size() > allToUserData.size()) {
                        
                    }
                    Set<UUID> uuids = new HashSet<>();
                    uuids.addAll(toUUIDS);
                    uuids.addAll(fromUUIDS);

                    List<UserData> combinedUserData = DataCombineUtils.combineUserDatas(allFromUserData, allToUserData, uuids);

                    HashMap<String, Integer> commandUse = DataCombineUtils.combineCommandUses(moveFromDB.getCommandUse(), moveToDB.getCommandUse());

                    moveToDB.removeAllData();

                    moveToDB.saveMultipleUserData(combinedUserData);
                    moveToDB.saveCommandUse(commandUse);

                    sender.sendMessage(Phrase.MANAGE_MOVE_SUCCESS + "");
                    if (!toDB.equals(plugin.getDB().getConfigName())) {
                        sender.sendMessage(Phrase.MANAGE_DB_CONFIG_REMINDER + "");
                    }
                    this.cancel();
                }

            }).runTaskAsynchronously(plugin);

        } catch (NullPointerException e) {
            sender.sendMessage(Phrase.MANAGE_DATABASE_FAILURE + "");
        }
        return true;
    }

}
