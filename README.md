# Local Network Chat App

A multithreaded Java console application that allows multiple users on the same local network to communicate through a central server using TCP sockets.

I built this project to strengthen my understanding of networking, client-server architecture, multithreading, shared state, and connection management in Java.

## Features

- Multiple clients can connect to one server
- Real-time message broadcasting
- Unique usernames
- Username validation
- Join and leave notifications
- Message timestamps
- Maximum message length of 500 characters
- Maximum client limit
- `/quit` command for clients
- `/stop` command for the server
- Graceful client and server disconnection
- Handles unexpected client disconnects
- Automated testing with JUnit

## Technologies Used

- Java
- TCP Sockets
- Java Threads
- Blocking I/O
- Maven
- JUnit 5
- Git / GitHub

## Architecture

The application follows a client-server architecture.

### Server

The server listens for incoming TCP connections and creates a separate `ClientHandler` for each connected client.

Each client handler runs on its own thread, allowing multiple users to communicate with the server concurrently.

The server maintains a shared collection of connected clients so messages can be broadcast to everyone in the chat.

### Client

The client connects to the server using a TCP socket.

After connecting, the client uses separate threads for sending and receiving messages:

- An input thread reads messages entered by the user and sends them to the server.
- A server listener receives and displays messages sent by the server.

This allows the client to receive messages while simultaneously waiting for keyboard input.

## How Communication Works

```text
Client 1 ──┐
           │
Client 2 ──┼──> Chat Server ──> Broadcast to connected clients
           │
Client 3 ──┘
```

The server acts as the central communication point. Clients do not communicate directly with each other.

When a client sends a message:

1. The client sends the message to the server through its socket.
2. The client's `ClientHandler` receives the message.
3. The server broadcasts the message to the connected clients.
4. Each client's listener receives and displays the message.

## Example

```text
Connected to server
Enter username: Siji

Welcome to the server

[04:32 PM] Alex joined the chat
[04:33 PM] Siji: Hey everyone
[04:33 PM] Alex: What's up?
[04:35 PM] Alex left the chat
```

## Commands

### Client

```text
/quit
```

Disconnects the client from the server.

### Server

```text
/stop
```

Shuts down the server and disconnects connected clients.

## Running the Project

### Requirements

- Java
- Maven

### 1. Start the server

Run:

```text
ChatServer
```

The server listens for incoming connections on port `5000`.

### 2. Start a client

Run:

```text
ChatClient
```

The client connects to the configured server host on port `5000`.

### 3. Connect additional clients

Run additional instances of `ChatClient` to simulate multiple users.

Each connected user must choose a unique valid username.

## Testing

The project includes automated tests covering the major client and server behaviors.

Testing includes:

- Client connections
- Server capacity
- Username validation
- Duplicate usernames
- Message broadcasting
- Message validation
- Client cleanup
- Unexpected disconnects
- Join and leave notifications
- Server shutdown
- Client shutdown

The completed V1 passed:

- **55 automated tests**
- **10 manual integration test scenarios**

Manual testing was also used to verify multiple clients communicating with the server simultaneously.

## Local Network Testing

The application was designed to support communication between devices on the same LAN.

Development and integration testing were successfully completed using localhost. Physical communication between two separate devices could not be fully verified on the university network because the network prevents direct communication between the tested devices.

Testing on an unrestricted private LAN remains the final physical network verification step.

## What I Learned

Building this project gave me hands-on experience with:

- TCP networking and sockets
- Client-server architecture
- Blocking I/O
- Multithreading and concurrency
- Managing shared state between threads
- Designing a simple communication protocol
- Handling client connection lifecycles
- Graceful shutdown and resource cleanup
- Debugging networking and concurrency issues
- Writing automated tests for networked applications
- Integration testing with multiple clients

One of the biggest challenges was handling client shutdown correctly while one thread could still be blocked waiting for keyboard input. I redesigned the client lifecycle so network listening and keyboard input run independently without relying on forcibly terminating the JVM.

## Project Structure

```text
LocalNetworkChat/
├── src/
│   ├── main/
│   │   └── java/
│   │       ├── client/
│   │       │   ├── ChatClient.java
│   │       │   └── ServerListener.java
│   │       │
│   │       └── server/
│   │           ├── ChatServer.java
│   │           ├── ClientHandler.java
│   │           └── ServerConsole.java
│   │
│   └── test/
│       └── java/
│           ├── client/
│           └── server/
│
├── pom.xml
├── .gitignore
└── README.md
```

## Status

**V1 Complete**

The core networking, concurrency, messaging, validation, connection management, shutdown behavior, and testing goals for the project have been completed.