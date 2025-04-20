package helloworld;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.demoiselle.signer.policy.impl.cades.pkcs7.PKCS7Signer;
import org.demoiselle.signer.policy.impl.cades.factory.PKCS7Factory;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");

        // Print the body of the request to logs (if the body is available)
        String signatureBase64 = "";
        try {
            if (input.getBody() != null) {
                String body = input.getBody();
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, String> bodyMap = objectMapper.readValue(body, Map.class);
                //ler file e certificate
                //password (criptografada e descriptografar 
                //na função então definir alguma variável de ambiente)
                String myEnvVar = System.getenv("MY_VAR");
                String certificate = bodyMap.get("certificate");
                String fileBase64 = bodyMap.get("file1");
                String secret = bodyMap.get("secret");
                byte[] certBytes = Base64.getDecoder().decode(certificate);

                KeyStore keystore = KeyStore.getInstance("PKCS12");
                try (ByteArrayInputStream certInputStream = new ByteArrayInputStream(certBytes)) {
                    keystore.load(certInputStream, secret.toCharArray());
                }catch(Exception e){
                    System.out.println("Passo 1 falhou "+e);

                }
                String alias = keystore.aliases().nextElement();
                PrivateKey privateKey = (PrivateKey) keystore.getKey(alias, secret.toCharArray());
                X509Certificate certificateTemp = (X509Certificate) keystore.getCertificate(alias);
                byte[] contentFile = Base64.getDecoder().decode(fileBase64);
                PKCS7Signer signer = PKCS7Factory.getInstance().factoryDefault();
                Certificate[] certificates = new Certificate[]{certificateTemp};
                signer.setCertificates(certificates);
                signer.setPrivateKey(privateKey);
                byte[] signature = signer.doDetachedSign(contentFile);
                signatureBase64 = Base64.getEncoder().encodeToString(signature);
      
            } else {
                System.out.println("Request body is empty or null");
            }
        } catch (Exception e) {
            System.err.println("Error loading keystore: " + e.getMessage());
            e.printStackTrace();

            // Detailed error hint
            if (e.getMessage().contains("keystore password was incorrect")) {
                System.err.println("Hint: The provided keystore password may be incorrect.");
            } else if (e.getMessage().contains("keystore tampered with")) {
                System.err.println("Hint: The certificate may not be a valid PKCS12 keystore.");
            }
            System.err.println("Error reading request body: " + e.getMessage());
        }

        // Create response object
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);

        try {
            // Fetch external content (IP address)
            final String pageContents = this.getPageContents("https://checkip.amazonaws.com");

            // Prepare the response body
            String output = String.format("{ \"message\": \"%s\", \"signature\":\"%s\"}", pageContents, signatureBase64);

            // Return a successful response with the location data
            return response
                    .withStatusCode(200)
                    .withBody(output);

        } catch (IOException e) {
            // Handle IO exception (e.g., error fetching the page contents)
            System.err.println("Error fetching page contents: " + e.getMessage());
            return response
                    .withBody("{\"error\": \"Failed to fetch page contents\"}")
                    .withStatusCode(500);
        }
    }

    private String getPageContents(String address) throws IOException {
        URL url = new URL(address);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()))) {
            return br.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }
}
