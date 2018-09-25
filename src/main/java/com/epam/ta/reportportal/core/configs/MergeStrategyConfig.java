/*
 * Copyright 2016 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/service-api
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.epam.ta.reportportal.core.configs;

import com.epam.ta.reportportal.core.item.impl.merge.strategy.DeepMergeStrategy;
import com.epam.ta.reportportal.core.item.impl.merge.strategy.MergeStrategyFactory;
import com.epam.ta.reportportal.core.item.impl.merge.strategy.MergeStrategyType;
import com.epam.ta.reportportal.core.item.merge.MergeStrategy;
import com.epam.ta.reportportal.dao.TestItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static java.util.Collections.singletonMap;

/**
 * @author Ivan Budaev
 */
@Configuration
public class MergeStrategyConfig {

	private final TestItemRepository testItemRepository;

	@Autowired
	public MergeStrategyConfig(TestItemRepository testItemRepository) {
		this.testItemRepository = testItemRepository;
	}

	@Bean
	public Map<MergeStrategyType, MergeStrategy> mergeStrategyMapping() {
		return singletonMap(MergeStrategyType.DEEP, new DeepMergeStrategy(testItemRepository));
	}

	@Bean
	public MergeStrategyFactory mergeStrategyFactory() {
		return new MergeStrategyFactory(mergeStrategyMapping());
	}
}
