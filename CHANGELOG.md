# Changelog

## [4.5.0] - 18-11-2025

### Added
- **Extra Arguments Support for WoW & WoW Hardcore Views**:
    - Added the ability to include **extraArguments** when creating new views for **World of Warcraft** and **World of Warcraft Hardcore**.
    - This enables specifying additional configuration such as:
      ```json
      "extraArguments": {
          "type": "com.kos.views.WowExtraArguments",
          "season": 15,
          "isGuild": false
      }
      ```
    - The system now correctly validates these arguments according to the selected game type.
  
## [4.4.0] - 22-02-2025

### Added
- **Search Individual Entities Endpoint** (`GET /api/entities`):
    - Introduced a new endpoint to search for **individual entities** rather than only tracking groups through views.
    - Supports searching for **World of Warcraft (WOW), WoW Hardcore (WOW_HC), and League of Legends (LOL)** entities.
    - **Required query parameters depend on the game type**:
        - **WoW & WoW Hardcore** → `name`, `region`, `realm`
        - **LoL** → `name`, `tag`
        - `game` parameter is always required (Possible values are `wow`, `wow_hc` and `lol`)
    - If required parameters are missing or an unknown game is provided, the API returns `400 Bad Request`.

## [4.3.3] 25-01-2025

### Added
- **Feature Alias**: Added a feature to define aliases for entities associated with views.
  - This allows users to create and manage custom names for entities, enhancing clarity and usability when working with complex data structures or multiple entities in a view.

## [4.3.2] 16-01-2025

### Added
- **League of Legends API Update**: The League of Legends API now retrieves the match-up for every game, allowing users to view the match-up information for each character in every game.

## [4.3.1] 10-01-2025

### Added
- **WoW Classic Characters Differentiation**: Characters of WoW Classic with the same name, server, and region are now uniquely identified using the internal Blizzard identifier.
    - This ensures accurate handling and distinction of characters sharing similar attributes.

### Fixed
- **WoW Hardcore Characters Death Flagging**:
    - **Non-Existent Characters**: Hardcore characters that do not exist are now flagged as dead.
    - **API Removal Handling**: Characters returning a `404` from the Blizzard API are flagged as dead, reflecting their removal from the Blizzard database.
    - **Repository Cleanup**: Characters returning a `404` are removed from the characters repository if no record exists for them in the cache.
        - This prevents stale or invalid entries from persisting in the database.

## [4.3.0] 22-12-2024

### Added
- **Metadata in Get Views Endpoint**: Introduced support for including metadata in the `Get Views` endpoint. By using the `include` parameter with the value `metadata`, users can now receive additional information, such as the total number of views. This is especially useful for users implementing pagination.

### Fixed
- **Featured Views Query Parameter**: Resolved an issue where the `featured` query parameter in the `Get Views` endpoint was not functioning as intended.

## [4.2.3] 17-12-2024
- **Limit and pagination added for Get Views Endpoint**:

## [4.2.2] 15-12-2024

### Added
- **Queue Status Endpoint**: Introduced a new endpoint to check the status of queues.
    - This feature provides visibility into the progress and state of queued events.

## [4.2.1] 12-12-2024

### Added
- **Featured Filter for Get Views Endpoint**: Added a new filter to the Get Views endpoint, enabling the retrieval of featured views from all games or a specific game. This enhancement allows users to quickly access highlighted views.

## [4.2.0] 15-11-2024

### Added
- **WoW Hardcore Views**: Introduced support for **World of Warcraft Hardcore Views**, allowing users to create and manage views specifically for hardcore characters.
- **Event Sourcing for WoW Characters**: Synchronization for WoW characters, including **Mythic+** and **Hardcore**, is now handled via event sourcing, improving efficiency and scalability.

## [4.1.1] 15-11-2024

### Added
- **Game-Based View Filtering**: Introduced a new filter to retrieve views specific to a particular game.
    - This enhancement improves user experience by allowing targeted retrieval of views for games like "World of Warcraft" or "League of Legends."

## [4.1.0] 12-11-2024

### Changed
- **Credentials System Update**: Enhanced the credentials management system with new requirements and modifications:
    - **Create Credential**: Now requires a set of roles to be provided in the request, ensuring that each credential is created with defined permissions.
    - **Edit Credential**: Endpoint updated to `/credentials/{user}` (previously `/credentials`). This operation now requires both `password` and `roles` to be included in the request.
    - **Patch Credential**: Introduced a new `PATCH` endpoint for credentials, similar to the edit functionality but with flexibility—fields such as `password` and `roles` can be optionally included.

### Removed
- **Activity and Role Management**: Removed the ability to create or delete activities and roles directly, streamlining the credential's system.

## [4.0.1] 10-11-2024
### Changed
- **League of Legends Background Sync**: Now league of legend background sync is optimized and only syncs characters that have not been synced by any other source.

