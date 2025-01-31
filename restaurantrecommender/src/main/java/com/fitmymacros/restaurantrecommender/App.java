package com.fitmymacros.restaurantrecommender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpStatusCode;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmymacros.restaurantrecommender.model.ChatCompletionResponse;
import com.fitmymacros.restaurantrecommender.model.ChatCompletionResponseChoice;

import reactor.core.publisher.Mono;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;

public class App implements RequestHandler<Map<String, Object>, Object> {

    private static final String OPENAI_API_KEY_NAME = "OpenAI-API_Key_Encrypted";
    private static final String OPENAI_MODEL_NAME = "OpenAI-Model";
    private static final String OPENAI_MODEL_TEMPERATURE = "OpenAI-Model-Temperature";
    private static final String OPENAI_MAX_TOKENS = "OpenAI-Max-Tokens";
    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private final SsmClient ssmClient;
    private final String openAiApiKey;
    private final String openAiModel;
    private final double modelTemperature;
    private final int modelMaxTokens;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public App() {
        this.ssmClient = SsmClient.builder().region(Region.EU_WEST_3).build();
        this.openAiApiKey = getParameterFromStore(OPENAI_API_KEY_NAME);
        this.openAiModel = getParameterFromStore(OPENAI_MODEL_NAME);
        this.modelTemperature = Double.parseDouble(getParameterFromStore(OPENAI_MODEL_TEMPERATURE));
        this.modelMaxTokens = Integer.parseInt(getParameterFromStore(OPENAI_MAX_TOKENS));
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.create();
    }

    @Override
    public Object handleRequest(Map<String, Object> input, Context context) {
        try {
            Map<String, String> queryParams = extractQueryString(input).orElse(new HashMap<>());
            String prompt = generatePrompt(queryParams);

            Map<String, Object> requestBody = createRequestBody(prompt);
            ChatCompletionResponse completionResponse = fetchCompletionResponse(requestBody);

            return buildSuccessResponse(parseJsonArray(completionResponse.getChoices().get(0).getMessage().getContent()));
        } catch (Exception e) {
            return buildErrorResponse(e.getMessage());
        }
    }

