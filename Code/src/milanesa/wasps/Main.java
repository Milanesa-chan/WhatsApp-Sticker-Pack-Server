package milanesa.wasps;

import org.apache.commons.io.FileUtils;
import org.ini4j.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.*;
import java.util.prefs.Preferences;

public class Main {
    static Logger logger = Logger.getLogger("MainLog");

    static Preferences appPrefs;
    static String WASPCPath;
    //WASPC = WhatsApp Sticker Pack Creator (The app that creates
    // sticker packs used by every worker individually);
    private static String[] latinGodNames = {"Titia", "Suher", "Duias", "Voesis", "Caasis", "Osus", "Dygnos", "Titon", "Keken", "Iteyar"};
    private static ArrayList<Worker> workersList;
    private static Random RNG = new Random();
    private static boolean working, cleaning;
    private static Queue<String> failQueue;

    public static void main(String[] args){
        //Obtain ini file and params
        String jarPath = getJarPath();
        setupLogger(jarPath);
        
        appPrefs = loadPreferencesFromIni(jarPath+"/prefs.ini");
        WASPCPath = jarPath+"/WASPC";

        //Test connection with database
        //testDatabaseConnection();

        //Gradle Daemon is a background application that makes consecutive executions of WASPC work faster
        //but it locks some files from being deleted. This should stop all of them so WASPS
        //Can empty the workers directory.
        stopGradleDaemon(WASPCPath);

        //Prepare directories (file_dir and workers_dir)
        setupDirectories();

        //Start up the workers
        createWorkers();

        //Initializes the queue to where failed tasks go
        failQueue = new LinkedList<>();

        //Starts loops.

        Thread workManagerThread = new Thread(Main::workManagerLoop);
        workManagerThread.start();

        Thread dbCleanupThread = new Thread(Main::dbCleanupLoop);
        dbCleanupThread.start();
    }

    static void setupLogger(String jarPath){
        File logsDir = new File(jarPath.concat("/logs"));
        if(!logsDir.exists()) logsDir.mkdirs();

        LogManager.getLogManager().reset();

        FileHandler fileHandler = null;
        SimpleDateFormat format = new SimpleDateFormat("M-d_HHmmss");
        String logPath = jarPath.concat("/logs/MainLog_" + format.format(Calendar.getInstance().getTime()) + ".log");

        try {
            fileHandler = new FileHandler(logPath);
        }catch(Exception ex){
            ex.printStackTrace();
            Runtime.getRuntime().exit(1);
        }

        fileHandler.setFormatter(new SimpleFormatter());
        logger.addHandler(fileHandler);
        logger.info("Logger setup correct.");
    }

    private static void workManagerLoop(){
        working = true;
        Connection dbCon;
        int sleepingTime = 1000;
        while(working){
            try{
                Thread.sleep(sleepingTime);
            }catch(Exception ex){ex.printStackTrace();}
            dbCon = getDatabaseConnection();
            if(dbCon != null) {
                sleepingTime = 1000;
                approveWorkers(dbCon);
                giveWorkToReadyWorker(dbCon);
            }else{
                System.out.println("[Error][workManagerLoop] Failed connection. Retrying in 10 seconds.");
                sleepingTime = 10000;
            }
            try{
                dbCon.close();
            }catch(Exception ex){}
        }
    }

    static void notifyFailed(String UID){
        failQueue.add(UID);
    }

    private static void giveWorkToReadyWorker(Connection dbCon){
        Worker nextReadyWorker = getReadyWorker();
        if(nextReadyWorker != null){
            ResultSet pendingEntry = getOnePendingEntry(dbCon);
            try {
                if(pendingEntry != null && pendingEntry.next()) {
                    String entryUID = pendingEntry.getString("UID");
                    String entryPackName = pendingEntry.getString("pack_name");
                    nextReadyWorker.startNewTask(entryUID, entryPackName);
                    updateStatusInDb(entryUID, "working", dbCon);
                }
            }catch(Exception ex){ex.printStackTrace();}
        }
    }

    private static void dbCleanupLoop(){
        cleaning = true;
        Connection dbCon;
        int sleepingTime = 5000;
        try{
            while(cleaning) {
                Thread.sleep(sleepingTime);
                dbCon = getDatabaseConnection();
                if(dbCon != null){
                    sleepingTime = 5000;

                    reportFailedTasks(dbCon);
                    DbCleaner.deleteExpiredEntriesAndFiles(dbCon);

                    dbCon.close();
                }else{
                    System.out.println("[Error][dbCleanupLoop] Failed connection. Retrying in 10 seconds.");
                    sleepingTime = 10000;
                }
            }
        }catch(Exception ex){ex.printStackTrace();}
    }