## [4.0.0] 09-11-2024
### Added
- **Event Sourcing Implementation**: Introduced a major architecture change with event sourcing for resource management. Previously, creating large views was not sustainable, as it required waiting for external systems to respond before proceeding. Now, when a user creates a view, an operation is queued, and an operation ID is returned, which will be used to track the status of the requested action over the resource.
- **View Creation Process**: Views will be created once the subscriptions process the queued events, improving the overall efficiency of resource handling and allowing for better scalability.
- **Queue System for Syncing League of Legends Characters**: League of Legends view updates now send characters for updates via queues, in addition to the background task. This ensures that views can be populated faster, as characters receive individual updates immediately, instead of waiting for a scheduled or forced background task to run.

### Changed
- **JWT-Based Authentication System**: Replaced the existing token system with JSON Web Tokens (JWT) to enhance authentication efficiency and reduce database load.
    - **Self-Contained Permissions**: Permissions are now embedded directly within the JWT, removing the need to query the database for permission checks on each request.
    - **Improved Performance**: This change significantly improves response times for authenticated requests by reducing dependency on database lookups for role-based access validation.
    - **Security Enhancements**: JWTs are securely signed, ensuring token authenticity and integrity without frequent database validation.

## [3.5.1] 04-11-2024

### Added
- **Character Limit by Role in Views**: Introduced a new feature that limits the maximum number of characters allowed per view based on user roles.

## [3.5.0] 03-11-2024

### Added
- Introduced a daily update for League characters to refresh summoner details, including summoner icon, summoner level, Riot name, and Riot tag every 24 hours.
- Optimized further the League Character's Sync by allowing the reuse of match data across multiple players in the same synchronization batch, leveraging dynamic programming to minimize calls. While this may not drastically increase capacity, it significantly improves efficiency in the synchronization process.

### Changed
- Updated the `getData` and `getCachedData` endpoints to include the `viewName` in the response. This change may break integration with existing frontends expecting the previous response format.

## [3.4.1] 01-11-2024

### Improved
- Implemented a mechanism to reuse cached matches, significantly reducing unnecessary API calls and improving League caching time.

## [3.4.0] 31-10-2024

### Improved
- Enhanced caching service for League characters, allowing for larger views with a greater number of matches per character.
- Integrated `Flow` and `Channels` to optimize memory usage, ensuring more efficient handling of concurrent data streams.

## [3.3.0] 30-10-2024

### Added

- **Task Filtering by Type**: Introduced a new feature allowing tasks to be filtered by `taskType`.
    - This enhancement improves user control and efficiency by enabling targeted task retrieval.

- **Query Parameter Validation**: Added validation for query parameters to ensure data integrity and prevent potential errors.

## [3.2.0] 28-10-2024

### Changed

- **View Limit by Role**: Updated the view creation limit to be role-based instead of a fixed number:
    - **Admin** now have no limit on the number of views they can create.
    - **User** remain limited to a maximum of **2** views.

  This enhancement provides greater flexibility and control, especially for administrators managing multiple views.


## [3.1.0] 18-10-2024

### Added

- **Behavior Change**: The run task endpoint now returns the task ID in the `Location` header upon successful execution.

### Changed

- **Endpoint Update**: Changed endpoint from `POST /api/tasks/run` to `POST /api/tasks`.

## [3.0.1] 17-10-2024

### Fixed

- **League Match Retrieval Bug**: Resolved a bug that prevented League matches for cached characters from being
  retrieved correctly across different queue types.
    - This fix ensures accurate and reliable match data for all queue types, improving the integrity of character
      performance information.

## [3.0.0] 13-10-2024

### Added

- **Background Task Management API**: Implemented a comprehensive API for managing background tasks:
    - **Immediate Execution**: Added an endpoint to execute background tasks immediately.
    - **Task Retrieval**: Introduced endpoints to retrieve all tasks and to fetch a specific task by its ID.
        - This enhancement improves task management capabilities and provides users with better control and visibility
          over background processes.

### Changed

- **Task Execution Timing**: Modified the execution logic for background tasks so that they are not executed every time
  the server starts. Instead, the first execution is delayed, taking into account the last time each task was executed.

## [2.0.0] 05-10-2024

### Added

- **Multi-Game View Creation**: Expanded functionality to allow the creation of both World of Warcraft views and League
  of Legends views.
    - Users can now create, manage, and customize views for both games within the same application, enhancing
      versatility and user engagement.

- **New Field: Game**: Introduced a new field, "game," within the scope of views, which can be "WOW" or "LOL." This
  field is required in both create and edit requests for views.
- **Character Request Types**: Specified that the types `com.kos.characters.WowCharacterRequest`
  and `com.kos.characters.LolCharacterRequest` need to be sent as part of the view requests when adding characters due
  to a software limitation.

## [1.3.0] 30-09-2024

### Added

- **Background Task Execution Results Registration**: Enhanced the existing background task execution logging by now
  registering the results of each execution in the database.
    - This addition allows for detailed tracking of task outcomes, improving the monitoring and analysis capabilities of
      background processes.

## [1.2.0] 23-09-2024

### Added

