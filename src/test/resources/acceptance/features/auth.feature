Feature: Authentication

  @happy
  Scenario: Authenticated user can access protected endpoints
    Given "sanxei" has a valid token with activity "get any activities"
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
