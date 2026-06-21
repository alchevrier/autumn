import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class AuthTest {
    public static void main(String[] args) throws Exception {
        String user = System.getenv("USER");
        String pass = System.getenv("PASS");
        
        String authStr = user + ":" + pass;
        String base64Auth = Base64.getEncoder().encodeToString(authStr.getBytes());

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(new URI("https://central.sonatype.com/api/v1/publisher/upload"))
            .header("Authorization", "Bearer " + base64Auth)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Bearer token response: " + response.body());
    }
}
