/*
 * Copyright 2019 EPAM Systems
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

package com.epam.ta.reportportal.util.email;

import static com.epam.ta.reportportal.commons.EntityUtils.normalizeId;
import static com.epam.ta.reportportal.dao.constant.WidgetContentRepositoryConstants.DEFECTS_AUTOMATION_BUG_TOTAL;
import static com.epam.ta.reportportal.dao.constant.WidgetContentRepositoryConstants.DEFECTS_NO_DEFECT_TOTAL;
import static com.epam.ta.reportportal.dao.constant.WidgetContentRepositoryConstants.DEFECTS_PRODUCT_BUG_TOTAL;
import static com.epam.ta.reportportal.dao.constant.WidgetContentRepositoryConstants.DEFECTS_SYSTEM_ISSUE_TOTAL;
import static com.epam.ta.reportportal.dao.constant.WidgetContentRepositoryConstants.DEFECTS_TO_INVESTIGATE_TOTAL;
import static com.epam.ta.reportportal.dao.constant.WidgetContentRepositoryConstants.EXECUTIONS_FAILED;
import static com.epam.ta.reportportal.dao.constant.WidgetContentRepositoryConstants.EXECUTIONS_PASSED;
import static com.epam.ta.reportportal.dao.constant.WidgetContentRepositoryConstants.EXECUTIONS_SKIPPED;
import static com.epam.ta.reportportal.dao.constant.WidgetContentRepositoryConstants.EXECUTIONS_TOTAL;
import static com.google.common.net.UrlEscapers.urlPathSegmentEscaper;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

import com.epam.reportportal.commons.template.TemplateEngine;
import com.epam.ta.reportportal.entity.ItemAttribute;
import com.epam.ta.reportportal.entity.launch.Launch;
import com.epam.ta.reportportal.entity.project.Project;
import com.epam.ta.reportportal.entity.project.ProjectIssueType;
import com.epam.ta.reportportal.entity.statistics.Statistics;
import com.epam.ta.reportportal.model.user.CreateUserRQFull;
import com.epam.ta.reportportal.util.UserUtils;
import com.epam.ta.reportportal.util.email.constant.IssueRegexConstant;
import com.google.common.annotations.VisibleForTesting;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.FileUrlResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Email Sending Service based on {@link JavaMailSender}
 *
 * @author Andrei_Ramanchuk
 */
public class EmailService extends JavaMailSenderImpl {

  private static final String FINISH_LAUNCH_EMAIL_SUBJECT =
      " ReportPortal Notification: [%s] launch '%s' #%s finished";
  private static final String URL_FORMAT = "%s/launches/all";
  private static final String COMPOSITE_ATTRIBUTE_FILTER_FORMAT =
      "%s?launchesParams=filter.has.compositeAttribute=%s";
  private static final String TEMPLATE_IMAGES_PREFIX = "templates/email/images/";
  private TemplateEngine templateEngine;
  /* Default value for FROM project notifications field */
  private String from;
  private String rpHost;

  public EmailService(Properties javaMailProperties) {
    super.setJavaMailProperties(javaMailProperties);
  }

  /**
   * User creation confirmation email
   *
   * @param subject    Letter's subject
   * @param recipients Letter's recipients
   * @param url        ReportPortal URL
   */
  public void sendCreateUserConfirmationEmail(final String subject, final String[] recipients,
      final String url) {
    MimeMessagePreparator preparator = mimeMessage -> {
      MimeMessageHelper message = new MimeMessageHelper(mimeMessage, true, "utf-8");
      message.setSubject(subject);
      message.setTo(recipients);
      setFrom(message);

      Map<String, Object> email = new HashMap<>();
      email.put("url", getUrl(url));
      String text = templateEngine.merge("registration-template.ftl", email);
      message.setText(text, true);

      message.addInline("create-user.png", emailTemplateResource("create-user.png"));

      attachSocialImages(message);
    };
    this.send(preparator);
  }

  /**
   * Finish launch notification
   *
   * @param recipients List of recipients
   * @param project    {@link Project}
   * @param url        ReportPortal URL
   * @param launch     Launch
   */
  public void sendLaunchFinishNotification(final String[] recipients, final String url,
      final Project project, final Launch launch) {
    String subject =
        format(FINISH_LAUNCH_EMAIL_SUBJECT, project.getName().toUpperCase(), launch.getName(),
            launch.getNumber()
        );
    MimeMessagePreparator preparator = mimeMessage -> {
      MimeMessageHelper message = new MimeMessageHelper(mimeMessage, true, "utf-8");
      message.setSubject(subject);
      message.setTo(recipients);
      setFrom(message);

      String text = mergeFinishLaunchText(getUrl(url), launch, project.getProjectIssueTypes());
      message.setText(text, true);

      attachSocialImages(message);
    };
    this.send(preparator);
  }

