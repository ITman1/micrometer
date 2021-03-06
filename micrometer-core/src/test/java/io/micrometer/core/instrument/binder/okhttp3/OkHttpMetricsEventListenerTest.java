/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.okhttp3;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OkHttpMetricsEventListenerTest {
    private MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    private OkHttpClient client = new OkHttpClient.Builder()
        .eventListener(OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
            .uriMapper(req -> req.url().encodedPath())
            .tags(Tags.zip("foo", "bar"))
            .build())
        .build();

    @Test
    void timeSuccessful() throws IOException {
        Request request = new Request.Builder()
            .url("https://publicobject.com/helloworld.txt")
            .build();

        client.newCall(request).execute().close();

        assertThat(registry.mustFind("okhttp.requests")
            .tags("uri", "/helloworld.txt", "status", "200")
            .timer().count()).isEqualTo(1L);
    }

    @Test
    void timeNotFound() {
        Request request = new Request.Builder()
            .url("https://publicobject.com/DOESNOTEXIST")
            .build();

        try {
            client.newCall(request).execute().close();
        } catch(IOException ignore) {
            // expected
        }

        assertThat(registry.mustFind("okhttp.requests")
            .tags("uri", "NOT_FOUND")
            .timer().count()).isEqualTo(1L);
    }

    @Test
    void timeFailureDueToTimeout() {
        Request request = new Request.Builder()
            .url("https://publicobject.com/helloworld.txt")
            .build();

        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.MILLISECONDS)
            .eventListener(OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
                .uriMapper(req -> req.url().encodedPath())
                .tags(Tags.zip("foo", "bar"))
                .build())
            .build();

        try {
            client.newCall(request).execute().close();
        } catch(IOException ignored) {
            // expected
        }

        assertThat(registry.mustFind("okhttp.requests")
            .tags("uri", "UNKNOWN", "status", "IO_ERROR")
            .timer().count()).isEqualTo(1L);
    }
}
