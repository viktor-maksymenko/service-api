/*
 * Copyright (C) 2018 EPAM Systems
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

package com.epam.ta.reportportal.core.project.impl;

import com.epam.ta.reportportal.auth.ReportPortalUser;
import com.epam.ta.reportportal.commons.querygen.Filter;
import com.epam.ta.reportportal.commons.querygen.FilterCondition;
import com.epam.ta.reportportal.core.events.activity.ActivityAction;
import com.epam.ta.reportportal.core.project.GetProjectInfoHandler;
import com.epam.ta.reportportal.dao.ActivityRepository;
import com.epam.ta.reportportal.dao.LaunchRepository;
import com.epam.ta.reportportal.dao.ProjectRepository;
import com.epam.ta.reportportal.entity.Activity;
import com.epam.ta.reportportal.entity.enums.InfoInterval;
import com.epam.ta.reportportal.entity.enums.LaunchModeEnum;
import com.epam.ta.reportportal.entity.launch.Launch;
import com.epam.ta.reportportal.entity.project.Project;
import com.epam.ta.reportportal.entity.project.email.ProjectInfoWidget;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.converter.PagedResourcesAssembler;
import com.epam.ta.reportportal.ws.converter.converters.LaunchConverter;
import com.epam.ta.reportportal.ws.converter.converters.ProjectConverter;
import com.epam.ta.reportportal.ws.model.ActivityResource;
import com.epam.ta.reportportal.ws.model.launch.Mode;
import com.epam.ta.reportportal.ws.model.project.ProjectInfoResource;
import com.google.common.collect.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;

import static com.epam.ta.reportportal.commons.Predicates.not;
import static com.epam.ta.reportportal.commons.querygen.Condition.*;
import static com.epam.ta.reportportal.commons.querygen.constant.ActivityCriteriaConstant.CRITERIA_ACTION;
import static com.epam.ta.reportportal.commons.querygen.constant.ActivityCriteriaConstant.CRITERIA_CREATION_DATE;
import static com.epam.ta.reportportal.commons.querygen.constant.GeneralCriteriaConstant.CRITERIA_PROJECT_ID;
import static com.epam.ta.reportportal.core.events.activity.ActivityAction.*;
import static com.epam.ta.reportportal.core.widget.content.constant.ContentLoaderConstants.RESULT;
import static com.epam.ta.reportportal.ws.converter.converters.ActivityConverter.TO_RESOURCE;
import static com.epam.ta.reportportal.ws.model.ErrorType.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * @author Pavel Bortnik
 */
@Service
public class GetProjectInfoHandlerImpl implements GetProjectInfoHandler {

	private DecimalFormat formatter = new DecimalFormat("###.##");
	private static final Double WEEKS_IN_MONTH = 4.4;
	private static final int LIMIT = 150;

	private final ProjectRepository projectRepository;

	private final LaunchRepository launchRepository;

	private final ActivityRepository activityRepository;

	private final ProjectInfoWidgetDataConverter dataConverter;

	@Autowired
	public GetProjectInfoHandlerImpl(ProjectRepository projectRepository, LaunchRepository launchRepository,
			ActivityRepository activityRepository, ProjectInfoWidgetDataConverter dataConverter) {
		this.projectRepository = projectRepository;
		this.launchRepository = launchRepository;
		this.activityRepository = activityRepository;
		this.dataConverter = dataConverter;
	}

	@Override
	public Iterable<ProjectInfoResource> getAllProjectsInfo(Filter filter, Pageable pageable) {
		return PagedResourcesAssembler.pageConverter(ProjectConverter.TO_PROJECT_INFO_RESOURCE)
				.apply(projectRepository.findProjectInfoByFilter(filter, pageable, Mode.DEFAULT.name()));
	}

	@Override
	public ProjectInfoResource getProjectInfo(ReportPortalUser.ProjectDetails projectDetails, String interval) {
		InfoInterval infoInterval = InfoInterval.findByInterval(interval)
				.orElseThrow(() -> new ReportPortalException(BAD_REQUEST_ERROR, interval));
		ProjectInfoResource projectInfoResource = ProjectConverter.TO_PROJECT_INFO_RESOURCE.apply(projectRepository.findProjectInfoFromDate(projectDetails.getProjectId(),
				getStartIntervalDate(infoInterval),
				Mode.DEFAULT.name()
		));
		if (projectInfoResource.getLaunchesQuantity() != 0) {
			formatter.setRoundingMode(RoundingMode.HALF_UP);
			double value = projectInfoResource.getLaunchesQuantity() / (infoInterval.getCount() * WEEKS_IN_MONTH);
			projectInfoResource.setLaunchesPerWeek(formatter.format(value));
		} else {
			projectInfoResource.setLaunchesPerWeek(formatter.format(0));
		}
		return projectInfoResource;
	}

