package server;

import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ClientHandler.
 *
 * These tests spin up a real loopback ServerSocket/Socket pair (instead of mocks)
 * because ClientHandler talks directly to a java.net.Socket. Each TestClient
 * represents one connected "client" and wraps the raw streams a real ChatClient
 * would use.
 *
 * NOTE: These tests exercise ClientHandler in isolation, bypassing ChatServer's
 * "SERVER_AVAILABLE"/"SERVER_FULL" handshake -- that line is written by ChatServer,
 * not ClientHandler, so the first line ClientHandler ever reads is the username.
 */
class ClientHandlerTest {

    private ServerSocket serverSocket;
    private CopyOnWriteArrayList<ClientHandler> handlers;
    private ExecutorService executor;
    private List<Socket> openSockets;

    @BeforeEach
    void setUp() throws IOException {
        serverSocket = new ServerSocket(0); // bind to a free port
        handlers = new CopyOnWriteArrayList<>();
        executor = Executors.newCachedThreadPool();
        openSockets = new ArrayList<>();
    }

    @AfterEach
    void tearDown() throws IOException {
        for (Socket s : openSockets) {
            try {
                if (!s.isClosed()) s.close();
            } catch (IOException ignored) {
            }
        }
        executor.shutdownNow();
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    /**
     * Represents one connected test client: the client-side socket/streams,
     * plus the ClientHandler running on a background thread for the server side.
     */
    private class TestClient {
        final Socket socket;
        final PrintWriter out;
        final BufferedReader in;
        final ClientHandler handler;

        TestClient() throws IOException {
            socket = new Socket("localhost", serverSocket.getLocalPort());
            socket.setSoTimeout(2000); // don't let a broken test hang forever
            openSockets.add(socket);

            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            Socket serverSide = serverSocket.accept();
            handler = new ClientHandler(serverSide, handlers);
            handlers.add(handler); // mirrors ChatServer: added before the thread starts
            executor.submit(handler);
        }

        String sendUsernameAndGetResponse(String username) throws IOException {
            out.println(username);
            return in.readLine();
        }

        void registerValidUsername(String username) throws IOException {
            String resp = sendUsernameAndGetResponse(username);
            assertEquals("Welcome to the server", resp);
        }

        /** True if no message arrives within a short window. */
        boolean hasNoMoreMessages() {
            try {
                socket.setSoTimeout(300);
                String line = in.readLine();
                return line == null;
            } catch (SocketTimeoutException e) {
                return true;
            } catch (IOException e) {
                return true;
            } finally {
                try {
                    socket.setSoTimeout(2000);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private TestClient newRegisteredClient(String username) throws IOException {
        TestClient c = new TestClient();
        c.registerValidUsername(username);
        return c;
    }

    // ------------------------------------------------------------------
    // Username
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void blankUsernameIsRejected() throws IOException {
        TestClient c = new TestClient();
        assertEquals("Invalid username", c.sendUsernameAndGetResponse(""));
    }

    @Test
    @Timeout(5)
    void spacesOnlyUsernameIsRejected() throws IOException {
        TestClient c = new TestClient();
        assertEquals("Invalid username", c.sendUsernameAndGetResponse("   "));
    }

    @Test
    @Timeout(5)
    void duplicateUsernameIsRejected() throws IOException {
        newRegisteredClient("Alice");

        TestClient second = new TestClient();
        assertEquals("Username already taken", second.sendUsernameAndGetResponse("Alice"));

        // send a valid username afterward so the handler thread completes cleanly
        second.registerValidUsername("Alice2");
    }

    @Test
    @Timeout(5)
    void validUniqueUsernameIsAccepted() throws IOException {
        TestClient c = new TestClient();
        assertEquals("Welcome to the server", c.sendUsernameAndGetResponse("Charlie"));
    }

    // ------------------------------------------------------------------
    // Message
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void emptyMessageIsIgnored() throws IOException {
        TestClient c = newRegisteredClient("Dana");

        c.out.println("");      // blank -> should be silently ignored
        c.out.println("hello"); // real message should still arrive next

        String received = c.in.readLine();
        assertNotNull(received);
        assertTrue(received.contains("Dana: hello"));
    }

    @Test
    @Timeout(5)
    void messageOver500CharsIsRejected() throws IOException {
        TestClient c = newRegisteredClient("Eve");
        c.out.println("a".repeat(1000));

        assertEquals("Message cannot be over 500 characters", c.in.readLine());
    }

    @Test
    @Timeout(5)
    void exactly500CharsIsAccepted() throws IOException {
        TestClient c = newRegisteredClient("Frank");
        String msg500 = "b".repeat(500);
        c.out.println(msg500);

        String response = c.in.readLine();
        assertNotNull(response);
        assertTrue(response.contains("Frank: " + msg500));
    }

    @Test
    @Timeout(5)
    void exactly501CharsIsRejectedBoundary() throws IOException {
        TestClient c = newRegisteredClient("Gina");
        c.out.println("c".repeat(501));

        assertEquals("Message cannot be over 500 characters", c.in.readLine());
    }

    @Test
    @Timeout(5)
    void validMessageSendsCorrectly() throws IOException {
        TestClient c = newRegisteredClient("Hank");
        c.out.println("hi there");

        String response = c.in.readLine();
        assertNotNull(response);
        assertTrue(response.contains("Hank: hi there"));
    }

    @Test
    @Timeout(5)
    void allClientsIncludingSenderReceiveMessage() throws IOException {
        TestClient alice = newRegisteredClient("Alice");
        TestClient bob = newRegisteredClient("Bob");

        // Alice sees Bob's join notice first
        assertTrue(alice.in.readLine().contains("Bob joined the chat"));

        alice.out.println("hello everyone");

        String senderCopy = alice.in.readLine();
        String receiverCopy = bob.in.readLine();

        assertNotNull(senderCopy);
        assertNotNull(receiverCopy);
        assertTrue(senderCopy.contains("Alice: hello everyone"));
        assertTrue(receiverCopy.contains("Alice: hello everyone"));
    }

    @Test
    @Timeout(5)
    void messageContainsTimestampUsernameAndText() throws IOException {
        TestClient c = newRegisteredClient("Ivan");
        c.out.println("test message");

        String response = c.in.readLine();
        assertNotNull(response);
        // format written by ClientHandler: "[hh:mm a] username: message"
        // AM/PM marker left loose (\S+) since it's locale-dependent
        assertTrue(response.matches("^\\[\\d{2}:\\d{2} \\S+] Ivan: test message$"),
                "Expected [hh:mm a] Ivan: test message, got: " + response);
    }

    // ------------------------------------------------------------------
    // Join / Leave
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void otherClientsReceiveJoinNotification() throws IOException {
        TestClient alice = newRegisteredClient("Alice");
        newRegisteredClient("Bob");

        String joinMsg = alice.in.readLine();
        assertNotNull(joinMsg);
        assertTrue(joinMsg.contains("Bob joined the chat"));
    }

    @Test
    @Timeout(5)
    void joiningClientDoesNotReceiveOwnJoinNotification() throws IOException {
        newRegisteredClient("Alice");
        TestClient bob = newRegisteredClient("Bob");

        // nothing else should be waiting for Bob right after his own welcome message
        assertTrue(bob.hasNoMoreMessages(),
                "Joining client should not receive a notification about its own join");
    }

    @Test
    @Timeout(5)
    void otherClientsReceiveLeaveNotification() throws IOException {
        TestClient alice = newRegisteredClient("Alice");
        TestClient bob = newRegisteredClient("Bob");

        assertTrue(alice.in.readLine().contains("Bob joined the chat"));

        bob.socket.close();

        String leaveMsg = alice.in.readLine();
        assertNotNull(leaveMsg);
        assertTrue(leaveMsg.contains("Bob left the chat"));
    }

    @Test
    @Timeout(5)
    void handlerIsRemovedFromListOnLeave() throws IOException, InterruptedException {
        newRegisteredClient("Alice");
        TestClient bob = newRegisteredClient("Bob");

        assertTrue(handlers.contains(bob.handler));

        bob.socket.close();

        // removal happens on the handler's background thread after it detects
        // disconnect, so poll briefly instead of asserting immediately
        waitUntilRemoved(bob.handler, 2000);
        assertFalse(handlers.contains(bob.handler));
    }

    // ------------------------------------------------------------------
    // Disconnect / Edge Cases
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void abruptDisconnectCleansUpCorrectly() throws IOException, InterruptedException {
        TestClient alice = newRegisteredClient("Alice");
        TestClient bob = newRegisteredClient("Bob");
        alice.in.readLine(); // consume Bob's join notification

        // no /quit -- just yank the connection
        bob.socket.close();

        waitUntilRemoved(bob.handler, 2000);
        assertFalse(handlers.contains(bob.handler), "Handler should be removed after abrupt disconnect");

        String leaveMsg = alice.in.readLine();
        assertNotNull(leaveMsg);
        assertTrue(leaveMsg.contains("Bob left the chat"));
    }

    @Test
    @Timeout(5)
    void disconnectDuringUsernameSetupIsHandledSafely() throws IOException, InterruptedException {
        TestClient c = new TestClient();

        // disconnect before ever sending a username
        c.socket.close();

        // KNOWN ISSUE: ClientHandler's username-validation loop re-reads
        // br.readLine() whenever the username is invalid. Once the peer has
        // disconnected, a closed/EOF stream keeps returning null forever rather
        // than throwing, so isUsernameValid(null) stays false and the loop can
        // spin indefinitely instead of exiting and cleaning up the handler.
        // This assertion documents the *expected* safe behavior. If it fails
        // (or this test times out), it is flagging that real bug rather than a
        // flaky test.
        waitUntilRemoved(c.handler, 2000);
        assertFalse(handlers.contains(c.handler),
                "Handler should be cleaned up if the client disconnects mid-username-setup");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void waitUntilRemoved(ClientHandler handler, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (handlers.contains(handler) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
    }
}