    private Map<String, Object> createRequestBody(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", this.openAiModel);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", generateSystemInstructions()),
                Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("max_tokens", this.modelMaxTokens);
        requestBody.put("temperature", modelTemperature);
        return requestBody;
    }

    private ChatCompletionResponse fetchCompletionResponse(Map<String, Object> requestBody) throws Exception {
        Mono<ChatCompletionResponse> completionResponseMono = webClient.post()
                .uri(URL)
                .headers(httpHeaders -> {
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                    httpHeaders.setBearerAuth(openAiApiKey);
                })
                .bodyValue(objectMapper.writeValueAsString(requestBody))
                .exchangeToMono(clientResponse -> {
                    HttpStatusCode statusCode = clientResponse.statusCode();
                    if (statusCode.is2xxSuccessful()) {
                        return clientResponse.bodyToMono(ChatCompletionResponse.class);
                    } else {
                        return handleErrorResponse(clientResponse);
                    }
                });
        return completionResponseMono.block();
    }

    private Mono<ChatCompletionResponse> handleErrorResponse(ClientResponse clientResponse) {
        clientResponse.bodyToMono(String.class).subscribe(s -> System.out.println("Response from Open AI API: " + s));
        System.out.println("Error occurred while invoking Open AI API");
        return Mono.error(new Exception("Error occurred while generating wordage"));
    }

    private Optional<Map<String, String>> extractQueryString(Map<String, Object> input) {
        Map<String, Object> queryStringMap = (Map<String, Object>) input.get("queryStringParameters");
        if (queryStringMap != null) {
            String queryString = (String) queryStringMap.get("querystring");
            return Optional.ofNullable(queryString != null ? parseQueryString(queryString) : null);
        }
        System.out.println("No queryStringParameters found.");
        return Optional.empty();
    }

    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> queryMap = new HashMap<>();

        if (queryString.startsWith("{") && queryString.endsWith("}")) {
            queryString = queryString.substring(1, queryString.length() - 1);
        }

        String[] pairs = queryString.split(", ");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length== 2) {
                String key = keyValue[0];
                String value = keyValue[1];
                queryMap.put(key, value);
            } else if (keyValue.length == 1) {
                queryMap.put(keyValue[0], "true");
            }
        }
        return queryMap;
    }

    private String getParameterFromStore(String parameterName) {
        try {
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();
            GetParameterResponse parameterResponse = this.ssmClient.getParameter(parameterRequest);
            return parameterResponse.parameter().value();
        } catch (SsmException e) {
            System.out.println("SSM Error: " + e.getMessage());
            System.exit(1);
        }
        return null;
    }

    private String generatePrompt(Map<String, String> input) {
        try {
            String restaurantName = input.getOrDefault("restaurantName", "");
            String cuisineType = input.getOrDefault("cuisineType", "");
            String mealTime = input.getOrDefault("mealTime", "");
            int protein = Integer.parseInt(input.getOrDefault("protein", "0"));
            int carbs = Integer.parseInt(input.getOrDefault("carbs", "0"));
            int fat = Integer.parseInt(input.getOrDefault("fat", "0"));
            int targetEnergy = Integer.parseInt(input.getOrDefault("targetEnergy", "0"));
            String energyUnit = input.getOrDefault("energyUnit", "");
            String weightUnit = input.getOrDefault("weightUnit", "");

            return createPrompt(restaurantName, cuisineType, mealTime, protein, carbs, fat, targetEnergy, energyUnit, weightUnit);
        } catch (Exception e) {
            System.out.println("Error while deserializing input params: " + e.getMessage());
            return null;
        }
    }

    private String createPrompt(String restaurantName, String cuisineType, String mealTime, int protein, int carbs,
                                int fat, int targetEnergy, String energyUnit, String weightUnit) {

        return String.format(
                "I'm looking for the best food options to choose from at a restaurant to meet my nutritional goals. Here are my specific requirements:\n" +
                "Cuisine Type: %s, " +
                "Meal Time: %s, " +
                "Target Energy: %d %s, " +
                "Target Protein: %d %s, " +
                "Target Carbs: %d %s, " +
                "Target Fat: %d %s." +
                "%s" +
                "Please provide a list of the 5 best options available at this type of restaurant that match these nutritional targets as closely as possible.",
                cuisineType, mealTime, targetEnergy, energyUnit, protein, weightUnit,
                carbs, weightUnit, fat, weightUnit,
                restaurantName.isEmpty() ? "" : "Restaurant Name: " + restaurantName + "."
        );
    }

    private String generateSystemInstructions() {
        return "You are a helpful assistant that generates a response containing a JSON array, following this structure for each option: {\n" +
                "  \"optionName\": \"\",\n" +
                "  \"energyAndMacros\": {\n" +
                "    \"energy\": \"\",\n" +
                "    \"protein\": \"\",\n" +
                "    \"carbs\": \"\",\n" +
                "    \"fat\": \"\"\n" +
                "  }\n" +
                "}";
    }

    private String parseJsonArray(String openAIResult) {
        int startIndex = openAIResult.indexOf('[');
        int endIndex = openAIResult.lastIndexOf(']');

        if (startIndex != -1 && endIndex != -1) {
            return openAIResult.substring(startIndex, endIndex + 1);
        } else {
            throw new RuntimeException("Invalid JSON string format generated by OpenAI");
        }
    }

    private Map<String, Object> buildSuccessResponse(String response) {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("statusCode", 200);
        responseBody.put("body", response);
        return responseBody;
    }

    private String buildErrorResponse(String errorMessage) {
        return "Error occurred: " + errorMessage;
    }
}
