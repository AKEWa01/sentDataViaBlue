package com.example.aphiwat.gapi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GraphQL {
    private OkHttpClient client = new OkHttpClient();
    private MediaType media_type = MediaType.parse("application/graphql");

    private List<String> value_konosuba = new ArrayList<String>();

    private String url = "https://stratos.watchsmart.space/graphql/";
    private String auth_token;
    private String postBody;

    private int max_konosuba;
    private int size_per_round;

    private boolean is_sendsuccess = true;
    private boolean is_konosuba = false;

    public GraphQL(String auth_token) {
        setToken(auth_token);
    }

    public Call call(String postBody) {
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(media_type, postBody))
                .addHeader("Authorization", "jwt " + auth_token)
                .addHeader("Content-Type", "application/graphql")
                .build();

        return client.newCall(request);
    }

    public void createPostBody(List<String> value) {
        int size;
        if (value.size() <= (int) (1.5 * size_per_round)) {
            size = value.size();
        } else {
            size = size_per_round;
        }

        postBody = "mutation{";
        for (int i = 0; i < size; i++) {
            postBody += "j" + i + ":" + value.get(0) + "{status}";
            value.remove(0);
        }
        postBody += "}";
    }

    public void push() {
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(media_type, postBody))
                .addHeader("Authorization", "jwt " + auth_token)
                .addHeader("Content-Type", "application/graphql")
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                is_sendsuccess = false;
                call.cancel();
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    is_sendsuccess = true;
                } else {
                    is_sendsuccess = false;
                }
            }
        });
    }

    public void send() {
        if (is_sendsuccess) {
            if (value_konosuba.size() == 0) {
                is_sendsuccess = true;
                is_konosuba = false;
            } else {
                createPostBody(value_konosuba);
                is_sendsuccess = false;
            }
        }

        if (!is_sendsuccess) {
            push();
        }
    }

    public void setToken(String token) {
        auth_token = token;
    }

    public boolean isSend() {
        return is_konosuba;
    }

    public void setSend() {
        is_konosuba = true;
    }

    public boolean canSend() {
        return value_konosuba.size() >= max_konosuba;
    }

    public void setSizePerRound(int limit) {
        size_per_round = limit;
    }

    public void setMaxTimes(int time) {
        max_konosuba = time * size_per_round;
    }

    public void setMaxSize(int size) {
        max_konosuba = size;
    }

    public void addList(String value) {
        value_konosuba.add(value);
    }

    public int getListSize() {
        return value_konosuba.size();
    }
}