package core.gameMachine;

import core.competition.CompetitionParameters;
import core.game.*;
import core.player.*;
import core.vgdl.*;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.StatSummary;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Random;

/**
 * Created by Daniel on 07.03.2017.
 */
public class LearningMachine {
    public static final boolean VERBOSE = false;


    /**
     * Reads and launches a game for a bot to play. Graphics always on.
     *
     * @param game_file  game description file.
     * @param level_file file with the level to be played.
     */
    public static double[] playOneGame(String game_file, String level_file, String actionFile, int randomSeed, boolean isTraining) throws IOException {
        String agentName = "controllers.human.Agent";
        boolean visuals = true;
        return runOneGame(game_file, level_file, visuals, agentName, actionFile, randomSeed, isTraining);
    }

    /**
     * Reads and launches a game for a bot to be played. Graphics can be on or off.
     *
     * @param game_file  game description file.
     * @param level_file file with the level to be played.
     * @param visuals    true to show the graphics, false otherwise. Training games have never graphics set to ON.
     * @param agentName  name (inc. package) where the controller is otherwise.
     * @param actionFile filename of the file where the actions of this player, for this game, should be recorded.
     * @param randomSeed sampleRandom seed for the sampleRandom generator.
     */
    public static double[] runOneGame(String game_file, String level_file, boolean visuals,
                                    String agentName, String actionFile, int randomSeed, boolean isTraining) throws IOException {
        int trainingPlays = 0;

        VGDLFactory.GetInstance().init(); //This always first thing to do.
        VGDLRegistry.GetInstance().init();

        System.out.println(" ** Playing game " + game_file + ", level " + level_file + " **");

        //Create the player.
        LearningPlayer player = LearningMachine.createPlayer(agentName);

//
        //2. Play the training games.
        System.out.print(trainingPlays + " ");
        double[] finalScore = playOnce(player, actionFile, game_file, level_file, visuals, randomSeed, isTraining);

        return finalScore;
    }

    /**
     * Reads and launches a game to be played on a series of both pre-determined and non
     * pre-determined levels.
     *
     * @param game_file  game description file.
     * @param level_files file with the level to be played.
     * @param agentName  name (inc. package) where the controller is otherwise.
     * @param actionFiles filename of the file where the actions of this player, for this game, should be recorded.
     */
    public static void runMultipleGames(String game_file, String[] level_files,
                                            String agentName, String[] actionFiles) throws IOException {
        int trainingPlays = 0;

        VGDLFactory.GetInstance().init(); //This always first thing to do.
        VGDLRegistry.GetInstance().init();

        //Create the player.
        LearningPlayer player = LearningMachine.createPlayer(agentName); //TODO (Diego: This happens once only - so it's okay as it is).

        // Play the training games.
        System.out.print(trainingPlays + "\n");
        runGames(game_file, level_files, 1, player, actionFiles);
    }

    private static double[] playOnce(LearningPlayer player, String actionFile, String game_file, String level_file,
                                   boolean visuals, int randomSeed, boolean isTraining) throws IOException {
        //Create the game.
        Game toPlay = new VGDLParser().parseGame(game_file);
        toPlay.buildLevel(level_file, randomSeed);

        //Init the player for the game.
        if (player == null || LearningMachine.initPlayer(player, actionFile, toPlay.getObservation(), randomSeed, isTraining) == null) {
            //Something went wrong in the constructor, controller disqualified
            toPlay.disqualify();

            //Get the score for the result.
            return toPlay.handleResult();
        }

        //Then, play the game.
        double[] score;

        // TODO: Fix this little hack
        Player[] players = new Player[]{player};
        if (visuals)
            score = toPlay.playGame(players, randomSeed, false, 0);
        else
            score = toPlay.runGame(players, randomSeed);

        //Finally, when the game is over, we need to tear the player down.
        LearningMachine.tearPlayerDown(player, toPlay);

        return score;
    }


