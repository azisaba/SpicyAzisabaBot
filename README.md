# SpicyAzisabaBot

## Environment variables

- BOT_TOKEN - Discord bot token
- MARIADB_HOST - Hostname or IP address of the mariadb server
- MARIADB_NAME - Database name
- MARIADB_USERNAME - Database username
- MARIADB_PASSWORD - Database password
- MARIADB_USE_SSL (default: true)
- MARIADB_VERIFY_SERVER_CERT (default: true)
- REAL_PROBLEM_CHANNEL_ID - set to 0 if you are not interested
- DEVELOPER_ROLE_ID - this thing is used to check if a user has the permission to use some command. set to 0 if you are not interested
- SELF_INTRO_CHANNEL - set to 0 if you are not interested
- LEAVE_LOG_CHANNEL - set to 0 if you are not interested
- WELCOME_CHANNEL_ID - don't set if you are not interested
- INVITE_LINKS - to be sent in the welcome channel
- MESSAGE_VIEWER_BASE_URL - without trailing slash
- BUILDER_ROLE_ID - a role that allows building arbitrary project using /build. set to 0 if you are not interested
- DOCKER_HOST - for /build command. set to empty string (or don't set) if you want to disable the command
- GITHUB_TOKEN - personal access token for /build command. used when fetching pull request and head repository. this is optional, anonymous authentication will be used if left empty.
