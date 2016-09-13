/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.aurora.scheduler.events;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.aurora.scheduler.events.PubsubEvent.EventSubscriber;
import org.apache.aurora.scheduler.events.PubsubEvent.TaskStateChange;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches TaskStateChanges and send events to configured endpoint.
 */
public class Webhook implements EventSubscriber {

  private static final Logger LOG = LoggerFactory.getLogger(Webhook.class);

  private HttpClient client;
  private WebhookInfo info;

  @Inject
  Webhook(HttpClient client, WebhookInfo info) {
    this.client = client;
    this.info = info;
  }

  private HttpPost createPostRequest(TaskStateChange stateChange) throws URISyntaxException, UnsupportedEncodingException {
    String eventJson = stateChange.toJson();

    HttpPost post = new HttpPost();
    post.setURI(new URI(info.getTargetURL()));
    post.setHeader("Timestamp", "124");
    post.setEntity(new StringEntity(eventJson, "application/json"));

    return post;
  }

  /**
   * Watches all TaskStateChanges and send them best effort to a configured endpoint.
   * <p>
   * This is used to expose an external event bus.
   *
   * @param stateChange State change notification.
   */
  @Subscribe
  public void taskChangedState(TaskStateChange stateChange) {
    HttpPost post = createPostRequest(stateChange);
    try {
      client.execute(post);
    } catch (IOException e) {
      // Log this.
    } catch (URISyntaxException e) {
      // Log this
    } finally {
      post.reset();
    }
  }
}
