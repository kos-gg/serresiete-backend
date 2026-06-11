Feature: Entities

  Background:
    Given "sanxei" exists in the database with role "admin"
    And "sanxei" has a valid token with activities "search entity"

  @happy
  Scenario: Searching for an unknown WOW entity queues it for sync
    When they search for a "WOW" entity "Sanxei" on realm "Silvermoon" region "eu"
    Then the response status is 200
    And the response data is null
    And the response contains an operation
    And a sync event exists for "WOW" entity "Sanxei" on realm "Silvermoon" region "eu"

  Scenario: Searching for a WOW entity with cached data returns the data
    Given a "WOW" entity "Sanxei" on realm "Silvermoon" region "eu" exists with cached data "wowEntity"
    When they search for a "WOW" entity "Sanxei" on realm "Silvermoon" region "eu"
    Then the response status is 200
    And the response contains entity data
    And the operation is null