    private static void reportFailedTasks(Connection dbCon){
        try{
            String failedUID;
            while(!failQueue.isEmpty()){
                failedUID = failQueue.remove();
                updateStatusInDb(failedUID, "failed", dbCon);
                System.out.println("[reportFailedTasks] Failed task: "+failedUID+" has been reported in database.");
            }
        }catch(Exception ex){ex.printStackTrace();}
    }

    private static void updateStatusInDb(String UID, String status, Connection dbCon){
        try {
            String query = "UPDATE entries SET status=? WHERE uid=?";
            PreparedStatement stmt = dbCon.prepareStatement(query);
            stmt.setString(1, status);
            stmt.setString(2, UID);
            stmt.executeUpdate();
        }catch(Exception ex){ex.printStackTrace(); }
    }

    private static ResultSet getOnePendingEntry(Connection dbCon){
        try{
            if(dbCon != null) {
                Statement stmt = dbCon.createStatement();
                String query = "SELECT * FROM entries WHERE status=\"pending\"";
                return stmt.executeQuery(query);
            }else{return null;}
        }catch(Exception ex){ex.printStackTrace(); return null;}
    }

    private static Worker getReadyWorker(){
        synchronized (workersList){
            for(Worker w : workersList){
                if(w.status == Worker.WorkerStatus.READY) return w;
            }
            return null;
        }
    }

    private static void approveWorkers(Connection dbCon){
        for(Worker w : workersList){
            if(w.status == Worker.WorkerStatus.WAITING_FOR_APPROVAL){
                String UID = w.currentUID;
                updateStatusInDb(UID, "done", dbCon);
                w.approveTask();
            }
        }
    }

    private static void createWorkers(){
        System.out.println("[createWorkers] Starting worker creation...");
        int amountOfWorkers = appPrefs.node("workers").getInt("amount", 0);
        if(amountOfWorkers == 0){
            System.out.println("[Error][createWorkers] Problem reading ini file. Aborting.");
            Runtime.getRuntime().exit(1);
        }else{
            ArrayList<String> usedNames = new ArrayList<>();
            workersList = new ArrayList<>();

            int randomNameLimit = latinGodNames.length-1;

            for(int w=0; w<amountOfWorkers; w++){
                String currentName = latinGodNames[RNG.nextInt(randomNameLimit)];
                //String currentName = "AraAraMaMa"; //DEBUG
                while(usedNames.contains(currentName)){
                    currentName = latinGodNames[RNG.nextInt(randomNameLimit)];
                }
                workersList.add(new Worker(currentName));
                usedNames.add(currentName);
            }
        }
    }

