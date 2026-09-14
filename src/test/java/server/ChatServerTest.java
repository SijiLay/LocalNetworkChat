package server;

import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit/integration tests for ChatServer.
 *
 * ChatServer is a `public static void main` with several hardcoded values
 * (port 5000, MAX_CLIENTS = 2) and no dependency injection, so it can't be
 * tested through mocks the way a normal class could. Instead each test:
 *   1. Redirects System.in to a pipe we control (so we can send "/stop"
 *      to ServerConsole, which reads System.in directly).
 *   2. Runs ChatServer.main() on a background thread.
 *   3. Talks to it over real sockets on localhost:5000, exactly like a
 *      real ChatClient would.
 *
 * Because the port is hardcoded, tests run sequentially and each one fully
 * stops the server in @AfterEach so the next test can rebind port 5000.
 */
class ChatServerTest {

    private static final int PORT = 5000;
    private static final int MAX_CLIENTS = ChatServer.MAX_CLIENTS;

    private InputStream originalSystemIn;
    private PrintStream originalSystemOut;
    private PipedOutputStream consoleIn;
    private Thread serverThread;
    private List<Socket> openSockets;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        originalSystemIn = System.in;
        originalSystemOut = System.out;
        openSockets = new ArrayList<>();

        consoleIn = new PipedOutputStream();
        PipedInputStream pipedSystemIn = new PipedInputStream(consoleIn);
        System.setIn(pipedSystemIn);

        serverThread = new Thread(() -> ChatServer.main(new String[0]), "chat-server-test-thread");
        serverThread.setDaemon(true);
        serverThread.start();