    /**
     * Reads and launches a game for a bot to be played. It specifies which levels to play and how many times.
     * Filenames for saving actions can be specified. Graphics always off.
     * @param game_file game description file.
     * @param level_files array of level file names to play.
     * @param level_times how many times each level has to be played.
     * @param actionFiles names of the files where the actions of this player, for this game, should be recorded. Accepts
     *                    null if no recording is desired. If not null, this array must contain as much String objects as
     *                    level_files.length*level_times.
     */
    public static StatSummary performance;
    public static void runGames(String game_file, String[] level_files, int level_times,
                                LearningPlayer player, String[] actionFiles) throws IOException {
        VGDLFactory.GetInstance().init(); //This always first thing to do.
        VGDLRegistry.GetInstance().init();

        boolean recordActions = false;
        if (actionFiles != null) {
            recordActions = true;
            assert actionFiles.length >= level_files.length * level_times :
                    "runGames (actionFiles.length<level_files.length*level_times): " +
                            "you must supply an action file for each game instance to be played, or null.";
        }

        Game toPlay = new VGDLParser().parseGame(game_file);
        int levelIdx = 0;

        StatSummary[] victories = new StatSummary[toPlay.getNoPlayers()];
        StatSummary[] scores = new StatSummary[toPlay.getNoPlayers()];
        victories[0] = new StatSummary();
        scores[0] = new StatSummary();
        performance = new StatSummary();

        // Player array to hold the single player
        // TODO: Fix this little hack (DIEGO: happy with this for the moment, not important).
        Player[] players = new Player[]{player};

        // TODO: Figure out what to do with the random seed. (DIEGO: Each time a game is played - in any level - a new random seed must be used)
        //Determine the random seed, different for each game to be played.
        int randomSeed = new Random().nextInt();
        // Initialize the player

        //TODO (Diego: This should happen INSIDE playOnce() - you'll have to change what to pass to that function).
        players[0] = LearningMachine.initPlayer(player, actionFiles[0], toPlay.getObservation(), randomSeed, true);

        for (String level_file : level_files) {
            for (int i = 0; i < level_times; ++i) {
                playOneLevel(game_file,level_file,i,recordActions,levelIdx,players,actionFiles,toPlay,scores,victories);
            }
            levelIdx++;
        }

        // TODO: Encircle this bit in a while loop for the duration of 10 minutes from the start of the game.
        // Ask player for next level to play in case the current level is 2
        StateObservation obs = toPlay.getObservation();
        obs.currentGameState = Types.GAMESTATES.CHOOSE_LEVEL;
        player.getServerComm().commSend(new SerializableStateObservation(obs).serialize(null));
        int level = Integer.parseInt(player.getServerComm().commRecv());

        // Play the selected level once
        playOneLevel(game_file,level_files[level],0,recordActions,0,players,actionFiles,toPlay,scores,victories);
        // TODO: Encircle this bit in a while loop for the duration of 10 minutes from the start of the game.

        String vict = "", sc = "";
        for (int i = 0; i < toPlay.no_players; i++) {
            vict += victories[i].mean();
            sc += scores[i].mean();
            if (i != toPlay.no_players - 1) {
                vict += ", ";
                sc += ", ";
            }
        }

        System.out.println("Results in game " + game_file + ", " +
                vict + " , " + sc //);
                + ", " + performance.mean());
    }

    /**
     *
     * @param game_file Game file to be used to play the game. Is sent by parent method.
     * @param level_file Level file to be used to play the game. Is sent by parent method.
     * @param level_time Integer denominating how many times the current level has been played in a row.
     *                   Is also sent from the exterior, and exists for debugging only.
     * @param recordActions Boolean determining whether the actions should be recorded.
     * @param levelIdx Level index. Used for debugging.
     * @param players Array of Player-type objects. Used to play the game
     * @param actionFiles
     * @param toPlay The game to be played. Must be pre-initialized.
     * @param scores Array of scores to be modified. Is modified at the end of the level.
     * @param victories Array of victories to be modified. Is modified at the end of the level.
     * @throws IOException
     */
    public static void playOneLevel(String game_file, String level_file, int level_time, boolean recordActions,
                                    int levelIdx, Player[] players, String[] actionFiles, Game toPlay, StatSummary[] scores,
                                    StatSummary[] victories) throws IOException{
        if (VERBOSE)
            System.out.println(" ** Playing game " + game_file + ", level " + level_file + " (" + level_time + ") **");

        // Create a new random seed for the next level.
        int randomSeed = new Random().nextInt();

        //build the level in the game.
        toPlay.buildLevel(level_file, randomSeed);

        String filename = recordActions ? actionFiles[levelIdx * level_time] : null;

        // Score array to hold handled results
        double[] score;

        // If the player cannot be initialized, disqualify the controller
        if (players[0] == null) {
            //Something went wrong in the constructor, controller disqualified
            //toPlay.disqualify(j);
            toPlay.getAvatars()[0].disqualify(true);
            toPlay.handleResult();
            toPlay.printResult();
        }

        //Play the game
        //Get array of scores back.
        score = toPlay.playGame(players, randomSeed, false, 0);
        toPlay.printResult();

        //Finally, when the game is over, we need to tear the player down.
        LearningMachine.tearPlayerDown(players[0], toPlay);

        //Get player stats
        if (players[0] != null) {
            scores[0].add(score[0]);
            victories[0].add(toPlay.getWinner(0) == Types.WINNER.PLAYER_WINS ? 1 : 0);
        }

        //reset the game.
        toPlay.reset();
    }

