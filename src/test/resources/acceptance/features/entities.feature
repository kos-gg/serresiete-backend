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

  @happy
  Scenario: Searching for a WOW entity with cached data returns the data
    Given a "WOW" entity "Sanxei" on realm "Silvermoon" region "eu" exists with cached data "wowEntity"
    When they search for a "WOW" entity "Sanxei" on realm "Silvermoon" region "eu"
    Then the response status is 200
    And the response data is a valid WOW entity
    And the operation is null

  @happy
  Scenario: Searching for an unknown LOL entity queues it for sync
    When they search for a "LOL" entity "GTP ZeroMVPs" with tag "EUW"
    Then the response status is 200
    And the response data is null
    And the response contains an operation

  @happy
  Scenario: Searching for a LOL entity with cached data returns the data
    Given a LOL entity "GTP ZeroMVPs" with tag "EUW" exists with cached data "lolEntity"
    When they search for a "LOL" entity "GTP ZeroMVPs" with tag "EUW"
    Then the response status is 200
    And the response data is a valid LOL entity
    And the operation is null
