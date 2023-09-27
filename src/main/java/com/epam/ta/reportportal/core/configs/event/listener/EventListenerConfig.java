/*
 * Copyright 2021 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.ta.reportportal.core.configs.event.listener;

import com.epam.ta.reportportal.core.events.activity.LaunchFinishedEvent;
import com.epam.ta.reportportal.core.events.activity.item.IssueResolvedEvent;
import com.epam.ta.reportportal.core.events.activity.item.TestItemFinishedEvent;
import com.epam.ta.reportportal.core.events.listener.LaunchFinishedEventListener;
import com.epam.ta.reportportal.core.events.listener.TestItemFinishedEventListener;
import com.epam.ta.reportportal.core.events.listener.TestItemIssueResolvedEventListener;
import com.epam.ta.reportportal.core.events.subscriber.impl.delegate.ProjectConfigDelegatingSubscriber;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
@Configuration
public class EventListenerConfig {

  @Bean
  public LaunchFinishedEventListener launchFinishedEventListener(
      ProjectConfigDelegatingSubscriber<LaunchFinishedEvent> launchFinishedDelegatingSubscriber) {
    return new LaunchFinishedEventListener(List.of(launchFinishedDelegatingSubscriber));
  }

  @Bean
  public TestItemIssueResolvedEventListener testItemIssueResolvedEventListener(
      ProjectConfigDelegatingSubscriber<IssueResolvedEvent> itemIssueResolvedDelegatingSubscriber) {
    return new TestItemIssueResolvedEventListener(List.of(itemIssueResolvedDelegatingSubscriber));
  }

  @Bean
  public TestItemFinishedEventListener testItemFinishedEventListener(
      ProjectConfigDelegatingSubscriber<TestItemFinishedEvent> testItemFinishedDelegatingSubscriber) {
    return new TestItemFinishedEventListener(List.of(testItemFinishedDelegatingSubscriber));
  }
}