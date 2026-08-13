Feature: Views

  Background:
    Given "sanxei" exists in the database with role "admin"
    And "sanxei" has a valid token with activities "create a view, get own views, get own view, edit own view, delete own view, get operation status"

  @happy
  Scenario Outline: User can create a view for each game
    When they create a "<game>" view
    And the views subscription processes pending events
    And the sync subscription processes pending events
    Then GET "/api/views" returns 1 view
    And a completed event is saved for the operation

    Examples:
      | game   |
      | LOL    |
      | WOW    |
      | WOW_HC |

  @happy
  Scenario: User can get their view by id
    When they create a "LOL" view
    And the views subscription processes pending events
    And the sync subscription processes pending events
    And they GET the created view
    Then the response status is 200
    And a completed event is saved for the operation

  @happy
  Scenario: User can poll the operation status endpoint until it completes
    When they create a "LOL" view
    And the views subscription processes pending events
    And the sync subscription processes pending events
    And they GET the operation status
    Then the response status is 200
    And the operation status is "COMPLETED"

  @happy
  Scenario: User can edit their view
    Given they have an existing "LOL" view
    When they edit the created view to be named "Updated View"
    And the views subscription processes pending events
    And the sync subscription processes pending events
    Then the response status is 200
    And a completed event is saved for the operation

  @happy
  Scenario: User can patch their view
    Given they have an existing "LOL" view
    When they patch the created view to be named "Patched View"
    And the views subscription processes pending events
    And the sync subscription processes pending events
    Then the response status is 200
    And a completed event is saved for the operation

  @happy
  Scenario: User can delete their view
    Given they have an existing "LOL" view
    When they DELETE the created view
    And the views subscription processes pending events
    Then the response status is 200
    And GET "/api/views" returns 0 views
    And a completed event is saved for the operation

  @sad
  Scenario: Deleting a non-existent view returns 404
    When they request DELETE "/api/views/non-existent-id"
    Then the response status is 404

  @sad
  Scenario: Unauthenticated request to create a view is rejected
    Given the request has no authentication
    When they create a "LOL" view
    Then the response status is 401

  @sad
  Scenario: User without permission to create a view gets 403
    Given "sanxei" has a valid token with activities "get own views"
    When they create a "LOL" view
    Then the response status is 403

  @sad
  Scenario: Editing a non-existent view returns 404
    When they edit a view at "/api/views/non-existent-id" to be named "Updated View"
    Then the response status is 404

  @sad
  Scenario: Getting a non-existent view returns 404
    When they request GET "/api/views/non-existent-id"
    Then the response status is 404
