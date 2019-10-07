# Installation

### Preparing the working directories

The application needs three directories to work properly. First, choose and prepare them carefully according to their requirements.

- **Main Dir**: The directory in which the .jar file is run. The .jar has to be put there along with all the 'Execution Directory Assets'.
- **Workers Dir**: The directory in which workers will dump their files and execute android builds. Can be anywhere in the filesystem. **BE CAREFUL, THIS DIRECTORY WILL BE AUTOMATICALLY EMPTIED ON EXECUTION WITHOUT WARNING. MAKE SURE NO IMPORTANT FILES ARE IN IT.**
- **Files Dir**: The directory from which workers pick up files to process (and to which the front-end should upload them). This can have other files on execution and will only be modified to return .apk files.

Proceed to the next sections once all directories are prepared.

### Installing the Android SDK

WASPC Requires the Android SDK to be installed in your system. This guide will not go in depth on how to install it as there are plenty of guides out there. The easy path of doing so is installing [Android Studio](https://developer.android.com/studio) which will install the SDK for you and prompt you to configure it. WASPC is know to work with **API Level 21** of the android platform tools, but this can change.

### Preparing WASPC

This application relies on [WASPC](https://github.com/Milanesa-chan/WhatsApp-Sticker-Pack-Creator) as the main engine for the workers. It in turn relies on Gradle, an Android build manager, to compile the final .sdk file. Gradle needs to know the location of the aforementioned Android SDK root directory. To set this up you have to go to (within the **Main Dir**): *WASPC/android* and check if there is a *local.properties* file. If there is not, create it.

The file should be set to have the next line: ```sdk.dir=``` followed by your android-sdk* root directory (sectioned using '/').

**\***: The android-sdk root is the folder where "tools", "platforms" and "build-tools" can be found along with others.

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

