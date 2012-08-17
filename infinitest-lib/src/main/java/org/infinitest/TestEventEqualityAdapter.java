/*
 * This file is part of Infinitest.
 *
 * Copyright (C) 2010
 * "Ben Rady" <benrady@gmail.com>,
 * "Rod Coffin" <rfciii@gmail.com>,
 * "Ryan Breidenbach" <ryan.breidenbach@gmail.com>, et al.
 *
 * Infinitest is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Infinitest is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Infinitest.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.infinitest;

import java.util.*;

import org.infinitest.testrunner.*;

import com.google.common.base.Objects;

public class TestEventEqualityAdapter {
	private final TestEvent event;

	public TestEventEqualityAdapter(TestEvent event) {
		this.event = event;
	}

	public TestEvent getEvent() {
		return event;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TestEventEqualityAdapter) {
			TestEventEqualityAdapter other = (TestEventEqualityAdapter) obj;
			return Objects.equal(event, other.event) && Objects.equal(event.getMessage(), other.event.getMessage()) && Objects.equal(event.getPointOfFailure(), other.event.getPointOfFailure()) && Arrays.equals(event.getStackTrace(), other.event.getStackTrace());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(event) ^ Objects.hashCode(event.getMessage()) ^ Objects.hashCode(event.getPointOfFailure()) ^ Arrays.hashCode(event.getStackTrace());
	}
}