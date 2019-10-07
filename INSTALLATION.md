# Installation

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

