# WhatsApp Sticker Pack Server (WASPS)

## This project is archived and the developer will not continue developing it. Feel free to fork it and do whatever you want under the licences of it and its dependencies.

[Installation](https://github.com/Milanesa-chan/WhatsApp-Sticker-Pack-Server/blob/master/INSTALLATION.md)

Backend for the [WASPC](https://github.com/Milanesa-chan/WhatsApp-Sticker-Pack-Creator "WASPC Repository") Web application.

# How it Works

Here's a little diagram to try to explain the role of this application in the whole Web app. Everything inside the "WASPS" box is what can be found in this repository. As a detail the "WASPS Workers" have a copy of "WASPC" inside each of them and that is how they create stickerpacks independently.

![](https://raw.githubusercontent.com/Milanesa-chan/WhatsApp-Sticker-Pack-Server/master/tools/Workflow%20Diagram.png)

# Database Requirements

WASPS organizes both status information and queueing in a MySQL database. The database connection data has to be set in "prefs.ini" found in the "Execution Directory Assets" directory.
