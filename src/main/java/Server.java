import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


public class Server implements Runnable, AutoCloseable {
    private final Socket connect;

    public Server(Socket c) {
        connect = c;
    }
    public static void main(String[] args) throws Exception {

        ServerSocket serverSocket = new ServerSocket(8080);
        System.out.println("Listening for connection on port 8080 ....");
        while (true) {
            try (Server server = new Server(serverSocket.accept())) {
                java.lang.Thread thread = new java.lang.Thread(server);
                thread.start();
                thread.join();
            }
        }
    }

    @Override
    public void run() {
        Gmail service = getGmailService("email-tagging");
        Multimap<Message, IssueType> badMessagesWithIssueTypes = HashMultimap.create();
        String userId = "me";
        try {
            getGmailMessages(service,
                    userId,
                    getAttachmentsQuery("/google_attachment_queries.txt")
            )
                    .forEach(message -> badMessagesWithIssueTypes.put(message, IssueType.GOOGLE_DRIVE_LINK_ATTACHED));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            getGmailMessages(service,
                    userId,
                    getAttachmentsQuery("/file_sharing_link_matches.txt")
            )
                    .forEach(message -> badMessagesWithIssueTypes.put(message, IssueType.POTENTIAL_FILE_SHARING_LINK_INLINE));
        } catch (IOException e) {
            e.printStackTrace();
        }
        String messagesOutput = getMessagesOutput(badMessagesWithIssueTypes);
        String httpResponse = "HTTP/1.1 200 OK\r\n\r\n" + messagesOutput;
        try {
            connect.getOutputStream().write(httpResponse.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getMessagesOutput(Multimap<Message, IssueType> messagesWithIssueTypes) {
        Set<Message> messages = messagesWithIssueTypes.keySet();
        if (messages.isEmpty()) {
            return "No messages found.";
        } else {
            StringBuilder sb = new StringBuilder("Messages:");
            sb.append("\nTotal Bad Message Count: ");
            sb.append(messages.size());
            sb.append("\nBad Message Count by Type: ");
            Map<IssueType, Long> issueTypesWithCounts = messagesWithIssueTypes.values().stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            issueTypesWithCounts.forEach((issueType, count) -> {
                sb.append("\n");
                sb.append(issueType);
                sb.append(": ");
                sb.append(count);
            });
            sb.append("\nNote that a message may have more than one bad message type, " +
                    "so the sum of these counts may total more than the total count");
            for (Message message : messages) {
                sb.append("\n- ");
                sb.append(message.getId());
                sb.append(": ");
                sb.append(messagesWithIssueTypes.get(message));
            }
            return sb.toString();
        }
    }

    private List<Message> getGmailMessages(Gmail service, String user, String query) {
        List<Message> messages = new ArrayList<>();
        // Initial null gets first page
        String pageToken = null;
        try {
            do {
                ListMessagesResponse messagesResponse = service.users()
                        .messages()
                        .list(user)
                        .setIncludeSpamTrash(true)
                        .setQ(query)
                        .setPageToken(pageToken)
                        .execute();

                messages.addAll(messagesResponse.getMessages());
                pageToken = messagesResponse.getNextPageToken();
            } while (pageToken != null);
            return messages;
        } catch (IOException e) {
            e.printStackTrace();
            return messages;
        }
    }

    private String getAttachmentsQuery(String googleAttachementQueriesFilePath) throws IOException {
        URL fileUrl = Server.class.getResource(googleAttachementQueriesFilePath);
        if (fileUrl == null) {
            throw new FileNotFoundException("Resource not found: " + googleAttachementQueriesFilePath);
        }
        return String.join(" OR ", Files.readAllLines(new File(fileUrl.getFile()).toPath()));
    }

    private Gmail getGmailService(String applicationName) {
        try {
            final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            return new Gmail.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                    .setApplicationName(applicationName)
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to create Gmail Service", e);
        }
    }

    @Override
    public void close() throws Exception {
        connect.close();
    }

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    /**
     * Creates an authorized Credential object.
     * @param httpTransport The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport httpTransport) throws IOException {
        // Load client secrets.
        InputStream in = Server.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("me");
    }

    enum IssueType {
        GOOGLE_DRIVE_LINK_ATTACHED,
        POTENTIAL_FILE_SHARING_LINK_INLINE,
        ;
    }

}
