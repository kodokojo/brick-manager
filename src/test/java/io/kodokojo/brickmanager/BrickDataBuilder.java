package io.kodokojo.brickmanager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kodokojo.brickmanager.service.DataFileFetcher;
import okhttp3.Request;
import org.apache.commons.io.IOUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;
import java.io.InputStream;

public interface BrickDataBuilder {

    default String fetchAllMarathonAppsResponse() {
        return fetchJsonFromFile("marathon/allApps.json");
    }

    default String fetchTestbNexusMarathonAppsResponse() {
        return fetchJsonFromFile("marathon/testb-nexus.json");
    }

    default String fetchJsonFromFile(String fileName) {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(fileName);
        try {
            String content = IOUtils.toString(inputStream);
            return content;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    class MockedMarathonCall implements Call<JsonObject> {

        private final DataFileFetcher fetcher;

        public MockedMarathonCall(DataFileFetcher fetcher) {
            this.fetcher = fetcher;
        }

        @Override
        public Response<JsonObject> execute() throws IOException {
            String str = fetcher.fetch();
            JsonParser parser = new JsonParser();
            JsonObject json = (JsonObject) parser.parse(str);
            return Response.success(json);
        }

        @Override
        public void enqueue(Callback<JsonObject> callback) {

        }

        @Override
        public boolean isExecuted() {
            return false;
        }

        @Override
        public void cancel() {

        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public Call<JsonObject> clone() {
            return null;
        }

        @Override
        public Request request() {
            return null;
        }
    }
}