    /**
     * Creates a player given its name. This method starts the process that runs this client.
     *
     * @param cmd name of the agent to create.
     * @return the player, created but NOT initialized, ready to start playing the game.
     */
    private static LearningPlayer createPlayer(String cmd) throws IOException {

        Process client;

        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.redirectErrorStream(true);
        //builder.redirectError(new File("logs/processErrorLog.txt"));
        client = builder.start();

        return new LearningPlayer(client);
    }


//
//    private static void printLines(String name, InputStream ins) {
//        String line = null;
//        BufferedReader in = new BufferedReader(
//                new InputStreamReader(ins));
//
//            while ((line = in.readLine()) != null) {
//                System.out.println(name + " " + line);
//            }
//
//    }

    /**
     * Inits the player for a given game.
     *
     * @param player     Player to init.
     * @param actionFile filename of the file where the actions of this player, for this game, should be recorded.
     * @param so         Initial state of the game to be played by the agent.
     * @param randomSeed Seed for the sampleRandom generator of the game to be played.
     * @return the player, created and initialized, ready to start playing the game.
     */
    private static LearningPlayer initPlayer(LearningPlayer player, String actionFile, StateObservation so,
                                             int randomSeed, boolean isTraining) {


        //Determine the time due for the controller initialization.
        ElapsedCpuTimer ect = new ElapsedCpuTimer();
        ect.setMaxTimeMillis(CompetitionParameters.INITIALIZATION_TIME);

        //Initialize the controller.
        if (!player.getServerComm().init(so, ect))
            return null;

        //Check if we returned on time, and act in consequence.
        long timeTaken = ect.elapsedMillis();
        if (ect.exceededMaxTime()) {
            long exceeded = -ect.remainingTimeMillis();
            System.out.println("Controller initialization time out (" + exceeded + ").");
            return null;
        } else {
            System.out.println("Controller initialization time: " + timeTaken + " ms.");
        }

        //If we have a player, set it up for action recording.
        if (player != null)
            player.setup(actionFile, randomSeed, false);


        return player;
    }

    /**
     * Creates a player given its name with package for multiplayer. This class calls the constructor of the agent
     * and initializes the action recording procedure. PlayerID used is 0, default for single player games.
     * @param playerName name of the agent to create. It must be of the type "<agentPackage>.Agent".
     * @param actionFile filename of the file where the actions of this player, for this game, should be recorded.
     * @param so Initial state of the game to be played by the agent.
     * @param randomSeed Seed for the sampleRandom generator of the game to be played.
     * @param isHuman Indicates if the player is human
     * @return the player, created and initialized, ready to start playing the game.
     */

    // TODO: 05/04/2017 Daniel: Finish this up somehow
    private static LearningPlayer initMultiPlayer(LearningPlayer playerName, String actionFile, StateObservationMulti so, int randomSeed, int id, boolean isHuman)
    {
        return playerName;
    }



    /**
     * Tears the player down. This initiates the saving of actions to file.
     * It should be called when the game played is over.
     *
     * @param player player to be closed.
     */
    private static void tearPlayerDown(Player player, Game toPlay) throws IOException {
        //Determine the time due for the controller initialization.
        ElapsedCpuTimer ect = new ElapsedCpuTimer();
        ect.setMaxTimeMillis(CompetitionParameters.TEAR_DOWN_TIME);

        player.teardown(toPlay);
        //player.close();
    }

    /**
     * Tears multiple players down. This initiates the saving of actions to file.
     * It should be called when the game played is over.
     *
     * @param players list of players to be closed.
     */
    private static boolean tearMultiPlayerDown(Player[] players, Game toPlay) throws IOException {
        for (Player p : players) {
            //Determine the time due for the controller close up.
            ElapsedCpuTimer ect = new ElapsedCpuTimer();
            ect.setMaxTimeMillis(CompetitionParameters.TEAR_DOWN_TIME);

            //Inform about the result and the final game state.
            if (toPlay.no_players > 1)
                p.resultMulti(toPlay.getObservationMulti().copy(), ect);
            else
                p.result(toPlay.getObservation(), ect);

            //Check if we returned on time, and act in consequence.
            long timeTaken = ect.elapsedMillis();
            if (ect.exceededMaxTime()) {
                long exceeded = -ect.remainingTimeMillis();
                System.out.println("Controller tear down time out (" + exceeded + ").");

                toPlay.disqualify(p.getPlayerID());
                return false;
            }

            if (VERBOSE)
                System.out.println("Controller tear down time: " + timeTaken + " ms.");
            return true;
        }

        return true;
    }


}