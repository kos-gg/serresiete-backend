Feature: WOW Sync

  Background:
    Given "sanxei" exists in the database with role "admin"
    And "sanxei" has a valid token with activity "search entity"
    And a current WOW season exists in the database

  Scenario: Searching for an unknown WOW entity and syncing it caches the data
    When they search for a "WOW" entity "Sanxei" on realm "Silvermoon" region "eu"
    Then the response status is 200
    And the response data is null
    And the response contains an operation
    When the WOW sync subscription processes pending events
    And they search for a "WOW" entity "Sanxei" on realm "Silvermoon" region "eu"
    Then the response status is 200
    And the response contains entity data
    And the response data is a valid WOW entity
    And the operation is null
