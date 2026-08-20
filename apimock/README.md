# Mock Server for API

This mock server is built with Express to simulate backend responses, allowing frontend developers to test the API
without connecting to the real server. The server runs on the same port as the production server (port 8080), so ensure
the real server is not running simultaneously when using this mock.

## Table of Contents

- [Installation](#installation)
- [Running the Server](#running-the-server)
- [Available Endpoints](#available-endpoints)

---

## Installation

1. Clone this repository or download the mock server code.
2. Open a terminal in the `apimock` directory.
3. Initialize the project dependencies:

```bash
npm install
 ```

## Running the Server

To start the mock server, use the following command:

```bash
npm start
```

## Available Endpoints

### 1. `GET /api/views/:id/data`

- **Description**: Returns data for a specific view.
- **Path Parameter**:
    - `id` (string): The ID of the view.
- **Response**: Sends the contents of `resources/data.json`.
- **Example Request**:
   ```bash
   curl http://localhost:8080/api/views/123/data
   ```

### 2. `GET /api/views/:id/cached-data`

- **Description**: Returns cached data for a specific view.
- **Path Parameter**:
    - `id` (string): The ID of the view.
- **Response**: Sends the contents of `resources/cached-data.json`.
- **Example Request**:
   ```bash
   curl http://localhost:8080/api/views/123/cached-data
   ```

### 3. `GET /api/views`

- **Description**: Returns a list of available views.
- **Response**: Sends the contents of `resources/views.json`.
- **Example Request**:
   ```bash
   curl http://localhost:8080/api/views
   ```