        awaitPortOpen(PORT, 3000);
    }

    @AfterEach
    void tearDown() throws IOException, InterruptedException {
        try {
            stopServerAndWaitForExit(3000);
        } finally {
            for (Socket s : openSockets) {
                try {
                    if (!s.isClosed()) s.close();
                } catch (IOException ignored) {
                }
            }
            System.setIn(originalSystemIn);
            System.setOut(originalSystemOut);
        }
    }

    // ------------------------------------------------------------------
    // Server startup / accepting
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void serverOpensServerSocket() throws IOException {
        // setUp() already waited for the port to accept connections; a
        // successful connect here is direct proof the ServerSocket is bound.
        try (Socket probe = new Socket("localhost", PORT)) {
            assertTrue(probe.isConnected());
        }
    }

    @Test
    @Timeout(5)
    void serverAcceptsIncomingClient() throws IOException {
        Connection c = connect();
        assertEquals("SERVER_AVAILABLE", c.status);
    }

    @Test
    @Timeout(5)
    void acceptGivesAFullyConnectedClientSocket() throws IOException {
        Connection c = connect();
        assertEquals("SERVER_AVAILABLE", c.status);

        // prove it's a genuine two-way connection wired to a working
        // ClientHandler, not just a one-shot greeting on a dead socket
        c.out.println("Nina");
        assertEquals("Welcome to the server", c.in.readLine());
    }

    @Test
    @Timeout(5)
    void serverContinuesAcceptingAfterFirstClient() throws IOException {
        Connection first = connect();
        assertEquals("SERVER_AVAILABLE", first.status);

        Connection second = connect();
        assertEquals("SERVER_AVAILABLE", second.status);
    }

    // ------------------------------------------------------------------
    // Capacity handling
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void clientGetsServerAvailableWhenThereIsRoom() throws IOException {
        Connection c = connect();
        assertEquals("SERVER_AVAILABLE", c.status);
    }

    @Test
    @Timeout(5)
    void clientGetsServerFullWhenMaxClientsReached() throws IOException {
        fillCapacity();

        Connection third = connect(); // MAX_CLIENTS is 2
        assertEquals("SERVER_FULL", third.status);
    }

    @Test
    @Timeout(5)
    void fullClientIsRejectedInsteadOfGettingAHandler() throws IOException {
        fillCapacity();

        Connection rejected = connect();
        assertEquals("SERVER_FULL", rejected.status);

        // server closes rejected sockets immediately -- no ClientHandler is
        // on the other end, so the next read should hit EOF
        assertNull(rejected.in.readLine(), "Rejected client's socket should be closed by the server");
    }

    @Test
    @Timeout(8)
    void afterConnectedClientLeavesNewClientCanTakeFreedSlot() throws Exception {
        List<Connection> full = fillCapacity();

        Connection rejected = connect();
        assertEquals("SERVER_FULL", rejected.status);

        // free a slot
        full.get(0).socket.close();

        // ClientHandler cleanup runs asynchronously on its own thread once it
        // detects the disconnect, so poll until a slot actually frees up
        assertTrue(awaitServerAvailable(3000), "A freed slot should eventually accept a new client");
    }

    // ------------------------------------------------------------------
    // ClientHandler setup
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void acceptedClientGetsAClientHandler() throws IOException {
        Connection c = connect();
        c.out.println("Oscar");
        assertEquals("Welcome to the server", c.in.readLine());

        // only a running ClientHandler formats and echoes chat messages back
        c.out.println("hello world");
        String echoed = c.in.readLine();
        assertNotNull(echoed);
        assertTrue(echoed.contains("Oscar: hello world"));
    }

    @Test
    @Timeout(5)
    void handlerIsAddedToClientHandlersAsSoonAsItIsAccepted() throws IOException {
        // connect two clients but never send a username for either
        Connection first = connect();
        Connection second = connect();
        assertEquals("SERVER_AVAILABLE", first.status);
        assertEquals("SERVER_AVAILABLE", second.status);

        // a third connection is already rejected, proving the first two were
        // added to clientHandlers right when they were accepted -- not only
        // after they finished registering a username
        Connection third = connect();
        assertEquals("SERVER_FULL", third.status);
    }

    @Test
    @Timeout(5)
    void handlerActuallyStartsRunningOnItsOwnThread() throws IOException {
        Connection c = connect();

        // if the handler's thread never started, it would never read the
        // username line and this would time out waiting for a response
        c.out.println("Priya");
        assertEquals("Welcome to the server", c.in.readLine());
    }

    @Test
    @Timeout(8)
    void rejectedClientIsNotAddedToClientHandlers() throws Exception {
        List<Connection> full = fillCapacity();

        Connection rejected = connect();
        assertEquals("SERVER_FULL", rejected.status);

        // free BOTH real slots
        for (Connection c : full) {
            c.socket.close();
        }

        // if the rejected client had been wrongly added to clientHandlers,
        // the list would still hold a "ghost" entry here and only one new
        // client (not two) would be able to get back in
        assertTrue(awaitServerAvailable(3000), "First freed slot should accept a new client");
        assertTrue(awaitServerAvailable(3000), "Second freed slot should accept a new client");
    }

    // ------------------------------------------------------------------
    // Shutdown / errors
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void closingServerStopsAccept() throws Exception {
        stopServerAndWaitForExit(3000);
        assertFalse(serverThread.isAlive(), "Server thread should have exited after shutdown");

        assertThrows(ConnectException.class, () -> {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("localhost", PORT), 500);
            }
        });
    }

    @Test
    @Timeout(5)
    void normalShutdownIsDistinguishedFromAnUnexpectedError() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            stopServerAndWaitForExit(3000);
        } finally {
            System.setOut(originalSystemOut);
        }

        String output = captured.toString();
        assertTrue(output.contains("Server stopped"), "Expected the clean-shutdown message, got: " + output);
        assertFalse(output.contains("Error:"), "A deliberate /stop should not be reported as an error");
    }

    @Test
    @Timeout(5)
    void serverShutdownWithZeroClientsExitsCleanly() throws Exception {
        // no clients connected at all
        stopServerAndWaitForExit(3000);
        assertFalse(serverThread.isAlive(), "Server should exit cleanly even with no connected clients");
    }

    @Test
    @Timeout(6)
    void serverShutdownClosesActiveClientConnectionsToo() throws Exception {
        Connection c = connect();
        c.out.println("Quinn");
        assertEquals("Welcome to the server", c.in.readLine());

        stopServerAndWaitForExit(3000);

        // the server should have closed this client's connection as part of shutdown
        assertNull(c.in.readLine(), "Client connection should be closed when the server shuts down");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static class Connection {
        Socket socket;
        PrintWriter out;
        BufferedReader in;
        String status; // first line sent by the server: SERVER_AVAILABLE / SERVER_FULL
    }

    private Connection connect() throws IOException {
        Socket socket = new Socket("localhost", PORT);
        socket.setSoTimeout(2000);
        openSockets.add(socket);

        Connection c = new Connection();
        c.socket = socket;
        c.out = new PrintWriter(socket.getOutputStream(), true);
        c.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        c.status = c.in.readLine();
        return c;
    }

    private List<Connection> fillCapacity() throws IOException {
        List<Connection> conns = new ArrayList<>();
        for (int i = 0; i < MAX_CLIENTS; i++) {
            Connection c = connect();
            assertEquals("SERVER_AVAILABLE", c.status, "Expected room while filling capacity");
            conns.add(c);
        }
        return conns;
    }

    /** Retries connecting until SERVER_AVAILABLE is seen, or the timeout elapses. */
    private boolean awaitServerAvailable(long timeoutMillis) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            Connection c = connect();
            if ("SERVER_AVAILABLE".equals(c.status)) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private void awaitPortOpen(int port, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try (Socket probe = new Socket("localhost", port)) {
                return; // connected -> port is open and accepting
            } catch (IOException e) {
                Thread.sleep(50);
            }
        }
        fail("Server did not start listening on port " + port + " in time");
    }

    private void stopServerAndWaitForExit(long timeoutMillis) throws IOException, InterruptedException {
        if (!serverThread.isAlive()) return;
        consoleIn.write(("/stop" + System.lineSeparator()).getBytes());
        consoleIn.flush();
        serverThread.join(timeoutMillis);
    }
}