Feature: Tasks

  Background:
    Given "sanxei" has a valid token with activities "run task, get task"

  @happy
  Scenario: TOKEN_CLEANUP_TASK task removes expired tokens
    And an expired token exists for "olduser" in the database
    When they run the "tokenCleanupTask" task
    Then the task completes with status "SUCCESSFUL"
    And the expired token for "olduser" has been removed

  Scenario: TASK_CLEANUP_TASK removes old task records
    And an old task record exists in the database
    When they run the "taskCleanupTask" task
    Then the task completes with status "SUCCESSFUL"
    And the old task record has been removed

  Scenario: CACHE_CLEAR_TASK clears the data cache
    And a "WOW" entity "Sanxei" on realm "Silvermoon" region "eu" exists with cached data "wowHcEntity"
    When they run the "cacheClearTask" task
    Then the task completes with status "SUCCESSFUL"
    And the data cache is empty

  Scenario: CACHE_LOL_DATA_TASK caches LOL entity data
    And a LOL entity "GTP ZeroMVPs" with tag "EUW" exists in the database
    When they run the "cacheLolDataTask" task
    Then the task completes with status "SUCCESSFUL"
    And the LOL data cache contains an entry for "GTP ZeroMVPs" "EUW"

  Scenario: CACHE_WOW_DATA_TASK caches WOW entity data
    And a current WOW season exists in the database
    And a "WOW" entity "Sanxei" on realm "Silvermoon" region "eu" exists in the database
    When they run the "cacheWowDataTask" task
    Then the task completes with status "SUCCESSFUL"
    And the data cache contains a "WOW" entry for "Sanxei" "Silvermoon" "eu"

  Scenario: CACHE_WOW_HC_DATA_TASK caches WOW HC entity data
    And a "WOW_HC" entity "Sanxei" on realm "Silvermoon" region "eu" exists in the database
    When they run the "cacheWowHcDataTask" task
    Then the task completes with status "SUCCESSFUL"
    And the data cache contains a "WOW_HC" entry for "Sanxei" "Silvermoon" "eu"

  Scenario: UPDATE_LOL_ENTITIES_TASK updates LOL entity names
    And a LOL entity "GTP ZeroMVPs" with tag "EUW" exists in the database
    When they run the "updateLolEntitiesTask" task
    Then the task completes with status "SUCCESSFUL"

  Scenario: UPDATE_WOW_HARDCORE_GUILDS task updates guild members
    And a WOW_HC guild "SilvermoonBrotherhood" on realm "Silvermoon" region "eu" is associated with view "test-view-id"
    When they run the "updateWowHardcoreGuilds" task
    Then the task completes with status "SUCCESSFUL"
    And WOW_HC entities exist for the guild members

  Scenario: UPDATE_MYTHIC_PLUS_SEASON task updates mythic plus dungeons from current expansion
    And a current WOW season exists in the database
    When they run the "updateMythicPlusSeason" task
    Then the task completes with status "SUCCESSFUL"

  Scenario: CACHE_GAME_VIEW_DATA_TASK caches entities for a specific view
    And a LOL view "test-lol-view" exists
    When they run the "cacheGameViewDataTask" task with viewId "test-lol-view"
    Then the task completes with status "SUCCESSFUL"

  @sad
  Scenario: UPDATE_MYTHIC_PLUS_SEASON task fails when no WOW expansion exists
    When they run the "updateMythicPlusSeason" task
    Then the task completes with status "ERROR"

  Scenario: CACHE_GAME_VIEW_DATA_TASK fails when no viewId is provided
    When they run the "cacheGameViewDataTask" task
    Then the task completes with status "ERROR"

  Scenario: CACHE_GAME_VIEW_DATA_TASK fails when view does not exist
    When they run the "cacheGameViewDataTask" task with viewId "non-existent-view"
    Then the task completes with status "ERROR"

  Scenario: CACHE_GAME_VIEW_DATA_TASK fails with retryAfter when view was synced recently
    And a LOL view "test-lol-view" was recently synced
    When they run the "cacheGameViewDataTask" task with viewId "test-lol-view"
    Then the task completes with status "ERROR" and a retryAfter timestamp