  @VisibleForTesting
  String mergeFinishLaunchText(String url, Launch launch, Set<ProjectIssueType> projectIssueTypes) {
    Map<String, Object> email = new HashMap<>();
    /* Email fields values */
    String basicUrl = format(URL_FORMAT, url);
    email.put("name", launch.getName());
    email.put("number", String.valueOf(launch.getNumber()));
    email.put("description", launch.getDescription());
    email.put("url", format("%s/%s", basicUrl, launch.getId()));

    /* Tags with links */
    if (!CollectionUtils.isEmpty(launch.getAttributes())) {
      email.put("attributes", launch.getAttributes().stream().filter(it -> !it.isSystem()).collect(
          toMap(attribute -> ofNullable(attribute.getKey()).map(it -> it.concat(":")).orElse("")
                  .concat(attribute.getValue()),
              attribute -> buildAttributesLink(basicUrl, attribute)
          )));
    }

    /* Launch execution statistics */

    Map<String, Integer> statistics = launch.getStatistics().stream().filter(
        s -> ofNullable(s.getStatisticsField()).isPresent() && StringUtils.isNotEmpty(
            s.getStatisticsField().getName())).collect(
        Collectors.toMap(s -> s.getStatisticsField().getName(), Statistics::getCounter,
            (prev, curr) -> prev
        ));

    email.put("total", ofNullable(statistics.get(EXECUTIONS_TOTAL)).orElse(0));
    email.put("passed", ofNullable(statistics.get(EXECUTIONS_PASSED)).orElse(0));
    email.put("failed", ofNullable(statistics.get(EXECUTIONS_FAILED)).orElse(0));
    email.put("skipped", ofNullable(statistics.get(EXECUTIONS_SKIPPED)).orElse(0));

    /* Launch issue statistics global counters */
    email.put("productBugTotal", ofNullable(statistics.get(DEFECTS_PRODUCT_BUG_TOTAL)).orElse(0));
    email.put("automationBugTotal",
        ofNullable(statistics.get(DEFECTS_AUTOMATION_BUG_TOTAL)).orElse(0)
    );
    email.put("systemIssueTotal", ofNullable(statistics.get(DEFECTS_SYSTEM_ISSUE_TOTAL)).orElse(0));
    email.put("noDefectTotal", ofNullable(statistics.get(DEFECTS_NO_DEFECT_TOTAL)).orElse(0));
    email.put("toInvestigateTotal",
        ofNullable(statistics.get(DEFECTS_TO_INVESTIGATE_TOTAL)).orElse(0)
    );

    Map<String, String> locatorsMapping = projectIssueTypes.stream().collect(
        toMap(it -> it.getIssueType().getLocator(), it -> it.getIssueType().getLongName()));

    /* Launch issue statistics custom sub-types */
    fillEmail(email, "pbInfo", statistics, locatorsMapping,
        IssueRegexConstant.PRODUCT_BUG_ISSUE_REGEX
    );
    fillEmail(email, "abInfo", statistics, locatorsMapping,
        IssueRegexConstant.AUTOMATION_BUG_ISSUE_REGEX
    );
    fillEmail(email, "siInfo", statistics, locatorsMapping, IssueRegexConstant.SYSTEM_ISSUE_REGEX);
    fillEmail(email, "ndInfo", statistics, locatorsMapping,
        IssueRegexConstant.NO_DEFECT_ISSUE_REGEX
    );
    fillEmail(email, "tiInfo", statistics, locatorsMapping,
        IssueRegexConstant.TO_INVESTIGATE_ISSUE_REGEX
    );

    return templateEngine.merge("finish-launch-template.ftl", email);
  }

  private String getUrl(String baseUrl) {
    return ofNullable(rpHost).map(rh -> {
      final UriComponents rpHostUri = UriComponentsBuilder.fromUriString(rh).build();
      return UriComponentsBuilder.fromUriString(baseUrl).scheme(rpHostUri.getScheme())
          .host(rpHostUri.getHost()).port(rpHostUri.getPort()).build().toUri().toASCIIString();
    }).orElse(baseUrl);
  }

  private String buildAttributesLink(String basicUrl, ItemAttribute attribute) {
    if (null != attribute.getKey()) {
      return format(COMPOSITE_ATTRIBUTE_FILTER_FORMAT, basicUrl,
          urlPathSegmentEscaper().escape(attribute.getKey()) + ":" + urlPathSegmentEscaper().escape(
              attribute.getValue())
      );
    } else {
      return format(COMPOSITE_ATTRIBUTE_FILTER_FORMAT, basicUrl,
          urlPathSegmentEscaper().escape(attribute.getValue())
      );
    }
  }

