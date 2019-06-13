# WhatsApp Sticker Pack Server (WASPS)

Backend for the [WASPC](https://github.com/Milanesa-chan/WhatsApp-Sticker-Pack-Creator "WASPC Repository") Web application. The project is advancing very slowly as it is not a priority for the developer right now. Feel free to fork it and do what you want with this.

# How it Works

Here's a little diagram to try to explain the role of this application in the whole Web app. Everything inside the "WASPS" box is what can be found in this repository. As a detail the "WASPS Workers" have a copy of "WASPC" inside each of them and that is how they create stickerpacks independently.

![](https://raw.githubusercontent.com/Milanesa-chan/WhatsApp-Sticker-Pack-Server/master/Workflow%20Diagram.png)

# Database Requirements

WASPS organizes both status information and queueing in a MySQL database. The database connection data has to be set in "prefs.ini" found in the "Execution Directory Assets" directory.