- **PATCH Method for Views**: Implemented a PATCH feature over views with its endpoint.
    - Users can now modify view fields one by one, offering greater flexibility compared to the previous PUT method,
      which required submitting all fields.

## [1.1.0] 09-06-2024

### Added

- **View Publishing Feature**: Introduced the ability to publish views.
    - Views can now be marked as published, making them visible to everyone, or kept unpublished, allowing only the
      owner to view and edit them.

## [1.0.0] 25-04-2024

### Added

- **Role-Based Access Control**: Implemented endpoint protection by activities, where users are assigned roles
  containing the activities they can perform.
    - This change enhances application security and user management, allowing for fine-grained access control.

## [0.3.0] 24-01-2024

### Fixed

- **Character Duplication Bug**: Resolved a bug that allowed the creation of characters with the same name due to
  capitalization differences.
    - Now, character names are normalized to ensure uniqueness regardless of letter casing, preventing duplication.

## [0.2.0] 29-11-2023

### Added

- **Character Existence Check**: Implemented a validation step to check for the existence of characters in the external
  API before allowing their creation in the system.
    - This ensures that only valid and existing characters are created, improving data integrity and reducing potential
      errors.

## [0.1.9] 22-11-2023

### Changed

- **Cache Data Task Optimization**: Updated the character caching background task to retrieve data from the external API
  concurrently instead of sequentially.
    - This enhancement significantly improves the speed and efficiency of the caching process, reducing overall latency
      and resource consumption.

## [0.1.8] 17-11-2023

### Added

- **Background Tasks**: Implemented two new background tasks to improve application performance and maintenance:
    - **Character Caching Task**: A scheduled task to cache character data, enhancing retrieval speed and reducing load
      on the database.
    - **Expired Token Cleanup Task**: A scheduled task to regularly clean up expired tokens, ensuring efficient use of
      storage and maintaining security.

## [0.1.7] 11-11-2023

### Added

- **Password Encryption**: Implemented encryption for user passwords before storing them in the repository.
    - Passwords are now hashed using a secure algorithm, enhancing security and protecting user data.

## [0.1.6] 11-11-2023

### Added

- **Refresh Token Feature**: Implemented a new refresh token mechanism to enhance user authentication.
    - The login response now delivers two tokens:
        - **Access Token**: Used for authenticating API requests.
        - **Refresh Token**: Allows users to obtain a new access token without re-entering credentials.

### Changed

- **Login Response**: Updated the login endpoint to return both the access token and the refresh token, improving
  session management and security.

## [0.1.5] 08-11-2023

### Added

- **Access Control System**: Implemented a new authentication system using access tokens and user credentials.

### Changed

- **Endpoint Authorization**: Updated existing API endpoints to require authentication via access tokens or credentials.

## [0.1.4] 07-11-2023

### Added

- **Edit Password Endpoint**: Introduced a new endpoint `PUT /api/credentials` for users to update their passwords.

## [0.1.3] 04-11-2023

### Added

- **Delete View Feature**: Implemented the ability to delete views.
    - Users can now remove views they no longer need, enhancing view management capabilities.

## [0.1.2] 04-11-2023

### Added

- **Expired Token Prevention**: Implemented logic to prevent the use of expired tokens.
    - API endpoints now validate token expiration before processing requests, ensuring enhanced security and user
      experience.
- **Persistent Tokens**: Introduced persistent tokens that never expire.
    - These tokens allow users to maintain long-term sessions without needing to re-authenticate frequently.

## [0.1.1] 04-11-2023

### Added

- **Name Field for Views**: Introduced an extra field, **name**, for views.
    - This field is now required when creating and editing views, enhancing the identification and management of views.

## [0.1.0] 02-11-2023

### Fixed

- **View Editing Issue**: Resolved a problem that prevented users from editing views correctly.
    - The fix ensures that all necessary data is correctly loaded and updated during the edit process, enhancing user
      experience and functionality.

## [0.0.4] 01-11-2023

### Added

- **Dockerfile**: Created a Dockerfile to simplify the deployment process.
    - This Dockerfile allows for easy containerization of the application, ensuring consistent environments across
      different deployment targets.

## [0.0.3] 14-09-2023

### Added

- **Database Configuration**: Introduced configuration settings for connecting to the database, including connection
  strings and credentials.

### Changed

- **Repository Upgrade**: Transitioned the repository from a volatile in-memory storage to a real database.
    - This change enhances data persistence, reliability, and scalability.

## [0.0.2] 26-07-2024

### Added

- **Maximum Views Limit**: Set the maximum number of views that a user can create to **2**.
    - This limit is enforced to optimize resource usage and ensure fair access for all users.

## [0.0.1] 26-07-2024

### Added

- **Code Coverage Enforcement**: Implemented code coverage checks for the project.
    - Pull requests will now fail if tests do not cover at least **75%** of the code, ensuring better testing practices
      and higher code quality.

### Changed

- **Continuous Integration Configuration**: Updated CI pipeline to include code coverage metrics as part of the testing
  process.

## [0.0.0] 25-07-2023

### Added

- Initial release with core features.