  private void fillEmail(Map<String, Object> email, String statisticsName,
      Map<String, Integer> statistics, Map<String, String> locatorsMapping, String regex) {
    Optional<Map<String, Integer>> pb = Optional.of(statistics.entrySet().stream().filter(entry -> {
      Pattern pattern = Pattern.compile(regex);
      return pattern.matcher(entry.getKey()).matches();
    }).collect(Collectors.toMap(
        entry -> locatorsMapping.get(StringUtils.substringAfterLast(entry.getKey(), "$")),
        entry -> ofNullable(entry.getValue()).orElse(0), (prev, curr) -> prev
    )));

    pb.ifPresent(stats -> email.put(statisticsName, stats));
  }

  /**
   * Restore password email
   *
   * @param subject    Letter's subject
   * @param recipients Letter's recipients
   * @param url        ReportPortal URL
   * @param login      User's login
   */
  public void sendRestorePasswordEmail(final String subject, final String[] recipients,
      final String url, final String login) {
    MimeMessagePreparator preparator = mimeMessage -> {
      MimeMessageHelper message = new MimeMessageHelper(mimeMessage, true, "utf-8");
      message.setSubject(subject);
      message.setTo(recipients);

      setFrom(message);

      Map<String, Object> email = new HashMap<>();
      email.put("login", login);
      email.put("url", getUrl(url));
      String text = templateEngine.merge("restore-password-template.ftl", email);
      message.setText(text, true);

      message.addInline("restore-password.png", emailTemplateResource("restore-password.png"));
      attachSocialImages(message);
    };
    this.send(preparator);
  }

  public void sendChangePasswordConfirmation(final String subject, final String[] recipients,
      final String login) {
    MimeMessagePreparator preparator = mimeMessage -> {
      MimeMessageHelper message = new MimeMessageHelper(mimeMessage, true, "utf-8");
      message.setSubject(subject);
      message.setTo(recipients);

      setFrom(message);

      Map<String, Object> email = new HashMap<>();
      email.put("user_name", login);
      String text = templateEngine.merge("change-password-template.ftl", email);
      message.setText(text, true);

      message.addInline("illustration.png", emailTemplateResource("illustration.png"));
      attachSocialImages(message);
    };
    this.send(preparator);
  }

  public void sendIndexFinishedEmail(final String subject, final String recipient,
      final Long indexedLogsCount) {
    MimeMessagePreparator preparator = mimeMessage -> {
      MimeMessageHelper message = new MimeMessageHelper(mimeMessage, true, "utf-8");
      message.setSubject(subject);
      message.setTo(recipient);
      Map<String, Object> email = new HashMap<>();
      email.put("indexedLogsCount", ofNullable(indexedLogsCount).orElse(0L));
      setFrom(message);
      String text = templateEngine.merge("index-finished-template.ftl", email);
      message.setText(text, true);
    };
    this.send(preparator);
  }

  public void setTemplateEngine(TemplateEngine templateEngine) {
    this.templateEngine = templateEngine;
  }

  public void setFrom(String from) {
    this.from = from;
  }

  public void setRpHost(String rpHost) {
    this.rpHost = rpHost;
  }

  public void sendCreateUserConfirmationEmail(CreateUserRQFull req, String basicUrl) {
    MimeMessagePreparator preparator = mimeMessage -> {
      MimeMessageHelper message = new MimeMessageHelper(mimeMessage, true, "utf-8");
      message.setSubject("Welcome to ReportPortal");
      message.setTo(req.getEmail());
      setFrom(message);

      Map<String, Object> email = new HashMap<>();
      email.put("url", getUrl(basicUrl));
      email.put("login", normalizeId(req.getLogin()));
      email.put("password", req.getPassword());
      String text = templateEngine.merge("create-user-template.ftl", email);
      message.setText(text, true);

      message.addInline("create-user.png", emailTemplateResource("create-user.png"));
      attachSocialImages(message);
    };
    this.send(preparator);
  }

  public void sendConnectionTestEmail(String sendTo) {
    MimeMessagePreparator preparator = mimeMessage -> {
      MimeMessageHelper message = new MimeMessageHelper(mimeMessage, true, "utf-8");
      message.setSubject("Email server integration creation");
      message.setTo(sendTo);
      setFrom(message);

      Map<String, Object> data = Collections.emptyMap();
      String text = templateEngine.merge("email-connection.ftl", data);
      message.setText(text, true);
      attachSocialImages(message);
    };
    this.send(preparator);
  }

