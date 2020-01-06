package milanesa.wasps;

import sun.java2d.pipe.SpanShapeRenderer;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalUnit;

public class DbCleaner {

    static int deleteExpiredEntriesAndFiles(Connection dbCon, int minutesToExpire){
        String uuidDeletionList[] = getUUIDsOfExpiredEntries(dbCon, minutesToExpire);
        if(uuidDeletionList.length > 0){
            ConOut(false, "Found "+uuidDeletionList.length+" entries to clean. Processing...");

        }else {
            ConOut(false, "No expired entries found. Database clean.");
            return 0;
        }
    }

    static String[] getUUIDsOfExpiredEntries(Connection dbCon, int minutesToExpire){
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

    static String maxExpirationDateTimeString(int minutesToExpire){
        LocalDateTime maximumExpirationDateTime = LocalDateTime.now().minusMinutes(minutesToExpire);
        String dateTimePattern = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat dateFormat = new SimpleDateFormat(dateTimePattern);

        ConOut(false, "Expiration Date and Time: "+dateFormat.format(maximumExpirationDateTime));

        return dateFormat.format(maximumExpirationDateTime);
    }

    static void deleteUUIDsFromDatabase(Connection dbCon, String[] uuidsToDelete){
        String deletionQuery = createDeletionQuery(uuidsToDelete);
        
    }

    static String createDeletionQuery(String[] strings){
        String query = "DELETE * FROM entries WHERE UID IN (";

        for(String uid : strings){
            query = query.concat("'"+uid+"',");
        }

        query = query.substring(0, query.length() - 1);
        query = query.concat(");");

        return query;
    }

    static void ConOut(boolean isError, String message){
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
