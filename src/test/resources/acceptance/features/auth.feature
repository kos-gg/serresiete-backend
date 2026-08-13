Feature: Authentication

  @happy
  Scenario: Authenticated user can access protected endpoints
    Given "sanxei" has a valid token with activities "get any activities"
    When they request GET "/api/activities"
    Then the response status is 200

  @happy
  Scenario: User can login with valid credentials and access a protected endpoint
    Given "sanxei" is registered with password "test-password" and role "admin"
    And role "admin" has activities "get any activities"
    When they login with username "sanxei" and password "test-password"
    Then the response status is 200
    And the response contains an access token and a refresh token
    When they request GET "/api/activities"
    Then the response status is 200

  @happy
  Scenario: User can refresh their access token after logging in
    Given "sanxei" is registered with password "test-password" and role "admin"
    And they login with username "sanxei" and password "test-password"
    When they refresh their access token
    Then the response status is 200
    And the response contains a new access token

  @sad
  Scenario: Unauthenticated request is rejected
    Given the request has no authentication
    When they request GET "/api/activities"
    Then the response status is 401

  @sad
  Scenario: Expired token is rejected
    Given "sanxei" has an expired token
    When they request GET "/api/activities"
    Then the response status is 401

  @sad
  Scenario: Refresh token cannot be used to access protected endpoints
    Given "sanxei" has a refresh token
    When they request GET "/api/activities"
    Then the response status is 401

  @sad
  Scenario: Login with an incorrect password is rejected
    Given "sanxei" is registered with password "test-password" and role "admin"
    When they login with username "sanxei" and password "wrong-password"
    Then the response status is 401

  @sad
  Scenario: Login with an unknown username is rejected
    When they login with username "ghost" and password "whatever"
    Then the response status is 401
