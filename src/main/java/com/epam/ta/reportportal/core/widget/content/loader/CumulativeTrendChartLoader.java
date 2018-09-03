package com.epam.ta.reportportal.core.widget.content.loader;

import com.epam.ta.reportportal.commons.querygen.Filter;
import com.epam.ta.reportportal.commons.validation.BusinessRule;
import com.epam.ta.reportportal.core.widget.content.LoadContentStrategy;
import com.epam.ta.reportportal.dao.WidgetContentRepository;
import com.epam.ta.reportportal.entity.widget.content.LaunchesStatisticsContent;
import com.epam.ta.reportportal.ws.model.ErrorType;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.epam.ta.reportportal.commons.Predicates.equalTo;
import static com.epam.ta.reportportal.core.widget.util.WidgetFilterUtil.GROUP_FILTERS;
import static java.util.Collections.singletonMap;

@Service
public class CumulativeTrendChartLoader implements LoadContentStrategy {

	@Autowired
	private WidgetContentRepository widgetContentRepository;

	@Override
	public Map<String, ?> loadContent(List<String> contentFields, Set<Filter> filters, Sort sort, Map<String, String> widgetOptions, int limit) {

		validateContentFields(contentFields);

		Filter filter = GROUP_FILTERS.apply(filters);

		Map<String, List<LaunchesStatisticsContent>> result = widgetContentRepository.cumulativeTrendStatistics(filter,
				contentFields,
				sort,
				widgetOptions.get(PREFIX),
				limit
		);
		return singletonMap(RESULT, result);
	}

	/**
	 * Validate provided content fields.
	 *
	 * @param contentFields List of provided content.
	 */
	private void validateContentFields(List<String> contentFields) {
		BusinessRule.expect(CollectionUtils.isNotEmpty(contentFields), equalTo(true))
				.verify(ErrorType.BAD_REQUEST_ERROR, "Content fields should not be empty");
	}
}