	@Override
	public Map<String, ?> getProjectInfoWidgetContent(ReportPortalUser.ProjectDetails projectDetails, String interval, String widgetCode) {
		Project project = projectRepository.findById(projectDetails.getProjectId())
				.orElseThrow(() -> new ReportPortalException(PROJECT_NOT_FOUND, projectDetails.getProjectId()));
		InfoInterval infoInterval = InfoInterval.findByInterval(interval)
				.orElseThrow(() -> new ReportPortalException(BAD_REQUEST_ERROR, interval));
		ProjectInfoWidget widgetType = ProjectInfoWidget.findByCode(widgetCode)
				.orElseThrow(() -> new ReportPortalException(BAD_REQUEST_ERROR, widgetCode));

		List<Launch> launches = launchRepository.findByProjectIdAndStartTimeGreaterThanAndMode(project.getId(),
				getStartIntervalDate(infoInterval),
				LaunchModeEnum.DEFAULT
		);

		Map<String, ?> result;

		switch (widgetType) {
			case INVESTIGATED:
				result = dataConverter.getInvestigatedProjectInfo(launches, infoInterval);
				break;
			case CASES_STATISTIC:
				result = dataConverter.getTestCasesStatisticsProjectInfo(launches);
				break;
			case LAUNCHES_QUANTITY:
				result = dataConverter.getLaunchesQuantity(launches, infoInterval);
				break;
			case ISSUES_CHART:
				result = dataConverter.getLaunchesIssues(launches, infoInterval);
				break;
			case ACTIVITIES:
				result = getActivities(projectDetails.getProjectId(), infoInterval);
				break;
			case LAST_LAUNCH:
				result = getLastLaunchStatistics(projectDetails.getProjectId());
				break;
			default:
				// empty result
				result = Collections.emptyMap();
		}

		return result;
	}

	private Map<String, ?> getLastLaunchStatistics(Long projectId) {
		Optional<Launch> launchOptional = launchRepository.findLastRun(projectId, Mode.DEFAULT.name());
		return launchOptional.isPresent() ?
				Collections.singletonMap(RESULT, LaunchConverter.TO_RESOURCE.apply(launchOptional.get())) :
				Collections.emptyMap();
	}

	/**
	 * Utility method for calculation of start interval date
	 *
	 * @param interval Back interval
	 * @return Now minus interval
	 */
	private static LocalDateTime getStartIntervalDate(InfoInterval interval) {
		return LocalDateTime.now(Clock.systemUTC()).minusMonths(interval.getCount());
	}

	private static final Predicate<ActivityAction> ACTIVITIES_PROJECT_FILTER = it -> it == UPDATE_DEFECT || it == DELETE_DEFECT
			|| it == LINK_ISSUE || it == LINK_ISSUE_AA || it == UNLINK_ISSUE || it == UPDATE_ITEM;

	private Map<String, List<ActivityResource>> getActivities(Long projectId, InfoInterval infoInterval) {
		String value = Arrays.stream(ActivityAction.values())
				.filter(not(ACTIVITIES_PROJECT_FILTER))
				.map(ActivityAction::getValue)
				.collect(joining(","));
		Filter filter = new Filter(Activity.class, Sets.newHashSet(new FilterCondition(IN, false, value, CRITERIA_ACTION),
				new FilterCondition(EQUALS, false, String.valueOf(projectId), CRITERIA_PROJECT_ID), new FilterCondition(
						GREATER_THAN_OR_EQUALS,
						false,
						String.valueOf(Timestamp.valueOf(getStartIntervalDate(infoInterval)).getTime()),
						CRITERIA_CREATION_DATE
				)
		));
		List<Activity> activities = activityRepository.findByFilter(filter, PageRequest.of(0, LIMIT, Sort.by(Sort.Direction.DESC,
				filter.getTarget()
						.getCriteriaByFilter(CRITERIA_CREATION_DATE)
						.orElseThrow(() -> new ReportPortalException(UNCLASSIFIED_REPORT_PORTAL_ERROR))
						.getQueryCriteria()
		))).getContent();

		return Collections.singletonMap(RESULT, activities.stream().map(TO_RESOURCE).collect(toList()));
	}

}
