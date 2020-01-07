package milanesa.wasps;

import org.apache.commons.io.FileUtils;
import sun.java2d.pipe.SpanShapeRenderer;

import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalUnit;

public class DbCleaner {

    static int deleteExpiredEntriesAndFiles(Connection dbCon){
        int minutesToExpire = Main.appPrefs.node("cleanup").getInt("minutes_to_expire", 30);
        String uuidDeletionList[] = getUUIDsOfExpiredEntries(dbCon, minutesToExpire);
        if(uuidDeletionList.length > 0){
            ConOut(false, "Found "+uuidDeletionList.length+" entries to clean. Processing...");
            deleteUUIDsFromDatabase(dbCon, uuidDeletionList);
            String filesDirPath = Main.appPrefs.node("dir").get("files_dir", null);
            deleteFilesOfUUIDs(uuidDeletionList, filesDirPath);
        }else {
            ConOut(false, "No expired entries found. Database clean.");
            return 0;
        }
    }

    private static void deleteUUIDsFromDatabase(Connection dbCon, String[] uuidsToDelete){
        String deletionQuery = createDeletionQuery(uuidsToDelete);
        try{
            if(dbCon != null){
                Statement stmt = dbCon.createStatement();
                stmt.execute(deletionQuery);
                ConOut(false, "Successfully cleansed database.");
            }else ConOut(false, "Failed to clean database. Connection null.");
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    private static void deleteFilesOfUUIDs(String[] uuids, String filesDirPath){
        File filesDir = new File(filesDirPath);

        if(!filesDir.exists() || !filesDir.isDirectory()){
            ConOut(true, "Failed to access files directory. Skipping deletion.");
        }else{
            File dirToDelete = null;
            for(String uid : uuids){
                dirToDelete = new File(filesDirPath);

                try {
                    if (dirToDelete.exists() && dirToDelete.isDirectory()){
                        FileUtils.deleteDirectory(dirToDelete);
                        ConOut(false, "Deleted directory: "+uid);
                    }
                }catch(Exception ex){
                    ex.printStackTrace();
                }
            }
        }
    }

    private static String[] getUUIDsOfExpiredEntries(Connection dbCon, int minutesToExpire){
        String maxExpirationDateTime = maxExpirationDateTimeString(minutesToExpire);
        ResultSet resultSet = null;

        try{
            if(dbCon != null) {
                Statement stmt = dbCon.createStatement();
                String query = "SELECT * FROM entries WHERE (creation_date < '"+maxExpirationDateTime+"' OR status = 'failed'";
                resultSet = stmt.executeQuery(query);
                return (String[]) resultSet.getArray("UID").getArray();
            }else return null;
        }catch(Exception ex){
            ex.printStackTrace();
            return null;
        }
    }

    private static String maxExpirationDateTimeString(int minutesToExpire){
        LocalDateTime maximumExpirationDateTime = LocalDateTime.now().minusMinutes(minutesToExpire);
        String dateTimePattern = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat dateFormat = new SimpleDateFormat(dateTimePattern);

        ConOut(false, "Expiration Date and Time: "+dateFormat.format(maximumExpirationDateTime));

        return dateFormat.format(maximumExpirationDateTime);
    }

    private static String createDeletionQuery(String[] strings){
        String query = "DELETE * FROM entries WHERE UID IN (";

        for(String uid : strings){
            query = query.concat("'"+uid+"',");
        }

        query = query.substring(0, query.length() - 1);
        query = query.concat(");");

        return query;
    }

    private static void ConOut(boolean isError, String message){
        String finalOutput = "";
        if(isError) finalOutput = finalOutput.concat("[Error]");
        String className = Thread.currentThread().getStackTrace()[1].getClassName();
        finalOutput = finalOutput.concat("["+className+"]");
        String methodName = Thread.currentThread().getStackTrace()[1].getMethodName();
        finalOutput = finalOutput.concat("["+methodName+"] ");
        finalOutput = finalOutput.concat(message);
        System.out.println(finalOutput);
    }
}
