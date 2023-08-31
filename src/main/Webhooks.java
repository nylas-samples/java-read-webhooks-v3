// Import Nylas packages
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nylas.NylasClient;

// Import Spark and Mustache libraries
import spark.ModelAndView;
import static spark.Spark.*;
import spark.template.mustache.MustacheTemplateEngine;

//Import DotEnv to handle .env files
import io.github.cdimascio.dotenv.Dotenv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Webhooks {
    public static void main(String[] args) {
        ArrayList<Webhook_Info> array = new ArrayList<Webhook_Info>();

        // Load the .env file
        Dotenv dotenv = Dotenv.load();
        // Initialize the Nylas client
        NylasClient nylas = new NylasClient.Builder(dotenv.get("V3_TOKEN")).apiUri(dotenv.get("BASE_URL")).build();

        // Default path when we load our web application
        get("/", (request, response) -> {
            // Create a model to pass information to the mustache template
            Map<String, Object> model = new HashMap<>();
            model.put("webhooks", array);
            // Call the mustache template
            return new ModelAndView(model, "show_webhooks.mustache");
        }, new MustacheTemplateEngine());

        get("/webhook", (request, response) ->
                request.queryParams("challenge"));

        post("/webhook", (request, response) -> {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode incoming_webhook = mapper.readValue(request.body(), JsonNode.class);
            System.out.println(incoming_webhook);
            Map<String, Object> model = new HashMap<>();
            model.put("webhooks", array);
            return new ModelAndView(model, "show_emails.mustache");
        }, new MustacheTemplateEngine());
    }
}
