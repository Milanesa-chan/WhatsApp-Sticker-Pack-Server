# Installation

### Preparing the working directories

The application needs three directories to work properly. First, choose and prepare them carefully according to their requirements.

- **Main Dir**: The directory in which the .jar file is run. The .jar has to be put there along with all the 'Execution Directory Assets'.
- **Workers Dir**: The directory in which workers will dump their files and execute android builds. Can be anywhere in the filesystem. **BE CAREFUL, THIS DIRECTORY WILL BE AUTOMATICALLY EMPTIED ON EXECUTION WITHOUT WARNING. MAKE SURE NO IMPORTANT FILES ARE IN IT.**
- **Files Dir**: The directory from which workers pick up files to process (and to which the front-end should upload them). This can have other files on execution and will only be modified to return .apk files.

### Database

The MySQL database connection information has to be set in the prefs.ini file.

The structure of the table can be set using the SQL Query below (this will create a table named 'entries'):

```SQL
CREATE TABLE `entries` (
 `key` int(10) NOT NULL AUTO_INCREMENT,
 `UID` varchar(50) NOT NULL,
 `pack_name` varchar(32) NOT NULL,
 `creation_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 `status` varchar(15) NOT NULL DEFAULT 'pending',
 PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1
```