    private static void setupDirectories(){
        //Checks if "file_dir" is in condition to be used.
        String fileDirPath = appPrefs.node("dir").get("files_dir", null);
        if(fileDirPath == null){
            System.out.println("[Error][setupDirectories] Error when reading \"files_dir\" from the ini file. Aborting.");
            Runtime.getRuntime().exit(1);
        }else{
            File fileDir = new File(fileDirPath);
            if(!fileDir.exists()){
                System.out.println("[Error][setupDirectories] Could not find directory: "+fileDirPath+". Aborting.");
                Runtime.getRuntime().exit(1);
            }else if(!fileDir.isDirectory()){
                System.out.println("[Error][setupDirectories] Path: "+fileDirPath+" is not a directory. Aborting.");
                Runtime.getRuntime().exit(1);
            }else{
                System.out.println("[setupDirectories] Files directory correct.");
            }
        }

        //Checks if "workers_dir" is in condition to be used.
        String workersDirPath = appPrefs.node("dir").get("workers_dir", null);
        if(workersDirPath == null){
            System.out.println("[Error][setupDirectories] Error when reading \"workers_dir\" from the ini file. Aborting.");
            Runtime.getRuntime().exit(1);
        }else{
            File workersDir = new File(workersDirPath);
            if(!workersDir.exists()){
                System.out.println("[Error][setupDirectories] Could not find directory: "+workersDirPath+". Creating it...");
                workersDir.mkdirs();
            }else if(!workersDir.isDirectory()){
                System.out.println("[Error][setupDirectories] Path: "+workersDirPath+" is not a directory. Aborting.");
                Runtime.getRuntime().exit(1);
            }else if(workersDir.listFiles().length > 0) {
                boolean autoEmptyWorkersDir = Boolean.valueOf(appPrefs.node("dir").get("empty_workers_directory", "false"));
                if (autoEmptyWorkersDir) {
                    try {
                        System.out.println("[setupDirectories] Workers directory has files in it. Emptying it...");
                        FileUtils.cleanDirectory(workersDir);
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, "Failed to empty workers directory", ex);
                        System.out.println("[Error][setupDirectories] Couldn't empty workers directory. Aborting.");
                        Runtime.getRuntime().exit(1);
                    }
                } else {
                    System.out.println("[Error][setupDirectories] Path: " + workersDirPath + " has files in it. If you want to empty it automatically change the option in prefs.ini.");
                    Runtime.getRuntime().exit(1);
                }
            }
            System.out.println("[setupDirectories] Workers directory correct.");
        }
    }

    private static void testDatabaseConnection(){
        Preferences dbPrefs = appPrefs.node("db");
        String db = dbPrefs.get("database", "");
        String table = dbPrefs.get("table", "");
        String user = dbPrefs.get("user", "");
        String password = dbPrefs.get("password", "");

        Connection conn;
        try {
            System.out.println("[testDatabaseConnection] Testing connection to database...");
            conn = DriverManager.getConnection("jdbc:mysql://localhost/"+db+"?" +
                    "user="+user+"&password="+password+"&serverTimezone=UTC&db="+db+"&table="+table);
            if(conn != null){
                System.out.println("[testDatabaseConnection] Database successfully connected.");
                System.out.println("[testDatabaseConnection] Database: "+db+" Table: "+table);
                conn.close();
            }
        }catch(Exception ex){
            //ex.printStackTrace();
            System.out.println("[Error][testDatabaseConnection] Failed to connect to database.");
            Runtime.getRuntime().exit(1);
        }
    }

    private static Connection getDatabaseConnection(){
        Preferences dbPrefs = appPrefs.node("db");
        String db = dbPrefs.get("database", "");
        String table = dbPrefs.get("table", "");
        String user = dbPrefs.get("user", "");
        String password = dbPrefs.get("password", "");

        Connection conn;
        try {
            conn = DriverManager.getConnection("jdbc:mysql://localhost/"+db+"?" +
                    "user="+user+"&password="+password+"&serverTimezone=UTC&db="+db+"&table="+table);
            if(conn != null){
                return conn;
            }else{
                System.out.println("[Error][getDatabaseConnection] Failed to connect to database.");
                return null;
            }
        }catch(Exception ex){
            //ex.printStackTrace();
            System.out.println("[Error][getDatabaseConnection] Failed to connect to database.");
            return null;
        }
    }

    private static Preferences loadPreferencesFromIni(String inipath){
        try {
            Ini iniFile = new Ini();
            iniFile.load(new FileReader(inipath));
            System.out.println("[loadPreferencesFromIni] Ini file obtained.");
            return new org.ini4j.IniPreferences(iniFile);
        }catch(Exception ex){
            System.out.println("[Error][loadPreferencesFromIni] Couldn't read ini preferences. Aborting.");
            ex.printStackTrace();
            Runtime.getRuntime().exit(1);
            return null;
        }
    }

    private static String getJarPath(){
        try {
            File jarFile = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());
            String encodedJarPath = jarFile.getParentFile().getAbsolutePath();
            return URLDecoder.decode(encodedJarPath, "UTF-8");
        } catch (Exception ex){
            ex.printStackTrace();
        }

        return "Error";
    }

    private static int stopGradleDaemon(String WASPCPath){
        System.out.println("[stopGradleDaemon] Stopping all Gradle Daemons to avoid file locking.");

        File waspcDir = new File(WASPCPath);

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(waspcDir);
        processBuilder.command("java", "-jar", "WASPC.jar", "-stopgd");

        try{
            Process waspcProcess = processBuilder.start();

            new Thread(() -> {
                try {
                    InputStreamReader isr = new InputStreamReader(waspcProcess.getInputStream());
                    BufferedReader br = new BufferedReader(isr);
                    String nextLine;
                    while ((nextLine = br.readLine()) != null) {
                        // output nextLine
                    }
                }catch(Exception ex){ex.printStackTrace();}

            }).start();

            new Thread(() -> {
                try {
                    InputStreamReader isr = new InputStreamReader(waspcProcess.getErrorStream());
                    BufferedReader br = new BufferedReader(isr);
                    String nextLine;
                    while ((nextLine = br.readLine()) != null) {
                        // output nextLine
                    }
                }catch(Exception ex){ex.printStackTrace();}

            }).start();

            int waspcExitCode = waspcProcess.waitFor();

            if(waspcExitCode != 0){
                System.out.println("[stopGradleDaemon] WASPC Process finished with exit code: "+waspcExitCode+". Aborting.");
                Runtime.getRuntime().exit(waspcExitCode);
                return 1;
            }else System.out.println("[stopGradleDaemon] Gradle Daemons stopped successfully.");

            return 0;
        }catch(Exception ex){
            System.out.println("[stopGradleDaemon] An error occurred while stopping the Gradle Daemon. Aborting.");
            Runtime.getRuntime().exit(1);
            return 1;
        }
    }
}
