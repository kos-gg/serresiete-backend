Feature: Authentication

  @happy
  Scenario: Authenticated user can access protected endpoints
    Given "sanxei" has a valid token with activities "get any activities"
    When they request GET "/api/activities"
    Then the response status is 200

  @sad
  Scenario: Unauthenticated request is rejected
    Given the request has no authentication
    When they request GET "/api/activities"
    Then the response status is 401

  Scenario: Expired token is rejected
    Given "sanxei" has an expired token
    When they request GET "/api/activities"
    Then the response status is 401

  Scenario: Refresh token cannot be used to access protected endpoints
    Given "sanxei" has a refresh token
    When they request GET "/api/activities"
    Then the response status is 401

  @happy
  Scenario: Login sets an httpOnly refreshToken cookie
    Given "sanxei" exists in the database with role "ADMIN"
    When they login as "sanxei" with password "test-password"
    Then the response status is 200
    And the response has an httpOnly refreshToken cookie

  @happy
  Scenario: Refresh using the cookie returns a new access token
    Given "sanxei" exists in the database with role "ADMIN"
    And they have logged in as "sanxei" with password "test-password"
    When they refresh using the cookie
    Then the response status is 200
    And the response body contains an access token

  @happy
  Scenario: Refresh using the Bearer header returns a new access token
    Given "sanxei" exists in the database with role "ADMIN"
    And they have logged in as "sanxei" with password "test-password"
    When they refresh using the Bearer header
    Then the response status is 200
    And the response body contains an access token

  @happy
  Scenario: Logout clears the refreshToken cookie
    Given "sanxei" exists in the database with role "ADMIN"
    And they have logged in as "sanxei" with password "test-password"
    And "sanxei" has a valid token with activities "logout"
    When they logout
    Then the response status is 200
    And the response clears the refreshToken cookie

  @sad
  Scenario: Refresh without credentials returns 401
    When they refresh without credentials
    Then the response status is 401
