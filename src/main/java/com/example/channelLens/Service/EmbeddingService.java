package com.example.channelLens.Service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;

@Service
public class EmbeddingService {

record EmbedRequest(String model, Content content) {}
record Content(List<Part> parts) {}
record Part(String text) {}
record EmbedResponse(Embedding embedding) {}
record Embedding(List<Float> values) {}

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();

   public float[] getEmbedding(String text) {
    //String url = "https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=" + apiKey;
   String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=" + apiKey;

    var request = new EmbedRequest(
        "models/text-embedding-001",
        new Content(List.of(new Part(text)))
    );
    
    var response = restClient.post()
        .uri(java.net.URI.create(url))
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .body(EmbedResponse.class);
    
    List<Float> values = response.embedding().values();
    float[] result = new float[values.size()];
    for (int i = 0; i < values.size(); i++) {
        result[i] = values.get(i);
    }
    return result;
}
// コサイン類似度計算
    public double cosineSimilarity(float[] a, float[] b){
        double dot = 0,normA = 0, normB = 0;
        for(int i = 0;i<a.length;i++){
            dot += a[i]*b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA)* Math.sqrt(normB));
    }
}


