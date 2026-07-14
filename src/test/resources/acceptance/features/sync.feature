Feature: Sync

  @happy
  Scenario: WOW sync caches entity data
    Given a "WOW" sync event is posted for "Sanxei" "Silvermoon" "eu"
    And a current WOW season exists in the database
    When the WOW sync subscription processes pending events
    Then the data cache contains a "WOW" entry for "Sanxei" "Silvermoon" "eu"

  Scenario: WOW sync records a failure when the raiderIo cutoff endpoint fails
    Given a "WOW" sync event is posted for "Sanxei" "Silvermoon" "eu"
    And a current WOW season exists in the database
    And the raiderIo cutoff API returns an error
    When the WOW sync subscription processes pending events
    Then the data cache contains a "WOW" entry for "Sanxei" "Silvermoon" "eu"

  Scenario: WOW Hardcore sync caches entity data
    Given a "WOW_HC" sync event is posted for "Sanxei" "Silvermoon" "eu"
    When the WOW HC sync subscription processes pending events
    Then the data cache contains a "WOW_HC" entry for "Sanxei" "Silvermoon" "eu"

  Scenario: WOW Hardcore sync marks a character as dead when Blizzard returns 404
    Given a "WOW_HC" entity "Sanxei" on realm "Silvermoon" region "eu" exists with cached data "wowHcEntity"
    And the Blizzard profile API returns 404
    And a "WOW_HC" sync event is posted for "Sanxei" "Silvermoon" "eu"
    When the WOW HC sync subscription processes pending events
    Then "Sanxei" "Silvermoon" "eu" is marked as dead in the WOW_HC data cache

  Scenario: WOW Hardcore sync skips a character that is already dead
    Given a "WOW_HC" entity "Sanxei" on realm "Silvermoon" region "eu" exists with cached data "wowHcDeadEntity"
    And a "WOW_HC" sync event is posted for "Sanxei" "Silvermoon" "eu"
    When the WOW HC sync subscription processes pending events
    Then the WOW_HC data cache for "Sanxei" "Silvermoon" "eu" has not been updated

  Scenario: LOL sync caches entity data
    Given a LOL sync event is posted for "GTP ZeroMVPs" "EUW"
    When the LOL sync subscription processes pending events
    Then the LOL data cache contains an entry for "GTP ZeroMVPs" "EUW"