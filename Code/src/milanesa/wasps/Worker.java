package milanesa.wasps;

import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.prefs.Preferences;

public class Worker implements Runnable{
    public String name;
    enum WorkerStatus {STARTING, READY, WORKING, WAITING_FOR_APPROVAL};
    public WorkerStatus status = WorkerStatus.STARTING;
    String currentUID;
    public String currentPackName;

    private String filesPath;
    private String workPath;
    private File workDir;
    private Thread workThread;

    Worker(String workerName){
        name = workerName;
        workThread = new Thread(this);
        workThread.start();
    }

    public void run(){
        try {
            workPath = createWorkPath(name);
            workDir = new File(workPath);
            filesPath = Main.appPrefs.node("dir").get("files_dir", null);
            deployWASPC();
            do {
                synchronized (this) {
                    ConOut(false, "Ready for next task.");
                    status = WorkerStatus.READY;
                    wait(); //Waits for the "startNewTask" method to notify.
                    ConOut(false, "Task received: "+currentUID+". Working...");
                    checkInputDirState(); //Checks if "input" directory is clean. If not, empties it.
                    String sourcePath = filesPath + "/" + currentUID;
                    File sourceDir = new File(sourcePath);
                    if (!sourceDir.exists()) {
                        System.out.println("[Error][Worker: " + name + "] UID: " + currentUID + " has no source directory. Skipping task.");
                        Main.notifyFailed(currentUID);
                    } else if (!sourceDir.isDirectory()) {
                        System.out.println("[Error][Worker: " + name + "] UID: " + currentUID + " directory is incorrect. Skipping task.");
                        Main.notifyFailed(currentUID);
                    } else {
                        File inputDir = new File(workPath+"/input");
                        ConOut(false, "Copying source files.");
                        copyInputFiles(sourceDir, inputDir);
                        int waspcExitCode = startWASPC(workPath, currentPackName);
                        if(waspcExitCode != 0){
                            ConOut(true, "WASPC Failed when processing task: "+currentUID+". Skipping task.");
                            Main.notifyFailed(currentUID);
                        }else {
                            moveOutputToSourceDir(workPath + "/output", sourcePath, currentPackName);
                            ConOut(false, "Waiting for task approval.");
                            status = WorkerStatus.WAITING_FOR_APPROVAL;
                            wait();
                        }
                    }
                }
            } while (!status.equals("dead"));
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    void approveTask(){
        synchronized (this){
            notify();
        }
    }

    private void moveOutputToSourceDir(String outputPath, String sourcePath, String packName){
        File outputFile = new File(outputPath + "/CustomStickerPack.apk");
        File finalFile = new File(sourcePath + "/"+packName+".apk");
        try {
            FileUtils.moveFile(outputFile, finalFile);
            ConOut(false, "Output file has been moved.");
        }catch(Exception ex){ex.printStackTrace();}
    }

    private int startWASPC(String workPath, String packName){
        ConOut(false, "Starting WASPC process.");
        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(new File(workPath));
        String command = workPath+"/Start.bat";
        builder.command(command, packName);
        try {
            //builder.inheritIO();
            Process buildProcess = builder.start();

            new Thread(() -> {
                try {
                    InputStreamReader isr = new InputStreamReader(buildProcess.getInputStream());
                    BufferedReader br = new BufferedReader(isr);
                    String nextLine;
                    while ((nextLine = br.readLine()) != null) {
                        //ConOut(false, nextLine);
                    }
                }catch(Exception ex){ex.printStackTrace();}

            }).start();

            new Thread(() -> {
                try {
                    InputStreamReader isr = new InputStreamReader(buildProcess.getErrorStream());
                    BufferedReader br = new BufferedReader(isr);
                    String nextLine;
                    while ((nextLine = br.readLine()) != null) {
                        //ConOut(false, nextLine);
                    }
                }catch(Exception ex){ex.printStackTrace();}

            }).start();

            buildProcess.waitFor();

            ConOut(false, "WASPC Process finished.");

            return buildProcess.exitValue();
        }catch(Exception ex){
            ex.printStackTrace();
            return 1;
        }
    }

    private void copyInputFiles(File sourceDir, File inputDir){
        try {
            FileUtils.copyDirectory(sourceDir, inputDir);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    private void checkInputDirState(){
        File inputDir = new File(workPath + "/input");
        if(inputDir.listFiles().length > 0){
            try {
                FileUtils.cleanDirectory(inputDir);
            }catch(Exception ex){
                ex.printStackTrace();
            }
        }
    }

    void startNewTask(String UID, String packName){
        synchronized (this) {
            currentUID = UID;
            currentPackName = packName;
            status = WorkerStatus.WORKING;
            notify();
        }
    }

    private String createWorkPath(String workerName){
        Preferences prefs = Main.appPrefs;
        String workersPath = prefs.node("dir").get("workers_dir", null);
        String thisWorkerPath = workersPath+"/"+workerName;
        File thisWorkerDir = new File(thisWorkerPath);
        if(!thisWorkerDir.exists()) thisWorkerDir.mkdirs();
        return thisWorkerPath;
    }

    private void deployWASPC(){
        String WASPCPath = Main.WASPCPath;
        File WASPCDir = new File(WASPCPath);
        if(!WASPCDir.exists()){
            System.out.println("[Error][deployWASPC] Path: "+WASPCPath+" does not exist. Aborting.");
            Runtime.getRuntime().exit(1);
        }else if(!WASPCDir.isDirectory()){
            System.out.println("[Error][deployWASPC] Path: "+WASPCPath+" is not a directory. Aborting.");
            Runtime.getRuntime().exit(1);
        }else{
            File workDir = new File(workPath);
            System.out.println("[Worker: "+name+"] WASPC Deploy in progress...");
            try {
                FileUtils.copyDirectory(WASPCDir, workDir);
            }catch(Exception ex){
                ex.printStackTrace();
            }
            System.out.println("[Worker: "+name+"] WASPC Deployed.");
        }
    }

    private void ConOut(boolean isError, String message){
        String toOut = "";
        if(isError) toOut = toOut.concat("[Error]");
        toOut = toOut.concat("[Worker: "+this.name+"][Task: "+currentUID+"] "+message);
        System.out.println(toOut);
    }
}