  /**
   * Builds FROM field If username is email, format will be "from \<email\>"
   */
  private void setFrom(MimeMessageHelper message)
      throws MessagingException, UnsupportedEncodingException {
    if (StringUtils.isNotBlank(this.from)) {
      if (UserUtils.isEmailValid(this.from) && isAddressValid(this.from)) {
        message.setFrom(this.from);
      } else if (UserUtils.isEmailValid(getUsername())) {
        message.setFrom(getUsername(), this.from);
      }
    } else if (UserUtils.isEmailValid(getUsername())) {
      message.setFrom(getUsername());
    }
    //otherwise generate automatically
  }

  private boolean isAddressValid(String from) {
    try {
      InternetAddress.parse(from);
      return true;
    } catch (AddressException e) {
      return false;
    }
  }

  private void attachSocialImages(MimeMessageHelper message) throws MessagingException {
    message.addInline("ic-twitter.png", emailTemplateResource("ic-twitter.png"));
    message.addInline("ic-slack.png", emailTemplateResource("ic-slack.png"));
    message.addInline("ic-youtube.png", emailTemplateResource("ic-youtube.png"));
    message.addInline("ic-linkedin.png", emailTemplateResource("ic-linkedin.png"));
    message.addInline("ic-facebook.png", emailTemplateResource("ic-facebook.png"));
    message.addInline("ic-github.png", emailTemplateResource("ic-github.png"));
  }

  private Resource emailTemplateResource(String resource) {
    return new FileUrlResource(Objects.requireNonNull(
        EmailService.class.getClassLoader().getResource(TEMPLATE_IMAGES_PREFIX + resource)));
  }

  public void sendAccountSelfDeletionNotification(String recipient) {
    MimeMessagePreparator preparator = mimeMessage -> {
      MimeMessageHelper message = new MimeMessageHelper(mimeMessage, true, "utf-8");
      message.setSubject("Account Deletion Notification");
      message.setTo(recipient);

      setFrom(message);

      Map<String, Object> data = Collections.emptyMap();
      String text = templateEngine.merge("self-delete-account-template.ftl", data);
      message.setText(text, true);

      message.addInline("new-logo.png", emailTemplateResource("new-logo.png"));
      message.addInline("deleted-account.png", emailTemplateResource("deleted-account.png"));
      attachNewSocialImages(message);
    };
    this.send(preparator);
  }

  public void sendAccountDeletionByRetentionNotification(String recipient) {
    MimeMessagePreparator preparator = mimeMessage -> {
      MimeMessageHelper message = new MimeMessageHelper(mimeMessage, true, "utf-8");
      message.setSubject("Account Deletion Notification");
      message.setTo(recipient);

      setFrom(message);

      Map<String, Object> data = Collections.emptyMap();
      String text = templateEngine.merge("delete-account-template.ftl", data);
      message.setText(text, true);

      message.addInline("new-logo.png", emailTemplateResource("new-logo.png"));
      message.addInline("deleted-account.png", emailTemplateResource("deleted-account.png"));
      attachNewSocialImages(message);
    };
    this.send(preparator);
  }

  public void sendUserExpirationNotification(String recipient, Map<String, Object> params) {
    MimeMessagePreparator preparator = mimeMessage -> {
      MimeMessageHelper message = new MimeMessageHelper(mimeMessage, true, "utf-8");
      message.setSubject("Account Deletion Notification");
      message.setTo(recipient);

      setFrom(message);

      Map<String, Object> data = new HashMap<>();
      data.put("remainingTime", params.get("remainingTime"));
      data.put("inactivityPeriod", params.get("inactivityPeriod"));
      data.put("deadlineDate", params.get("deadlineDate"));
      String text = templateEngine.merge("delete-account-notification-template.ftl", data);
      message.setText(text, true);

      message.addInline("new-logo.png", emailTemplateResource("new-logo.png"));
      message.addInline(
          "delete-account-notification.png",
          emailTemplateResource("delete-account-notification.png")
      );
      attachNewSocialImages(message);
    };
    this.send(preparator);
  }

  private void attachNewSocialImages(MimeMessageHelper message) throws MessagingException {
    message.addInline("new-ic-twitter.png", emailTemplateResource("new-ic-twitter.png"));
    message.addInline("new-ic-slack.png", emailTemplateResource("new-ic-slack.png"));
    message.addInline("new-ic-youtube.png", emailTemplateResource("new-ic-youtube.png"));
    message.addInline("new-ic-linkedin.png", emailTemplateResource("new-ic-linkedin.png"));
    message.addInline("new-ic-facebook.png", emailTemplateResource("new-ic-facebook.png"));
    message.addInline("new-ic-github.png", emailTemplateResource("new-ic-github.png"));
  }

}
